package me.mochibit.defcon.utils

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import io.github.retrooper.packetevents.util.SpigotReflectionUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.DefconPlugin
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.block.Block
import org.bukkit.block.data.*
import org.joml.Vector3i
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Lightweight block change representation
 */
data class BlockChange(
    val x: Int,
    val y: Int,
    val z: Int,
    val newMaterial: Material? = null,
    val copyBlockData: Boolean = false,
    val updateBlock: Boolean = false,
    val newBiome: Biome? = null,
)

/**
 * Optimized BlockChanger using Kotlin Channels and Coroutines for efficient processing
 * with adaptive performance pacing
 */
class BlockChanger private constructor(
    private val world: World,
) {
    private val plugin = Defcon
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Configuration properties with more aggressive defaults
    private var blocksPerBatch = 30000
    private var processingInterval = 1.seconds
    private var workerCount = 4

    // Performance monitoring settings
    private var performanceMonitoringEnabled = true
    private var targetTickTime = 50.milliseconds  // Target 50ms per tick (20 TPS)
    private var adaptationThreshold = 80.milliseconds  // Start adapting at 80ms tick time (indicates server stress)
    private var adaptationFactor = 0.8  // Reduce by 20% when server is under stress
    private var recoveryFactor = 1.05  // Increase by 5% when server is performing well
    private var minBlocksPerBatch = 1000  // Don't go below this value
    private var adaptationCheckInterval = 5.seconds  // How often to check and adapt
    private var originalBlocksPerBatch = blocksPerBatch  // Store original value for recovery

    // Performance monitoring state
    private var lastAdaptationTime: TimeMark = TimeSource.Monotonic.markNow()
    private var currentTickTime = 0.milliseconds
    private var adaptationActive = false

    // Stats tracking
    private val totalProcessed = AtomicLong(0)
    private val processingActive = AtomicBoolean(false)

    // Cache reference
    private val chunkCache = ChunkCache.getInstance(world)

    // Single shared channel for all workers
    private lateinit var blockChannel: Channel<BlockChange>
    private var processingJobs = mutableListOf<Job>()
    private var monitoringJob: Job? = null

    init {
        initializeChannel()
        startProcessing()
        if (performanceMonitoringEnabled) {
            startPerformanceMonitoring()
        }
    }

    /**
     * Initialize the channel system
     */
    private fun initializeChannel() {
        blockChannel = Channel(Channel.BUFFERED)
    }

    /**
     * Start monitoring server performance and adapting processing rate
     */
    private fun startPerformanceMonitoring() {
        monitoringJob = scope.launch(plugin.minecraftDispatcher) {
            while (isActive && processingActive.get()) {
                // Measure performance on the main thread
                val startTime = TimeSource.Monotonic.markNow()

                // This delay of 1 tick allows us to measure how long it takes for the server to process a tick
                delay(1)  // Wait for one server tick

                // Calculate time it took for the tick to complete
                currentTickTime = startTime.elapsedNow()

                // Adapt processing rate if needed
                adaptProcessingRate()

                // Wait before checking again
                delay(adaptationCheckInterval)
            }
        }
    }

    /**
     * Adapt processing rate based on server performance
     */
    private fun adaptProcessingRate() {
        if (!performanceMonitoringEnabled || lastAdaptationTime.elapsedNow() < adaptationCheckInterval) {
            return
        }

        lastAdaptationTime = TimeSource.Monotonic.markNow()

        when {
            // Server under stress - reduce processing rate
            currentTickTime > adaptationThreshold -> {
                val newBatchSize = max((blocksPerBatch * adaptationFactor).toInt(), minBlocksPerBatch)

                if (newBatchSize < blocksPerBatch) {
                    blocksPerBatch = newBatchSize
                    adaptationActive = true
                    // No logging to avoid additional overhead when server is already stressed
                }
            }

            // Server performing well and adaptation was active - gradually recover
            currentTickTime < targetTickTime && adaptationActive -> {
                val newBatchSize = (blocksPerBatch * recoveryFactor).toInt()

                if (newBatchSize <= originalBlocksPerBatch) {
                    blocksPerBatch = newBatchSize
                } else {
                    blocksPerBatch = originalBlocksPerBatch
                    adaptationActive = false
                }
            }
        }
    }

    /**
     * Start processing block changes with multiple workers
     */
    private fun startProcessing() {
        if (!processingActive.compareAndSet(false, true)) return

        processingJobs.clear()

        // Launch multiple workers that share the same channel
        repeat(workerCount) {
            val job = scope.launch(Dispatchers.Default) {
                var processedBlocks = 0
                for (change in blockChannel) {
                    // Process the block change
                    applyBlockChange(change)
                    processedBlocks++
                    if (processedBlocks >= blocksPerBatch) {
                        delay(processingInterval)
                        totalProcessed.addAndGet(processedBlocks.toLong())
                        processedBlocks = 0
                    }
                }
            }
            processingJobs.add(job)
        }
    }


    /**
     * Apply a single block change efficiently
     */
    private suspend fun applyBlockChange(change: BlockChange) {
        try {
            val oldBlockData = NMSReflectionCache.getBlockMaterial(world, change.x, change.y, change.z)
            // Apply material change
            change.newMaterial?.let {
                val newBlockData = it.createBlockData()
                copyRelevantBlockData(oldBlockData.createBlockData(), newBlockData)
                scope.launch(plugin.minecraftDispatcher) {
                    NMSReflectionCache.setBlockFast(world, change.x, change.y, change.z, newBlockData)
                }
            }
        } catch (e: Exception) {
            // Silent catch for resilience against world/chunk unload scenarios
        }
    }

    /**
     * Copy only the relevant block data properties with memory-efficient implementation
     */
    private fun copyRelevantBlockData(oldBlockData: BlockData, newBlockData: BlockData) {
        try {
            // Only copy properties that exist in both block data types
            // Use more type checking to avoid unnecessary casts
            if (oldBlockData is Directional && newBlockData is Directional) {
                try {
                    newBlockData.facing = oldBlockData.facing
                } catch (_: Exception) {
                }
            }
            if (oldBlockData is Bisected && newBlockData is Bisected) {
                try {
                    newBlockData.half = oldBlockData.half
                } catch (_: Exception) {
                }
            }
            if (oldBlockData is Orientable && newBlockData is Orientable) {
                try {
                    newBlockData.axis = oldBlockData.axis
                } catch (_: Exception) {
                }
            }
            if (oldBlockData is Rotatable && newBlockData is Rotatable) {
                try {
                    newBlockData.rotation = oldBlockData.rotation
                } catch (_: Exception) {
                }
            }

            // Only check boolean properties if we need to change them (avoid extra calls)
            if (oldBlockData is Waterlogged && newBlockData is Waterlogged && oldBlockData.isWaterlogged) {
                newBlockData.isWaterlogged = true
            }
            if (oldBlockData is Snowable && newBlockData is Snowable && oldBlockData.isSnowy) {
                newBlockData.isSnowy = true
            }
            if (oldBlockData is Openable && newBlockData is Openable && oldBlockData.isOpen) {
                newBlockData.isOpen = true
            }
            if (oldBlockData is Powerable && newBlockData is Powerable && oldBlockData.isPowered) {
                newBlockData.isPowered = true
            }

            // Rail is expensive to check, only do it if both are rails
            if (oldBlockData is Rail && newBlockData is Rail) {
                try {
                    newBlockData.shape = oldBlockData.shape
                } catch (_: Exception) {
                }
            }

            // Age is expensive due to coercing, do last
            if (oldBlockData is Ageable && newBlockData is Ageable) {
                val targetAge = oldBlockData.age.coerceAtMost(newBlockData.maximumAge)
                if (targetAge > 0) { // Skip if age is 0 (default)
                    newBlockData.age = targetAge
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Add a block change using x, y, z coordinates - suspending for backpressure
     */
    suspend fun addBlockChange(
        x: Int,
        y: Int,
        z: Int,
        newMaterial: Material?,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false,
        newBiome: Biome? = null
    ) {
        val change = BlockChange(x, y, z, newMaterial, copyBlockData, updateBlock, newBiome)

        // Send to channel - will suspend if channel is full (built-in backpressure)
        blockChannel.send(change)

        // Ensure processing is active
        if (!processingActive.get()) {
            startProcessing()
        }
    }

    /**
     * Add a block change using Block object
     */
    suspend fun addBlockChange(
        block: Block,
        newMaterial: Material,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false,
        newBiome: Biome? = null
    ) {
        addBlockChange(
            block.x,
            block.y,
            block.z,
            newMaterial,
            copyBlockData,
            updateBlock,
            newBiome
        )
    }

    /**
     * Add a block change using Vector3i object
     */
    suspend fun addBlockChange(
        pos: Vector3i,
        newMaterial: Material,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false,
        newBiome: Biome? = null
    ) {
        addBlockChange(
            pos.x,
            pos.y,
            pos.z,
            newMaterial,
            copyBlockData,
            updateBlock,
            newBiome
        )
    }

    /**
     * Change only the biome at a location
     */
    suspend fun changeBiome(
        x: Int,
        y: Int,
        z: Int,
        newBiome: Biome
    ) {
        addBlockChange(
            x,
            y,
            z,
            newMaterial = null,
            copyBlockData = false,
            updateBlock = false,
            newBiome = newBiome
        )
    }

    /**
     * Bulk add block changes - optimized with larger batches
     */
    suspend fun addBlockChanges(changes: Collection<BlockChange>) {
        if (changes.isEmpty()) return

        val batchSize = 20000

        for (batch in changes.chunked(batchSize)) {
            // Submit batch through the channel with counter updates
            for (change in batch) {
                blockChannel.send(change)
            }

            // Brief yield after each batch
            if (batch.size == batchSize) {
                yield()
            }
        }

        // Ensure processing is active
        if (!processingActive.get()) {
            startProcessing()
        }
    }

    /**
     * Non-suspending version for use in synchronous contexts
     */
    fun addBlockChangeSync(
        x: Int,
        y: Int,
        z: Int,
        newMaterial: Material?,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false,
        newBiome: Biome? = null
    ): Boolean {
        val change = BlockChange(x, y, z, newMaterial, copyBlockData, updateBlock, newBiome)

        // Try to add with non-blocking approach
        val added = blockChannel.trySend(change).isSuccess

        if (added) {
            // Ensure processing is active
            if (!processingActive.get()) {
                scope.launch { startProcessing() }
            }
        }

        return added
    }

    /**
     * Configure block changer parameters
     */
    fun configure(
        newBlocksPerBatch: Int? = null,
        newProcessingInterval: Duration? = null,
        newMaxQueueSize: Int? = null,
        newWorkerCount: Int? = null,
        newChunkBatchSize: Int? = null,
        newPerformanceMonitoringEnabled: Boolean? = null,
        newTargetTickTime: Duration? = null,
        newAdaptationThreshold: Duration? = null,
        newAdaptationFactor: Double? = null,
        newRecoveryFactor: Double? = null,
        newMinBlocksPerBatch: Int? = null,
        newAdaptationCheckInterval: Duration? = null
    ) {
        var needRestart = false
        var restartMonitoring = false

        // Store original batch size for adaptive scaling
        if (newBlocksPerBatch != null && newBlocksPerBatch > 0) {
            blocksPerBatch = newBlocksPerBatch
            originalBlocksPerBatch = newBlocksPerBatch
        }

        newProcessingInterval?.let { if (it.inWholeMilliseconds >= 0) processingInterval = it }

        // Performance monitoring configuration
        newPerformanceMonitoringEnabled?.let {
            if (performanceMonitoringEnabled != it) {
                performanceMonitoringEnabled = it
                restartMonitoring = true
            }
        }

        newTargetTickTime?.let { if (it.inWholeMilliseconds > 0) targetTickTime = it }
        newAdaptationThreshold?.let { if (it.inWholeMilliseconds > 0) adaptationThreshold = it }
        newAdaptationFactor?.let { if (it > 0 && it <= 1.0) adaptationFactor = it }
        newRecoveryFactor?.let { if (it >= 1.0) recoveryFactor = it }
        newMinBlocksPerBatch?.let { if (it > 0) minBlocksPerBatch = it }
        newAdaptationCheckInterval?.let { if (it.inWholeMilliseconds > 0) adaptationCheckInterval = it }

        // Worker count requires restart if changed
        newWorkerCount?.let {
            if (it > 0 && it != workerCount) {
                workerCount = it
                needRestart = true
            }
        }

        if (needRestart && processingActive.get()) {
            shutdown()
            initializeChannel() // Recreate channel with new capacity
            startProcessing()
            if (performanceMonitoringEnabled) {
                startPerformanceMonitoring()
            }
        } else if (restartMonitoring) {
            monitoringJob?.cancel()
            monitoringJob = null
            if (performanceMonitoringEnabled) {
                startPerformanceMonitoring()
            }
        }
    }

    /**
     * Get the total number of blocks processed
     */
    fun getTotalProcessed(): Long {
        return totalProcessed.get()
    }

    /**
     * Get current server performance metrics
     */
    fun getPerformanceMetrics(): Map<String, Any> {
        return mapOf(
            "currentTickTime" to currentTickTime.toString(),
            "adaptationActive" to adaptationActive,
            "currentBlocksPerBatch" to blocksPerBatch,
            "originalBlocksPerBatch" to originalBlocksPerBatch,
            "targetTickTime" to targetTickTime.toString(),
            "adaptationThreshold" to adaptationThreshold.toString()
        )
    }

    /**
     * Stop processing and clean up resources
     */
    fun shutdown() {
        processingActive.set(false)

        // Cancel performance monitoring
        monitoringJob?.cancel()
        monitoringJob = null

        // Cancel all processing jobs
        scope.launch {
            processingJobs.forEach {
                it.join()
            }
            processingJobs.clear()
            // Close channel
            blockChannel.close()
        }
    }

    /**
     * Singleton pattern implementation
     */
    companion object {
        private val instances = ConcurrentHashMap<String, BlockChanger>()

        @JvmStatic
        fun getInstance(world: World): BlockChanger {
            val worldName = world.name
            return instances.computeIfAbsent(worldName) { BlockChanger(world) }
        }

        @JvmStatic
        fun shutdownAll() {
            instances.values.forEach { it.shutdown() }
            instances.clear()
        }
    }
}

/**
 * Extension method for Defcon class for convenient access
 */
fun DefconPlugin.getBlockChanger(world: World): BlockChanger {
    return BlockChanger.getInstance(world)
}