package me.mochibit.defcon.explosions.processor

import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.Geometry
import me.mochibit.defcon.utils.Geometry.wangNoise
import org.bukkit.Location
import org.bukkit.Material
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.*

/**
 * Optimized crater generation with improved performance and cleaner architecture
 */
class Crater(
    private val center: Location,
    private val radiusX: Int,
    private val radiusY: Int, // Depth for the paraboloid
    private val radiusZ: Int,
    private val transformationRule: TransformationRule,
    private val destructionHeight: Int,
) {
    // Immutable configuration
    private val config = CraterConfig(center, radiusX, radiusY, radiusZ, destructionHeight)
    private val world = center.world!!
    private val chunkCache = ChunkCache.getInstance(world)
    private val blockChanger = BlockChanger.getInstance(world)

    // Optimized scorch materials (ordered from least to most intense)
    private val scorchMaterials = arrayOf(
        Material.TUFF,
        Material.DEEPSLATE,
        Material.BASALT,
        Material.BLACKSTONE,
        Material.COAL_BLOCK,
        Material.BLACK_CONCRETE_POWDER,
        Material.BLACK_CONCRETE,
    )

    suspend fun create(): Int {
        return try {
            createOptimizedCrater()
        } catch (e: Exception) {
            e.printStackTrace()
            maxOf(radiusX, radiusZ)
        } finally {
            chunkCache.cleanupCache()
        }
    }

    private suspend fun createOptimizedCrater(): Int = coroutineScope {
        // Use BitSet for more memory-efficient crater tracking
        val craterShape = CraterShape(config)

        // Process crater in optimized chunks
        val effectiveRadius = processInParallelChunks(craterShape)

        // Apply rim effects after main processing
        applyRimEffects(craterShape)

        effectiveRadius
    }

    private suspend fun processInParallelChunks(craterShape: CraterShape): Int = coroutineScope {
        val chunkRegions = generateChunkRegions()

        // Process regions with limited concurrency to avoid overwhelming the server
        val semaphore = Semaphore(min(chunkRegions.size, 4)) // Limit concurrent chunks

        val regionJobs = chunkRegions.map { region ->
            async {
                semaphore.withPermit {
                    processRegion(region, craterShape)
                }
            }
        }

        val results = regionJobs.awaitAll()
        val maxDistSq = results.maxOfOrNull { it } ?: 0.0

        sqrt(maxDistSq).roundToInt()
    }

    private fun generateChunkRegions(): List<ChunkRegion> {
        val regions = mutableListOf<ChunkRegion>()
        val chunkMinX = config.minX shr 4
        val chunkMaxX = config.maxX shr 4
        val chunkMinZ = config.minZ shr 4
        val chunkMaxZ = config.maxZ shr 4

        for (chunkX in chunkMinX..chunkMaxX) {
            for (chunkZ in chunkMinZ..chunkMaxZ) {
                val startX = maxOf(chunkX shl 4, config.minX)
                val startZ = maxOf(chunkZ shl 4, config.minZ)
                val endX = minOf((chunkX shl 4) + 15, config.maxX)
                val endZ = minOf((chunkZ shl 4) + 15, config.maxZ)

                regions.add(ChunkRegion(startX, startZ, endX, endZ))
            }
        }
        return regions
    }

    private suspend fun processRegion(region: ChunkRegion, craterShape: CraterShape): Double {
        var maxDistSq = 0.0

        for (x in region.startX..region.endX) {
            val xOffset = x - config.centerX
            val xComponent = xOffset * xOffset * config.invRadiusXSquared

            if (xComponent > 1.0) continue

            for (z in region.startZ..region.endZ) {
                val zOffset = z - config.centerZ
                val zComponent = zOffset * zOffset * config.invRadiusZSquared
                val normalizedDistSq = xComponent + zComponent

                if (normalizedDistSq <= 1.0) {
                    val result = processPoint(x, z, normalizedDistSq, craterShape)
                    if (result > 0) {
                        maxDistSq = maxOf(maxDistSq, result)
                    }
                }
            }
        }

        return maxDistSq
    }

    private suspend fun processPoint(x: Int, z: Int, normalizedDistSq: Double, craterShape: CraterShape): Double {
        // Calculate crater floor depth using paraboloid equation
        val depth = config.radiusY * (1.0 - normalizedDistSq)
        val floorY = (config.centerY - depth).roundToInt()

        // Mark position in crater shape for rim detection
        craterShape.markPosition(x, z, floorY)

        if (floorY < config.minY) return 0.0

        // Apply floor scorching and get distance
        val scorchDist = applyFloorScorching(x, z, floorY, normalizedDistSq)

        // Remove blocks above crater floor
        removeBlocksAboveFloor(x, z, floorY)

        return scorchDist
    }

    private suspend fun applyFloorScorching(x: Int, z: Int, floorY: Int, normalizedDistSq: Double): Double {
        val blockType = chunkCache.getBlockMaterial(x, floorY, z)

        if (canScorchBlock(blockType)) {
            val material = selectScorchMaterial(sqrt(normalizedDistSq), x, z)
            blockChanger.addBlockChange(x, floorY, z, material, updateBlock = false)

            val xDist = x - config.centerX
            val zDist = z - config.centerZ
            return (xDist * xDist + zDist * zDist).toDouble()
        }

        return 0.0
    }

    private suspend fun removeBlocksAboveFloor(x: Int, z: Int, floorY: Int) {
        val maxRemovalY = minOf(config.centerY + destructionHeight, config.maxY)

        for (y in (floorY + 1)..maxRemovalY) {
            val blockType = chunkCache.getBlockMaterial(x, y, z)
            if (canRemoveBlock(blockType)) {
                blockChanger.addBlockChange(x, y, z, Material.AIR, updateBlock = false)
            }
        }
    }

    private suspend fun applyRimEffects(craterShape: CraterShape) = coroutineScope {
        // Process rim in parallel with limited concurrency
        val rimJobs = (config.minX..config.maxX step 4).map { startX ->
            async {
                val endX = minOf(startX + 3, config.maxX)
                for (x in startX..endX) {
                    processRimColumn(x, craterShape)
                }
            }
        }

        rimJobs.awaitAll()
    }

    private suspend fun processRimColumn(x: Int, craterShape: CraterShape) {
        for (z in config.minZ..config.maxZ) {
            if (!craterShape.isInCrater(x, z) && isRimPosition(x, z, craterShape)) {
                processRimPosition(x, z)
            }
        }
    }

    private fun isRimPosition(x: Int, z: Int, craterShape: CraterShape): Boolean {
        // Check 8-directional neighbors
        val offsets = arrayOf(-1, 0, 1)

        for (dx in offsets) {
            for (dz in offsets) {
                if (dx == 0 && dz == 0) continue

                val nx = x + dx
                val nz = z + dz

                if (nx in config.minX..config.maxX &&
                    nz in config.minZ..config.maxZ &&
                    craterShape.isInCrater(nx, nz)) {
                    return true
                }
            }
        }

        return false
    }

    private suspend fun processRimPosition(x: Int, z: Int) {
        val topY = findTopBlock(x, z) ?: return

        // Calculate rim scorch intensity
        val distance = calculateNormalizedDistance(x, z)
        val rimThreshold = 1.4

        if (distance < rimThreshold) {
            val intensity = maxOf(0.0, 1.0 - (distance / rimThreshold))
            val material = selectScorchMaterial(1.0 - intensity, x, z)
            blockChanger.addBlockChange(x, topY, z, material, updateBlock = true)
        }
    }

    private fun findTopBlock(x: Int, z: Int): Int? {
        // Use binary search for efficiency
        val low = config.minY
        var high = config.maxY
        var result: Int? = null

        while (low <= high) {
            val mid = (low + high) / 2
            val hasBlock = hasNonAirBlock(x, z, mid, config.maxY)

            if (hasBlock) {
                result = findExactTopBlock(x, z, mid, config.maxY)
                break
            } else {
                high = mid - 1
            }
        }

        return result
    }

    private fun hasNonAirBlock(x: Int, z: Int, fromY: Int, toY: Int): Boolean {
        for (y in fromY..toY) {
            val blockType = chunkCache.getBlockMaterial(x, y, z)
            if (canScorchBlock(blockType)) {
                return true
            }
        }
        return false
    }

    private fun findExactTopBlock(x: Int, z: Int, fromY: Int, toY: Int): Int? {
        for (y in toY downTo fromY) {
            val blockType = chunkCache.getBlockMaterial(x, y, z)
            if (canScorchBlock(blockType)) {
                return y
            }
        }
        return null
    }

    private fun calculateNormalizedDistance(x: Int, z: Int): Double {
        val xNorm = (x - config.centerX) * config.invRadiusX
        val zNorm = (z - config.centerZ) * config.invRadiusZ
        return sqrt(xNorm * xNorm + zNorm * zNorm)
    }

    private fun selectScorchMaterial(normalizedDistance: Double, x: Int, z: Int): Material {
        val clampedDistance = normalizedDistance.coerceIn(0.0, 1.0)

        // Add noise variation
        val noise = wangNoise(x, 0, z)
        val distortion = (noise - 0.5) * 0.25
        val finalDistance = (clampedDistance + distortion).coerceIn(0.0, 1.0)

        // Select material based on intensity
        val index = ((1.0 - finalDistance) * (scorchMaterials.size - 1))
            .roundToInt()
            .coerceIn(0, scorchMaterials.lastIndex)

        return scorchMaterials[index]
    }

    private fun canScorchBlock(material: Material): Boolean {
        return !material.isAir &&
               material !in TransformationRule.LIQUID_MATERIALS &&
               material !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST
    }

    private fun canRemoveBlock(material: Material): Boolean {
        return canScorchBlock(material)
    }

    // Data classes for better organization
    private data class ChunkRegion(val startX: Int, val startZ: Int, val endX: Int, val endZ: Int)

    private class CraterConfig(
        center: Location,
        val radiusX: Int,
        val radiusY: Int,
        val radiusZ: Int,
        destructionHeight: Int
    ) {
        val world = center.world!!
        val centerX = center.blockX
        val centerY = minOf(center.blockY, world.seaLevel)
        val centerZ = center.blockZ

        val minX = centerX - radiusX - 2
        val maxX = centerX + radiusX + 2
        val minZ = centerZ - radiusZ - 2
        val maxZ = centerZ + radiusZ + 2
        val minY = maxOf(centerY - radiusY, world.minHeight)
        val maxY = minOf(centerY + destructionHeight, world.maxHeight - 1)

        val invRadiusX = 1.0 / radiusX
        val invRadiusZ = 1.0 / radiusZ
        val invRadiusXSquared = 1.0 / (radiusX * radiusX)
        val invRadiusZSquared = 1.0 / (radiusZ * radiusZ)
    }

    private class CraterShape(private val config: CraterConfig) {
        private val width = config.maxX - config.minX + 1
        private val height = config.maxZ - config.minZ + 1
        private val craterData = IntArray(width * height) // 0 = not in crater, >0 = floor Y

        fun markPosition(x: Int, z: Int, floorY: Int) {
            val index = getIndex(x, z)
            if (index >= 0) {
                craterData[index] = maxOf(floorY, config.minY)
            }
        }

        fun isInCrater(x: Int, z: Int): Boolean {
            val index = getIndex(x, z)
            return index >= 0 && craterData[index] > 0
        }

        private fun getIndex(x: Int, z: Int): Int {
            val localX = x - config.minX
            val localZ = z - config.minZ

            return if (localX in 0 until width && localZ in 0 until height) {
                localX * height + localZ
            } else {
                -1
            }
        }
    }
}