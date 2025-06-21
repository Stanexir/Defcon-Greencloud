/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.Bukkit
import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.LinkedHashMap

class ChunkCache private constructor(
    private val world: World,
    private val maxAccessCount: Int = 20,
    private val useLocalCache: Boolean = true,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        // Shared cache with memory-efficient eviction
        private val sharedChunkCache = object : LinkedHashMap<Long, SoftReference<ChunkSnapshot>>(16, 0.75f, true) {
            private val MAX_SHARED_CACHE_SIZE = 5000
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, SoftReference<ChunkSnapshot>>): Boolean {
                return size > MAX_SHARED_CACHE_SIZE
            }
        }

        private val sharedCacheMutex = Mutex()
        private val instanceCache = ConcurrentHashMap<String, ChunkCache>()
        private val memoryStats = AtomicLong(0L)

        fun getInstance(
            world: World,
            maxAccessCount: Int = 20,
            useLocalCache: Boolean = true
        ): ChunkCache {
            val key = "${world.name}_${maxAccessCount}_$useLocalCache"
            return instanceCache.computeIfAbsent(key) {
                ChunkCache(world, maxAccessCount, useLocalCache)
            }
        }

        suspend fun cleanupAllCaches() {
            sharedCacheMutex.withLock {
                sharedChunkCache.clear()
            }
            instanceCache.values.forEach { it.cleanupLocalCache() }
            memoryStats.set(0L)
        }

        fun getMemoryUsage(): Long = memoryStats.get()

        // Efficient chunk key packing
        private fun packChunkKey(chunkX: Int, chunkZ: Int): Long {
            return (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
        }

        private fun unpackChunkX(key: Long): Int = (key shr 32).toInt()
        private fun unpackChunkZ(key: Long): Int = key.toInt()
    }

    // Local cache - only used if useLocalCache is true
    private val localCache = if (useLocalCache) {
        object : LinkedHashMap<Long, ChunkSnapshot>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ChunkSnapshot>): Boolean {
                if (size > maxAccessCount) {
                    // Move to shared cache before eviction
                    coroutineScope.launch {
                        sharedCacheMutex.withLock {
                            sharedChunkCache[eldest.key] = SoftReference(eldest.value)
                        }
                    }
                    return true
                }
                return false
            }
        }
    } else null

    // Async chunk loading queue
    private val loadingQueue = Channel<ChunkLoadRequest>(capacity = Channel.UNLIMITED)
    private val loadingJobs = ConcurrentHashMap<Long, Deferred<ChunkSnapshot>>()

    init {
        // Start async chunk loader
        coroutineScope.launch {
            for (request in loadingQueue) {
                processChunkLoadRequest(request)
            }
        }
    }

    private data class ChunkLoadRequest(
        val chunkKey: Long,
        val priority: Int = 0
    )

    private suspend fun processChunkLoadRequest(request: ChunkLoadRequest) {
        val chunkX = unpackChunkX(request.chunkKey)
        val chunkZ = unpackChunkZ(request.chunkKey)

        try {
            withContext(Dispatchers.Default) {
                val chunk = world.getChunkAtAsyncUrgently(chunkX, chunkZ).await()
                val snapshot = chunk.chunkSnapshot

                // Store in appropriate cache
                if (useLocalCache && localCache != null) {
                    localCache[request.chunkKey] = snapshot
                } else {
                    sharedCacheMutex.withLock {
                        sharedChunkCache[request.chunkKey] = SoftReference(snapshot)
                    }
                }

                memoryStats.addAndGet(estimateSnapshotSize(snapshot))
            }
        } finally {
            loadingJobs.remove(request.chunkKey)
        }
    }

    private fun estimateSnapshotSize(snapshot: ChunkSnapshot): Long {
        // Rough estimate: 16x16x384 blocks = ~100KB per chunk
        return 100_000L
    }

    suspend fun cleanupLocalCache() {
        localCache?.clear()
    }

    private suspend fun getChunkSnapshotAsync(x: Int, z: Int): ChunkSnapshot {
        val chunkX = x shr 4
        val chunkZ = z shr 4
        val chunkKey = packChunkKey(chunkX, chunkZ)

        // Check local cache first (if enabled)
        if (useLocalCache && localCache != null) {
            localCache[chunkKey]?.let { return it }
        }

        // Check shared cache
        val sharedSnapshot = sharedCacheMutex.withLock {
            sharedChunkCache[chunkKey]?.get()
        }
        if (sharedSnapshot != null) {
            // Move to local cache if enabled
            if (useLocalCache && localCache != null) {
                localCache[chunkKey] = sharedSnapshot
            }
            return sharedSnapshot
        }

        // Check if already loading
        loadingJobs[chunkKey]?.let { return it.await() }

        // Start async loading
        val deferred = coroutineScope.async {
            val chunk = world.getChunkAtAsync(chunkX, chunkZ).await()
            val snapshot = chunk.chunkSnapshot

            // Store in appropriate cache
            if (useLocalCache && localCache != null) {
                localCache[chunkKey] = snapshot
            } else {
                sharedCacheMutex.withLock {
                    sharedChunkCache[chunkKey] = SoftReference(snapshot)
                }
            }

            memoryStats.addAndGet(estimateSnapshotSize(snapshot))
            snapshot
        }

        loadingJobs[chunkKey] = deferred
        return deferred.await().also {
            loadingJobs.remove(chunkKey)
        }
    }

    // Bulk preloading with priority and batching
    suspend fun preloadChunksAsync(
        chunkKeys: Set<Long>,
        batchSize: Int = 10,
        priority: Int = 0
    ) {
        chunkKeys.chunked(batchSize).forEach { batch ->
            val jobs = batch.map { key ->
                coroutineScope.async {
                    val chunkX = unpackChunkX(key)
                    val chunkZ = unpackChunkZ(key)

                    // Skip if already cached
                    if (useLocalCache && localCache?.containsKey(key) == true) return@async

                    val sharedExists = sharedCacheMutex.withLock {
                        sharedChunkCache[key]?.get() != null
                    }
                    if (sharedExists) return@async

                    // Queue for loading
                    loadingQueue.trySend(ChunkLoadRequest(key, priority))
                }
            }
            jobs.awaitAll()

            // Small delay between batches to prevent server overload
            delay(5)
        }
    }

    // Convenience method for coordinate-based preloading
    suspend fun preloadChunksInRadius(
        centerX: Int,
        centerZ: Int,
        radius: Int,
        batchSize: Int = 10
    ) {
        val chunkKeys = mutableSetOf<Long>()
        val centerChunkX = centerX shr 4
        val centerChunkZ = centerZ shr 4

        for (x in (centerChunkX - radius)..(centerChunkX + radius)) {
            for (z in (centerChunkZ - radius)..(centerChunkZ + radius)) {
                chunkKeys.add(packChunkKey(x, z))
            }
        }

        preloadChunksAsync(chunkKeys, batchSize)
    }

    // Async block access methods
    suspend fun highestBlockYAtAsync(x: Int, z: Int): Int {
        return getChunkSnapshotAsync(x, z).getHighestBlockYAt(x and 15, z and 15)
    }

    suspend fun getBlockMaterialAsync(x: Int, y: Int, z: Int): Material {
        if (y < world.minHeight || y > world.maxHeight) return Material.AIR
        return getChunkSnapshotAsync(x, z).getBlockType(x and 15, y, z and 15)
    }

    suspend fun getBlockDataAsync(x: Int, y: Int, z: Int): BlockData {
        if (y < world.minHeight || y > world.maxHeight) return Bukkit.createBlockData(Material.AIR)
        return getChunkSnapshotAsync(x, z).getBlockData(x and 15, y, z and 15)
    }

    suspend fun getSkyLightLevelAsync(x: Int, y: Int, z: Int): Int {
        if (y < world.minHeight || y > world.maxHeight) return 0
        return getChunkSnapshotAsync(x, z).getBlockSkyLight(x and 15, y, z and 15)
    }

    // Batch processing for multiple blocks (very efficient for area processing)
    suspend fun getBlockMaterialsBatch(coordinates: List<Triple<Int, Int, Int>>): List<Material> {
        val groupedByChunk = coordinates.groupBy { (x, _, z) ->
            packChunkKey(x shr 4, z shr 4)
        }

        val results = Array<Material?>(coordinates.size) { null }

        groupedByChunk.entries.map { (chunkKey, coords) ->
            coroutineScope.async {
                val snapshot = getChunkSnapshotAsync(coords.first().first, coords.first().third)
                coords.forEachIndexed { _, (x, y, z) ->
                    val index = coordinates.indexOf(Triple(x, y, z))
                    results[index] = if (y < world.minHeight || y > world.maxHeight) {
                        Material.AIR
                    } else {
                        snapshot.getBlockType(x and 15, y, z and 15)
                    }
                }
            }
        }.awaitAll()

        return results.map { it ?: Material.AIR }
    }

    // Cache invalidation for specific chunks
    suspend fun invalidateChunk(chunkX: Int, chunkZ: Int) {
        val chunkKey = packChunkKey(chunkX, chunkZ)
        localCache?.remove(chunkKey)
        sharedCacheMutex.withLock {
            sharedChunkCache.remove(chunkKey)
        }
    }

    // Force refresh a chunk (useful when blocks have been modified)
    suspend fun refreshChunk(chunkX: Int, chunkZ: Int) {
        invalidateChunk(chunkX, chunkZ)
        // Preload the chunk again
        preloadChunksAsync(setOf(packChunkKey(chunkX, chunkZ)))
    }

    fun cleanup() {
        coroutineScope.cancel()
        localCache?.clear()
        loadingJobs.clear()
    }
}