package me.mochibit.defcon.biomes

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.chunked
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.utils.Logger
import me.mochibit.defcon.save.savedata.BiomeAreaSave
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


object CustomBiomeHandler {
    data class CustomBiomeBoundary(
        val id: Int = 0,
        val uuid: UUID,
        val biome: NamespacedKey,
        val minX: Int, val maxX: Int,
        val minY: Int, val maxY: Int,
        val minZ: Int, val maxZ: Int,
        val worldName: String,
        val priority: Int = 0,
        val transitions: List<BiomeTransition> = emptyList()
    ) {
        data class BiomeTransition(
            val transitionTime: Instant,
            val targetBiome: NamespacedKey,
            val targetPriority: Int = 0,
            val completed: Boolean = false,
        )

        @Transient
        private val chunkIntersectionCache = ConcurrentHashMap<Long, Boolean>()

        // Combined bounds checking
        fun isInBounds(x: Int, y: Int, z: Int): Boolean =
            x in minX..maxX && y in minY..maxY && z in minZ..maxZ

        fun intersectsChunk(chunkX: Int, chunkZ: Int): Boolean {
            val key = (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
            return chunkIntersectionCache.computeIfAbsent(key) {
                val chunkMinX = chunkX shl 4
                val chunkMaxX = chunkMinX + 15
                val chunkMinZ = chunkZ shl 4
                val chunkMaxZ = chunkMinZ + 15
                !(chunkMaxX < minX || chunkMinX > maxX || chunkMaxZ < minZ || chunkMinZ > maxZ)
            }
        }

        fun contains(other: CustomBiomeBoundary): Boolean =
            worldName == other.worldName &&
                    minX <= other.minX && maxX >= other.maxX &&
                    minY <= other.minY && maxY >= other.maxY &&
                    minZ <= other.minZ && maxZ >= other.maxZ

        fun getNextPendingTransition(): BiomeTransition? {
            val now = Instant.now()
            return transitions.asSequence()
                .filter { !it.completed && it.transitionTime.isBefore(now) }
                .minByOrNull { it.transitionTime }
        }

        fun clearCache() = chunkIntersectionCache.clear()

        // Immutable update methods
        fun withBiome(newBiome: NamespacedKey) = copy(biome = newBiome)
        fun withTransitions(newTransitions: List<BiomeTransition>) = copy(transitions = newTransitions)
        fun withPriority(newPriority: Int) = copy(priority = newPriority)
    }

    // Consolidated world management
    private val worldData = ConcurrentHashMap<String, WorldBiomeData>()
    private val loadedWorlds = Collections.synchronizedSet(HashSet<String>())

    // Flow-based player updates
    private val playerVisibilityUpdates = MutableSharedFlow<PlayerVisibilityUpdate>(
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Flow-based biome state changes
    private val biomeStateChanges = MutableSharedFlow<BiomeStateChange>(
        replay = 0,
        extraBufferCapacity = 100
    )

    private data class WorldBiomeData(
        val biomes: ConcurrentHashMap<UUID, CustomBiomeBoundary> = ConcurrentHashMap(),
        val spatialIndex: SpatialIndex = SpatialIndex(),
        val playerVisibility: ConcurrentHashMap<UUID, ConcurrentHashMap.KeySetView<UUID, Boolean>> = ConcurrentHashMap()
    )

    private data class PlayerVisibilityUpdate(
        val playerId: UUID,
        val worldName: String,
        val biomeId: UUID,
        val isVisible: Boolean
    )

    private data class BiomeStateChange(
        val biomeId: UUID,
        val worldName: String,
        val changeType: BiomeChangeType,
        val biome: CustomBiomeBoundary? = null
    )

    private enum class BiomeChangeType { CREATED, UPDATED, REMOVED, TRANSITIONED }

    private class SpatialIndex {
        private val chunkToBiomes = ConcurrentHashMap<Long, MutableSet<UUID>>()

        fun addBiome(biome: CustomBiomeBoundary) {
            getChunkRange(biome).forEach { chunkKey ->
                chunkToBiomes.computeIfAbsent(chunkKey) {
                    Collections.synchronizedSet(HashSet())
                }.add(biome.uuid)
            }
        }

        fun removeBiome(biome: CustomBiomeBoundary) {
            getChunkRange(biome).forEach { chunkKey ->
                chunkToBiomes[chunkKey]?.remove(biome.uuid)
            }
        }

        fun getBiomesInChunk(chunkX: Int, chunkZ: Int): Set<UUID> {
            val key = (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
            return chunkToBiomes[key]?.toSet() ?: emptySet()
        }

        private fun getChunkRange(biome: CustomBiomeBoundary): Sequence<Long> = sequence {
            val minChunkX = biome.minX shr 4
            val maxChunkX = biome.maxX shr 4
            val minChunkZ = biome.minZ shr 4
            val maxChunkZ = biome.maxZ shr 4

            for (chunkX in minChunkX..maxChunkX) {
                for (chunkZ in minChunkZ..maxChunkZ) {
                    yield((chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL))
                }
            }
        }

        fun clear() = chunkToBiomes.clear()
    }

    // Task management with flows
    private var taskScope: CoroutineScope? = null
    private var biomeMergeJob: Job? = null
    private var transitionCheckJob: Job? = null
    private var playerUpdateJob: Job? = null

    val MERGE_CHECK_INTERVAL = 5.minutes
    val TRANSITION_CHECK_INTERVAL = 30.seconds
    const val MAX_CHUNKS_PER_UPDATE = 8
    const val CHUNK_UPDATE_DELAY = 150L
    const val PLAYER_UPDATE_BATCH_SIZE = 50

    fun initialize() {
        taskScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // Periodic biome management tasks
        biomeMergeJob = taskScope?.launch {
            while (isActive) {
                delay(MERGE_CHECK_INTERVAL)
                processBiomeMerges()
            }
        }

        transitionCheckJob = taskScope?.launch {
            while (isActive) {
                delay(TRANSITION_CHECK_INTERVAL)
                processBiomeTransitions()
            }
        }

        // Flow-based player visibility updates
        playerUpdateJob = taskScope?.launch {
            playerVisibilityUpdates
                .buffer(capacity = PLAYER_UPDATE_BATCH_SIZE)
                .chunked(PLAYER_UPDATE_BATCH_SIZE)
                .collect { updates ->
                    processPlayerVisibilityUpdates(updates)
                }
        }

        Logger.info("CustomBiomeHandler initialized with flow-based processing")
    }

    fun shutdown() {
        taskScope?.cancel()
        biomeMergeJob = null
        transitionCheckJob = null
        playerUpdateJob = null

        worldData.values.forEach { data ->
            data.biomes.values.forEach { it.clearCache() }
        }
        worldData.clear()
        loadedWorlds.clear()

        Logger.info("CustomBiomeHandler shut down")
    }

    private suspend fun processBiomeMerges() {
        val mergeCandidates = mutableListOf<Pair<CustomBiomeBoundary, CustomBiomeBoundary>>()

        worldData.values.forEach { worldData ->
            val biomes = worldData.biomes.values.toList()
            for (i in biomes.indices) {
                for (j in i + 1 until biomes.size) {
                    val biome1 = biomes[i]
                    val biome2 = biomes[j]

                    when {
                        biome1.contains(biome2) && biome1.priority > biome2.priority ->
                            mergeCandidates.add(biome1 to biome2)

                        biome2.contains(biome1) && biome2.priority > biome1.priority ->
                            mergeCandidates.add(biome2 to biome1)
                    }
                }
            }
        }

        mergeCandidates.forEach { (container, contained) ->
            mergeCustomBiomes(container, contained)
        }
    }

    private suspend fun processBiomeTransitions() {
        val transitionsToApply = mutableListOf<Pair<CustomBiomeBoundary, CustomBiomeBoundary.BiomeTransition>>()

        worldData.values.forEach { worldData ->
            worldData.biomes.values.forEach { biome ->
                biome.getNextPendingTransition()?.let { transition ->
                    transitionsToApply.add(biome to transition)
                }
            }
        }

        transitionsToApply.forEach { (biome, transition) ->
            applyBiomeTransition(biome, transition)
        }
    }

    private suspend fun processPlayerVisibilityUpdates(updates: List<PlayerVisibilityUpdate>) {
        val groupedUpdates = updates.groupBy { it.playerId }

        groupedUpdates.forEach { (playerId, playerUpdates) ->
            val player = Defcon.instance.server.getPlayer(playerId) ?: return@forEach

            playerUpdates.forEach { update ->
                if (player.world.name == update.worldName) {
                    updateClientSideBiomeChunks(player, update.biomeId, update.isVisible)
                }
            }
        }
    }

    // Optimized biome management methods
    fun isWorldLoaded(worldName: String): Boolean = loadedWorlds.contains(worldName)

    fun markWorldAsLoaded(worldName: String) {
        loadedWorlds.add(worldName)
        worldData.computeIfAbsent(worldName) { WorldBiomeData() }
    }

    fun activateBiome(biome: CustomBiomeBoundary) {
        val data = worldData.computeIfAbsent(biome.worldName) { WorldBiomeData() }
        data.biomes[biome.uuid] = biome
        data.spatialIndex.addBiome(biome)

        // Emit state change
        biomeStateChanges.tryEmit(
            BiomeStateChange(biome.uuid, biome.worldName, BiomeChangeType.CREATED, biome)
        )
    }

    fun activateBiomes(worldName: String, biomes: Collection<CustomBiomeBoundary>) {
        val data = worldData.computeIfAbsent(worldName) { WorldBiomeData() }

        biomes.forEach { biome ->
            data.biomes[biome.uuid] = biome
            data.spatialIndex.addBiome(biome)
        }
    }

    fun addBiomeVisibilityForPlayer(playerId: UUID, biomeId: UUID, worldName: String) {
        val data = worldData[worldName] ?: return
        val playerBiomes = data.playerVisibility.computeIfAbsent(playerId) {
            ConcurrentHashMap.newKeySet()
        }

        if (playerBiomes.add(biomeId)) {
            playerVisibilityUpdates.tryEmit(
                PlayerVisibilityUpdate(playerId, worldName, biomeId, true)
            )
        }
    }

    fun removeBiomeVisibilityFromPlayer(playerId: UUID, biomeId: UUID, worldName: String) {
        val data = worldData[worldName] ?: return
        val removed = data.playerVisibility[playerId]?.remove(biomeId) == true

        if (removed) {
            playerVisibilityUpdates.tryEmit(
                PlayerVisibilityUpdate(playerId, worldName, biomeId, false)
            )
        }
    }

    fun getBiomesInChunk(worldName: String, chunkX: Int, chunkZ: Int): Collection<CustomBiomeBoundary> {
        val data = worldData[worldName] ?: return emptySet()
        val biomeIds = data.spatialIndex.getBiomesInChunk(chunkX, chunkZ)
        return biomeIds.mapNotNull { data.biomes[it] }
    }

    fun getBiomeAtLocation(location: Location): CustomBiomeBoundary? {
        val data = worldData[location.world.name] ?: return null
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        val chunkX = x shr 4
        val chunkZ = z shr 4

        val candidateBiomes = data.spatialIndex.getBiomesInChunk(chunkX, chunkZ)
            .mapNotNull { data.biomes[it] }
            .filter { it.isInBounds(x, y, z) }

        return candidateBiomes.maxByOrNull { it.priority }
    }

    fun getPlayerVisibleBiomes(playerId: UUID, worldName: String): Set<CustomBiomeBoundary> {
        val data = worldData[worldName] ?: return emptySet()
        val biomeIds = data.playerVisibility[playerId] ?: return emptySet()
        return biomeIds.mapNotNull { data.biomes[it] }.toSet()
    }

    fun getAllActiveBiomesInWorld(worldName: String): Collection<CustomBiomeBoundary> =
        worldData[worldName]?.biomes?.values ?: emptySet()

    fun getAllActiveBiomes(): Collection<CustomBiomeBoundary> =
        worldData.values.flatMap { it.biomes.values }

    // Simplified biome creation with flow integration
    suspend fun createBiomeArea(
        center: Location,
        biome: CustomBiome,
        lengthPositiveY: Int, lengthNegativeY: Int,
        lengthPositiveX: Int, lengthNegativeX: Int,
        lengthPositiveZ: Int, lengthNegativeZ: Int,
        priority: Int = 0,
        transitions: List<CustomBiomeBoundary.BiomeTransition> = emptyList()
    ): UUID = withContext(Dispatchers.Default) {
        val worldName = center.world.name
        val biomeId = UUID.randomUUID()

        val boundary = CustomBiomeBoundary(
            uuid = biomeId,
            biome = biome.asBukkitBiome.key,
            minX = center.blockX - lengthNegativeX,
            maxX = center.blockX + lengthPositiveX,
            minY = (center.blockY - lengthNegativeY).coerceAtLeast(center.world.minHeight),
            maxY = (center.blockY + lengthPositiveY).coerceAtMost(center.world.maxHeight),
            minZ = center.blockZ - lengthNegativeZ,
            maxZ = center.blockZ + lengthPositiveZ,
            worldName = worldName,
            priority = priority,
            transitions = transitions
        )

        markWorldAsLoaded(worldName)
        val biomeSave = BiomeAreaSave.getSave(worldName)
        val savedBoundary = biomeSave.addBiome(boundary)

        activateBiome(savedBoundary)
        updateAffectedPlayers(savedBoundary)

        biomeId
    }

    private suspend fun updateAffectedPlayers(boundary: CustomBiomeBoundary) {
        val world = Defcon.instance.server.getWorld(boundary.worldName) ?: return

        world.players.forEach { player ->
            val playerChunkX = player.location.blockX shr 4
            val playerChunkZ = player.location.blockZ shr 4

            if (boundary.intersectsChunk(playerChunkX, playerChunkZ)) {
                addBiomeVisibilityForPlayer(player.uniqueId, boundary.uuid, boundary.worldName)
            }
        }
    }

    private suspend fun updateClientSideBiomeChunks(player: Player, biomeId: UUID, isVisible: Boolean) {
        val worldName = player.world.name
        val data = worldData[worldName] ?: return
        val biome = data.biomes[biomeId] ?: return

        if (!isVisible) return // Skip chunk updates for removed biomes for now

        val playerChunkX = player.location.blockX shr 4
        val playerChunkZ = player.location.blockZ shr 4
        val viewDistance = player.viewDistance.coerceAtMost(6)

        val chunksToUpdate = mutableSetOf<Pair<Int, Int>>()
        val minChunkX = (biome.minX shr 4).coerceAtLeast(playerChunkX - viewDistance)
        val maxChunkX = (biome.maxX shr 4).coerceAtMost(playerChunkX + viewDistance)
        val minChunkZ = (biome.minZ shr 4).coerceAtLeast(playerChunkZ - viewDistance)
        val maxChunkZ = (biome.maxZ shr 4).coerceAtMost(playerChunkZ + viewDistance)

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                if (player.world.isChunkLoaded(chunkX, chunkZ)) {
                    chunksToUpdate.add(chunkX to chunkZ)
                }
            }
        }

        chunksToUpdate.take(MAX_CHUNKS_PER_UPDATE).forEach { (chunkX, chunkZ) ->
            try {
                withContext(Dispatchers.IO) {
                    player.world.refreshChunk(chunkX, chunkZ)
                }
                delay(CHUNK_UPDATE_DELAY)
            } catch (e: Exception) {
                Logger.warn("Failed to refresh chunk at $chunkX, $chunkZ for player ${player.name}")
            }
        }
    }

    // Remaining methods simplified and optimized...
    suspend fun removeBiomeArea(biomeId: UUID): Boolean = withContext(Dispatchers.Default) {
        val (biome, worldName) = findBiomeInAnyWorld(biomeId) ?: return@withContext false
        val data = worldData[worldName] ?: return@withContext false

        data.biomes.remove(biomeId)
        data.spatialIndex.removeBiome(biome)

        // Update affected players
        data.playerVisibility.keys.forEach { playerId ->
            removeBiomeVisibilityFromPlayer(playerId, biomeId, worldName)
        }

        biomeStateChanges.tryEmit(
            BiomeStateChange(biomeId, worldName, BiomeChangeType.REMOVED)
        )

        BiomeAreaSave.getSave(worldName).delete(biome.id)
    }

    private fun findBiomeInAnyWorld(biomeId: UUID): Pair<CustomBiomeBoundary, String>? {
        worldData.forEach { (worldName, data) ->
            data.biomes[biomeId]?.let { biome ->
                return biome to worldName
            }
        }
        return null
    }

    fun unloadBiomesForWorld(worldName: String) {
        worldData.remove(worldName)?.let { data ->
            data.biomes.values.forEach { it.clearCache() }
            data.spatialIndex.clear()
        }
        loadedWorlds.remove(worldName)
        Logger.info("Unloaded biomes for world: $worldName")
    }

    fun cleanupPlayer(playerId: UUID) {
        worldData.values.forEach { data ->
            data.playerVisibility.remove(playerId)
        }
    }

    private suspend fun mergeCustomBiomes(container: CustomBiomeBoundary, contained: CustomBiomeBoundary) {
        removeBiomeArea(contained.uuid)
    }

    private suspend fun applyBiomeTransition(
        biome: CustomBiomeBoundary,
        transition: CustomBiomeBoundary.BiomeTransition
    ) {
        val updatedBiome = biome
            .withBiome(transition.targetBiome)
            .withPriority(transition.targetPriority)
            .withTransitions(biome.transitions.map {
                if (it == transition) it.copy(completed = true) else it
            })

        val data = worldData[biome.worldName] ?: return
        data.biomes[biome.uuid] = updatedBiome

        BiomeAreaSave.getSave(biome.worldName).updateBiome(updatedBiome)

        biomeStateChanges.tryEmit(
            BiomeStateChange(biome.uuid, biome.worldName, BiomeChangeType.TRANSITIONED, updatedBiome)
        )
    }
}