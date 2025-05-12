package me.mochibit.defcon.particles.emitter

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.effects.ColorSupply
import me.mochibit.defcon.effects.PositionColorSupply
import me.mochibit.defcon.lifecycle.Lifecycled
import me.mochibit.defcon.particles.mutators.AbstractShapeMutator
import me.mochibit.defcon.particles.templates.AbstractParticle
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.joml.Matrix4d
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.random.Random

/**
 * Coroutine-optimized ParticleEmitter designed to handle 15,000+ particles efficiently
 * with improved LOD performance and structured concurrency.
 *
 */


class ParticleEmitter<T : EmitterShape>(
    position: Location,
    range: Double,
    maxParticlesInitial: Int = 1500,
    val emitterShape: T,
    val transform: Matrix4d = Matrix4d(),
    val spawnableParticles: MutableList<AbstractParticle> = mutableListOf(),
    var shapeMutator: AbstractShapeMutator? = null,

    var colorSupply: ColorSupply? = null,
    var positionColorSupply: PositionColorSupply? = null
) : Lifecycled {

    companion object {
        // LOD settings
        private const val LOD_CLOSE = 4    // 4x update speed
        private const val LOD_MEDIUM = 2   // 2x update speed
        private const val LOD_FAR = 1      // Normal speed
        private const val LOD_INACTIVE = 0 // Minimal updates

        // Distance thresholds (squared for performance)
        private const val LOD_CLOSE_DISTANCE_SQ = 100.0
        private const val LOD_MEDIUM_DISTANCE_SQ = 400.0

        // Batch processing constants
        private const val BATCH_SIZE = 1000
        private const val PARTICLE_SPAWN_BATCH = 64
        private const val PARTICLE_UPDATE_BATCH = 256
        private const val PLAYER_UPDATE_INTERVAL = 250L
        private const val BURST_BATCH_SIZE = 250
        private const val BURST_BATCH_DELAY = 10L

        // Heartbeat interval for frozen particle detection
        private const val HEARTBEAT_INTERVAL = 5000L
    }

    // Core position data
    private val origin: Vector3f = Vector3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
    val world: World = position.world
    private val rangeSquared = range * range

    // State tracking
    private val activeCount = AtomicInteger(0)
    private val isRunning = AtomicBoolean(true)
    private val hasPlayersInRange = AtomicBoolean(false)
    private val lastActivityTimestamp = AtomicInteger((System.currentTimeMillis() / 1000).toInt())

    // Use ConcurrentHashMap for better concurrent access patterns
    private val particles = ConcurrentHashMap<Int, ParticleInstance>()
    private val visiblePlayers = ConcurrentHashMap<Player, PlayerLodInfo>()
    private val updateTimes = ConcurrentHashMap<ParticleInstance, Long>()

    // Channels with appropriate buffer sizes
    private val particleSpawnChannel = Channel<List<AbstractParticle>>(Channel.BUFFERED)
    private val particleRemoveChannel = Channel<List<ParticleInstance>>(Channel.BUFFERED)
    private val positionUpdateChannel = Channel<Pair<Player, List<ClientSideParticleInstance>>>(Channel.BUFFERED)
    private val playerUpdateChannel = Channel<Unit>(Channel.CONFLATED)
    private val updateTickChannel = Channel<Float>(Channel.CONFLATED)
    private val heartbeatChannel = Channel<Unit>(Channel.CONFLATED)

    // Settings
    val radialVelocity = Vector3f(0f, 0f, 0f)
    private var particlesPerFrame = 15
    private var spawnProbability = 1.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    var maxParticles = maxParticlesInitial
        set(value) {
            val oldValue = field
            field = value

            // If maxParticles increased, trigger burst spawn
            if (value > oldValue && isRunning.get() && visible) {
                triggerBurstSpawn(oldValue, value)
            }
        }

    // Emitter scope with limited parallelism for better resource usage
    private val emitterScope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.Default.limitedParallelism(2) +
                CoroutineName("ParticleEmitter")
    )

    // LOD tracking
    private data class PlayerLodInfo(val lodLevel: Int, val lastUpdate: Long = System.currentTimeMillis())

    // Visibility control
    var visible = true
        set(value) {
            if (field == value) return
            field = value

            emitterScope.launch {
                val currentPlayers = visiblePlayers.keys.toList()
                val currentParticles = particles.values.filterIsInstance<ClientSideParticleInstance>()

                if (!value) {
                    // Hide all particles
                    for (player in currentPlayers) {
                        ClientSideParticleInstance.destroyParticlesInBatch(player, currentParticles)
                    }
                } else if (currentPlayers.isNotEmpty()) {
                    // Show particles
                    for (player in currentPlayers) {
                        for (batch in currentParticles.chunked(PARTICLE_UPDATE_BATCH)) {
                            batch.forEach { it.sendSpawnPacket(player) }
                            yield()
                        }
                    }
                }
            }
        }

    /**
     * Trigger a burst of particle spawns when maxParticles increases
     */
    private fun triggerBurstSpawn(oldValue: Int, newValue: Int) {
        if (spawnableParticles.isEmpty()) return

        val particlesToAdd = newValue - oldValue
        val particlesToCreate = min(particlesToAdd, newValue - activeCount.get())

        if (particlesToCreate <= 0) return

        emitterScope.launch {
            try {
                var remaining = particlesToCreate

                while (remaining > 0 && isRunning.get()) {
                    val batchSize = min(BURST_BATCH_SIZE, remaining)
                    val batch = ArrayList<AbstractParticle>(batchSize)

                    repeat(batchSize) {
                        val index = Random.nextInt(spawnableParticles.size)
                        batch.add(spawnableParticles[index])
                    }

                    particleSpawnChannel.send(batch)
                    remaining -= batchSize

                    delay(BURST_BATCH_DELAY)
                }

                // Update activity timestamp
                recordActivity()
            } catch (e: CancellationException) {
                // Expected during cancel
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error during burst spawn: ${e.message}")
            }
        }
    }

    /**
     * Record activity to prevent freezing detection
     */
    private fun recordActivity() {
        lastActivityTimestamp.set((System.currentTimeMillis() / 1000).toInt())
    }

    /**
     * Process particle spawn requests
     */
    private suspend fun processParticleSpawns() {
        for (particleBatch in particleSpawnChannel) {
            try {
                spawnParticleBatch(particleBatch)
                recordActivity()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error in particle spawn processor: ${e.message}")
            }
        }
    }

    /**
     * Process particle removal requests
     */
    private suspend fun processParticleRemovals() {
        for (particleBatch in particleRemoveChannel) {
            try {
                removeParticleBatch(particleBatch)
                recordActivity()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error in particle removal processor: ${e.message}")
            }
        }
    }

    /**
     * Process position updates
     */
    private suspend fun processPositionUpdates() {
        for ((player, particleBatch) in positionUpdateChannel) {
            try {
                // Process in optimized batches
                val batchSize = PARTICLE_UPDATE_BATCH

                if (particleBatch.size > BATCH_SIZE && hasPlayersInRange.get()) {
                    // Parallel processing for large batches
                    withContext(Dispatchers.IO) {
                        val batches = particleBatch.chunked(batchSize * 2)
                        batches.map { subBatch ->
                            async {
                                for (particle in subBatch) {
                                    particle.updatePosition(player)
                                }
                            }
                        }.awaitAll()
                    }
                } else {
                    // Sequential processing for smaller batches
                    for (i in particleBatch.indices step batchSize) {
                        val end = min(i + batchSize, particleBatch.size)
                        for (j in i until end) {
                            particleBatch[j].updatePosition(player)
                        }
                        yield()
                    }
                }

                recordActivity()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error in position update processor: ${e.message}")
            }
        }
    }

    /**
     * Process player updates
     */
    private suspend fun processPlayerUpdates() {
        for (update in playerUpdateChannel) {
            try {
                updateVisiblePlayers()
                recordActivity()
                delay(PLAYER_UPDATE_INTERVAL)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error in player update: ${e.message}")
                delay(PLAYER_UPDATE_INTERVAL * 2)
            }
        }
    }

    /**
     * Process heartbeat to detect freezes
     */
    private suspend fun processHeartbeat() {
        for (update in heartbeatChannel) {
            try {
                val currentTime = (System.currentTimeMillis() / 1000).toInt()
                val lastActivity = lastActivityTimestamp.get()

                // If no activity for more than 10 seconds, restart the emitter
                if (currentTime - lastActivity > 10 && isRunning.get()) {
                    Defcon.instance.logger.warning("Detected potential particle freeze - restarting emitter")
                    reset()
                }

                delay(HEARTBEAT_INTERVAL)
                heartbeatChannel.send(Unit) // Schedule next heartbeat
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error in heartbeat: ${e.message}")
                delay(HEARTBEAT_INTERVAL)
                heartbeatChannel.send(Unit) // Retry heartbeat
            }
        }
    }

    /**
     * Process update ticks with improved memory management
     */
    private suspend fun processUpdateTicks() {
        for (delta in updateTickChannel) {
            try {
                // Spawn new particles if needed
                if (activeCount.get() < maxParticles &&
                    isRunning.get() &&
                    visible &&
                    spawnableParticles.isNotEmpty() &&
                    (spawnProbability >= 1.0f || Random.nextFloat() < spawnProbability)
                ) {
                    val availableCapacity = maxParticles - activeCount.get()
                    if (availableCapacity > 0) {
                        val particlesToCreate = min(particlesPerFrame, availableCapacity)
                        val batch = ArrayList<AbstractParticle>(particlesToCreate)

                        repeat(particlesToCreate) {
                            val index = Random.nextInt(spawnableParticles.size)
                            batch.add(spawnableParticles[index])
                        }

                        particleSpawnChannel.send(batch)
                    }
                }

                // Process particles in optimized batches
                val currentParticles = particles.values
                val size = currentParticles.size

                if (size > 0) {
                    if (size > BATCH_SIZE) {
                        // For large collections, process in parallel
                        val batchSize = 500

                        coroutineScope {
                            currentParticles.chunked(batchSize).forEach { batch ->
                                launch {
                                    updateParticleRange(batch, delta.toDouble())
                                }
                            }
                        }
                    } else {
                        // For smaller collections, process sequentially
                        updateParticleRange(currentParticles, delta.toDouble())
                    }
                }

                recordActivity()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error processing update tick: ${e.message}")
            }
        }
    }

    /**
     * Spawn particles in a batch with improved memory efficiency
     */
    private suspend fun spawnParticleBatch(particleBatch: List<AbstractParticle>) {
        if (activeCount.get() >= maxParticles || !visible || !isRunning.get()) return

        val playersInRange = visiblePlayers.keys.toList()
        val particlesToSpawn = ArrayList<ParticleInstance>(particleBatch.size)

        try {
            for (particle in particleBatch) {
                if (activeCount.get() >= maxParticles) break

                // Create new particle from template
                val newParticle = particle2D {
                    withTemplate(particle)
                    withOrigin(origin)
                    if (positionColorSupply == null) {
                        colorSupply?.let { withColor(it.invoke()) }
                    }
                    positionModifier { instance ->
                        // Apply shape and transform
                        if (emitterShape != PointShape) {
                            emitterShape.maskLoc(instance.position)
                            transform.transformPosition(instance.position)
                            positionColorSupply?.let {
                                withColor(it.invoke(instance.position, emitterShape))
                            }
                            shapeMutator?.mutateLoc(instance.position)
                        } else {
                            transform.transformPosition(instance.position)
                        }
                    }
                    velocityModifier { instance ->
                        if (radialVelocity.lengthSquared() > 0) {
                            instance.velocity
                                .set(instance.position.x, instance.position.y, instance.position.z)
                                .normalize()
                                .mul(radialVelocity)

                            instance.velocity.add(particle.particleProperties.initialVelocity)
                        } else {
                            instance.velocity.set(particle.particleProperties.initialVelocity)
                        }
                    }
                }
                val particleId = System.identityHashCode(newParticle)


                // Add to collection
                particles[particleId] = newParticle
                activeCount.incrementAndGet()
                particlesToSpawn.add(newParticle)
                updateTimes[newParticle] = System.currentTimeMillis()
            }

            // Send spawn packets to players in efficient batches
            if (playersInRange.isNotEmpty() && particlesToSpawn.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    for (player in playersInRange) {
                        for (batch in particlesToSpawn.chunked(PARTICLE_UPDATE_BATCH)) {
                            for (particle in batch) {
                                particle.show(player)
                            }
                            yield()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Defcon.instance.logger.warning("Error in particle spawn: ${e.message}")
        }
    }

    /**
     * Remove particles in batch with improved cleanup
     */
    private suspend fun removeParticleBatch(particleBatch: List<ParticleInstance>) {
        if (particleBatch.isEmpty()) return

        val playersInRange = visiblePlayers.keys.toList()

        try {
            // Collect client-side particles for batch processing
            val clientParticles = particleBatch.filterIsInstance<ClientSideParticleInstance>()

            // Batch despawn for efficiency
            if (clientParticles.isNotEmpty() && playersInRange.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    for (player in playersInRange) {
                        try {
                            ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                        } catch (e: Exception) {
                            Defcon.instance.logger.warning("Failed to despawn particles for player ${player.name}: ${e.message}")
                        }
                    }
                }
            }

            // Remove particles from collections
            for (particle in particleBatch) {
                val id = System.identityHashCode(particle)
                particles.remove(id)
                updateTimes.remove(particle)
            }

            // Update count
            activeCount.addAndGet(-particleBatch.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Defcon.instance.logger.warning("Error removing particle batch: ${e.message}")
        }
    }

    /**
     * Update particles with LOD factor application and better memory management
     */
    private suspend fun updateParticleRange(particleRange: Collection<ParticleInstance>, delta: Double) {
        if (particleRange.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val playersInRange = visiblePlayers.keys.toList()
        val hasPlayers = playersInRange.isNotEmpty()
        hasPlayersInRange.set(hasPlayers)

        try {
            // Prepare collections with proper initial capacity
            val particlesToUpdate = ArrayList<ClientSideParticleInstance>(
                if (hasPlayers) min(particleRange.size / 4, 1000) else 0
            )
            val particlesToRemove = ArrayList<ParticleInstance>(
                min(particleRange.size / 10, 500)
            )

            // Get current player LOD levels
            val playerLodMap = HashMap<Player, Int>(playersInRange.size)
            for (player in playersInRange) {
                val lodInfo = visiblePlayers[player] ?: continue
                playerLodMap[player] = lodInfo.lodLevel
            }

            // Process particles with LOD-based timing
            for (particle in particleRange) {
                // Determine LOD level to use
                val maxLodFactor = if (!hasPlayers) {
                    LOD_INACTIVE
                } else {
                    playerLodMap.values.maxOrNull() ?: LOD_FAR
                }

                // Apply LOD-based update schedule
                val updateInterval = when (maxLodFactor) {
                    LOD_CLOSE -> 12L
                    LOD_MEDIUM -> 25L
                    LOD_INACTIVE -> 1000L
                    else -> 50L
                }

                // Track last update time
                val lastUpdate = updateTimes[particle] ?: currentTime

                if (currentTime - lastUpdate >= updateInterval) {
                    // Apply scaled delta based on LOD
                    val scaledDelta = delta * maxLodFactor
                    val positionChanged = particle.update(scaledDelta)
                    updateTimes[particle] = currentTime

                    // Add to position update list if needed
                    if (positionChanged && particle is ClientSideParticleInstance) {
                        particlesToUpdate.add(particle)
                    }

                    // Add to removal list if needed
                    if (particle.isDead()) {
                        particlesToRemove.add(particle)
                    }
                }
            }

            // Send position updates to players in optimized batches
            if (particlesToUpdate.isNotEmpty() && playersInRange.isNotEmpty()) {
                // Group updates by player for better batching
                for (player in playersInRange) {
                    positionUpdateChannel.send(player to particlesToUpdate)
                }
            }

            // Queue particles for removal
            if (particlesToRemove.isNotEmpty()) {
                particleRemoveChannel.send(particlesToRemove)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Defcon.instance.logger.warning("Error updating particle range: ${e.message}")
        }
    }

    /**
     * Get LOD level based on player distance
     */
    private fun getLodLevel(playerLocation: Location): Int {
        val dx = origin.x - playerLocation.x.toFloat()
        val dy = origin.y - playerLocation.y.toFloat()
        val dz = origin.z - playerLocation.z.toFloat()
        val distSq = dx * dx + dy * dy + dz * dz

        return when {
            distSq < LOD_CLOSE_DISTANCE_SQ -> LOD_CLOSE
            distSq < LOD_MEDIUM_DISTANCE_SQ -> LOD_MEDIUM
            else -> LOD_FAR
        }
    }

    /**
     * Update visible players with improved error handling
     */
    private suspend fun updateVisiblePlayers() {
        val currentTime = System.currentTimeMillis()
        val newVisiblePlayers = HashMap<Player, PlayerLodInfo>()
        val playersNoLongerVisible = HashSet<Player>()

        try {
            // Identify players to add or remove
            for (player in world.players) {
                if (!player.isValid || player.isDead) continue

                val playerLocation = player.location
                if (playerLocation.world != world) continue

                val distSquared = origin.distanceSquared(
                    playerLocation.x.toFloat(),
                    playerLocation.y.toFloat(), playerLocation.z.toFloat()
                )

                if (distSquared < rangeSquared) {
                    // Player is in range - update LOD level
                    val lodLevel = getLodLevel(playerLocation)
                    val playerInfo = PlayerLodInfo(lodLevel, currentTime)
                    newVisiblePlayers[player] = playerInfo

                    // If this is a new player in range and particles are visible
                    val existingInfo = visiblePlayers[player]
                    if (existingInfo == null && visible) {
                        // Send spawn packets for existing particles
                        val clientParticles = particles.values.filterIsInstance<ClientSideParticleInstance>()
                        try {
                            for (batch in clientParticles.chunked(PARTICLE_UPDATE_BATCH * 2)) {
                                for (particle in batch) {
                                    particle.sendSpawnPacket(player)
                                }
                                yield()
                            }
                        } catch (e: Exception) {
                            Defcon.instance.logger.warning("Failed to spawn particles for player ${player.name}: ${e.message}")
                        }
                    }
                } else if (visiblePlayers.containsKey(player)) {
                    // Player went out of range
                    playersNoLongerVisible.add(player)
                }
            }

            // Handle players who went out of range
            if (playersNoLongerVisible.isNotEmpty()) {
                val clientParticles = particles.values.filterIsInstance<ClientSideParticleInstance>()
                for (player in playersNoLongerVisible) {
                    try {
                        ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                        visiblePlayers.remove(player)
                    } catch (e: Exception) {
                        Defcon.instance.logger.warning("Failed to despawn particles for player ${player.name}: ${e.message}")
                    }
                }
            }

            // Update visible players
            visiblePlayers.clear()
            visiblePlayers.putAll(newVisiblePlayers)

            // Update player presence flag
            hasPlayersInRange.set(newVisiblePlayers.isNotEmpty())
        } catch (e: Exception) {
            Defcon.instance.logger.warning("Error updating visible players: ${e.message}")
        }
    }

    /**
     * Initialize the emitter and start all processes
     */
    override fun start() {
        // Initialize state
        activeCount.set(0)
        isRunning.set(true)
        recordActivity()

        // Clear collections
        particles.clear()
        visiblePlayers.clear()
        updateTimes.clear()

        // Start flow processors
        emitterScope.launch {
            try {
                // Start all channel processors
                launch { processParticleSpawns() }
                launch { processParticleRemovals() }
                launch { processPositionUpdates() }
                launch { processPlayerUpdates() }
                launch { processUpdateTicks() }
                launch { processHeartbeat() }

                // Start player tracking
                playerUpdateChannel.send(Unit)

                // Start heartbeat monitor
                heartbeatChannel.send(Unit)

                // Pre-spawn particles if needed
                if (visible && spawnableParticles.isNotEmpty()) {
                    val initialParticles = min((maxParticles * 0.3).toInt(), 1500)
                    val adaptiveBatchSize = min(BURST_BATCH_SIZE, initialParticles / 4)

                    for (i in 0 until initialParticles step adaptiveBatchSize) {
                        val batchEnd = min(i + adaptiveBatchSize, initialParticles)
                        val batch = ArrayList<AbstractParticle>(batchEnd - i)

                        for (j in i until batchEnd) {
                            val index = Random.nextInt(spawnableParticles.size)
                            batch.add(spawnableParticles[index])
                        }

                        particleSpawnChannel.send(batch)
                        delay(5)
                    }
                }
            } catch (e: CancellationException) {
                // Expected when scope is canceled
            } catch (e: Exception) {
                Defcon.instance.logger.severe("Critical error starting particle emitter: ${e.message}")
                e.printStackTrace()
                stop()
            }
        }
    }

    /**
     * Update the emitter state with timeout detection
     */
    override fun update(delta: Float) {
        if (!isRunning.get()) return

        // Send update tick to the channel
        val success = updateTickChannel.trySend(delta).isSuccess

        // Record activity if update was successful
        if (success) {
            recordActivity()
        }
    }

    /**
     * Stop the emitter and clean up resources
     */
    override fun stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return // Already stopping or stopped
        }

        // Clean up in a controlled way
        emitterScope.launch {
            try {
                // Despawn particles for all players
                val players = visiblePlayers.keys.toList()
                if (players.isNotEmpty()) {
                    val clientParticles = particles.values.filterIsInstance<ClientSideParticleInstance>()
                    for (player in players) {
                        try {
                            ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                        } catch (e: Exception) {
                            Defcon.instance.logger.warning("Error despawning particles for player ${player.name}: ${e.message}")
                        }
                    }
                }

                // Wait briefly for any final operations
                withTimeoutOrNull(500L) {
                    delay(100)
                }

                // Clear collections
                particles.clear()
                visiblePlayers.clear()
                updateTimes.clear()
                activeCount.set(0)

            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error during emitter shutdown: ${e.message}")
            } finally {
                // Cancel all coroutines
                emitterScope.coroutineContext.cancelChildren()
            }
        }
    }

    /**
     * Get current particle count
     */
    fun getParticleCount(): Int = activeCount.get()

    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): Map<String, Any> {
        return mapOf(
            "activeParticles" to activeCount.get(),
            "playerCount" to visiblePlayers.size,
            "lastActivity" to (System.currentTimeMillis() / 1000 - lastActivityTimestamp.get())
        )
    }

    /**
     * Get players in range
     */
    fun getPlayersInRange(): List<Player> {
        return visiblePlayers.keys.toList()
    }

    /**
     * Reset the emitter with improved robustness
     */
    fun reset() {
        // Stop current operations
        isRunning.set(false)

        emitterScope.launch {
            try {
                // Despawn all particles
                val players = getPlayersInRange()
                if (players.isNotEmpty()) {
                    val clientParticles = particles.values.filterIsInstance<ClientSideParticleInstance>()
                    for (player in players) {
                        try {
                            ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                        } catch (e: Exception) {
                            Defcon.instance.logger.warning("Error despawning particles during reset: ${e.message}")
                        }
                    }
                }

                // Wait for pending operations to complete or timeout
                withTimeoutOrNull(500L) {
                    delay(100) // Brief delay to allow any in-flight operations to complete
                }

                // Cancel jobs with proper cleanup
                emitterScope.coroutineContext.cancelChildren()

                // Clear collections
                particles.clear()
                visiblePlayers.clear()
                updateTimes.clear()

                // Reset counters
                activeCount.set(0)
                recordActivity()

                // Wait briefly before restart to ensure clean state
                delay(50)

                // Restart
                isRunning.set(true)
                start()
            } catch (e: Exception) {
                Defcon.instance.logger.severe("Error during emitter reset: ${e.message}")
                e.printStackTrace()

                // Force restart even after error
                isRunning.set(true)
                start()
            }
        }
    }


    /**
     * Set particle spawn rate dynamically
     * @param particlesPerSecond Number of particles to spawn per second
     */
    fun setSpawnRate(particlesPerSecond: Int) {
        // Convert particles per second to particles per frame (assuming 20 ticks/second)
        this.particlesPerFrame = (particlesPerSecond / 20).coerceAtLeast(1)
    }

    /**
     * Set new emitter position
     * @param location New location for the emitter
     */
    fun setPosition(location: Location) {
        // Update origin position
        origin.set(
            location.x.toFloat(),
            location.y.toFloat(),
            location.z.toFloat()
        )

        // Force player update on next cycle
        emitterScope.launch {
            updateVisiblePlayers()
        }
    }

    fun getShape(): T {
        return emitterShape
    }
}