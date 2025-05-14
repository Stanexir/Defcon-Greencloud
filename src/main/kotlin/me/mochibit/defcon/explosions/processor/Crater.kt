package me.mochibit.defcon.explosions.processor

import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.Geometry
import me.mochibit.defcon.utils.Geometry.wangNoise
import org.bukkit.Location
import org.bukkit.Material
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.*

class Crater(
    val center: Location,
    private val radiusX: Int,
    private val radiusY: Int, // This will be depth for the paraboloid
    private val radiusZ: Int,
    private val transformationRule: TransformationRule,
    private val destructionHeight: Int,
) {
    private val world = center.world
    private val chunkCache = ChunkCache.getInstance(world)
    private val centerX = center.blockX
    private val centerY = min(center.blockY, world.seaLevel)
    private val centerZ = center.blockZ
    private val maxRadius = maxOf(radiusX, radiusZ)

    // Pre-calculate bounding box for optimization
    private val minX = centerX - radiusX - 2
    private val maxX = centerX + radiusX + 2
    private val minZ = centerZ - radiusZ - 2
    private val maxZ = centerZ + radiusZ + 2
    private val minY = max(centerY - radiusY, world.minHeight)
    private val maxY = min(centerY + destructionHeight, world.maxHeight - 1)

    // Pre-calculated squared radiuses for faster distance checks
    private val radiusXSquared = radiusX * radiusX.toDouble()
    private val radiusZSquared = radiusZ * radiusZ.toDouble()
    private val invRadiusXSquared = 1.0 / radiusXSquared
    private val invRadiusZSquared = 1.0 / radiusZSquared
    private val invRadiusX = 1.0 / radiusX
    private val invRadiusZ = 1.0 / radiusZ

    // Ordered from least to most scorched
    private val scorchMaterials = listOf(
        Material.TUFF,
        Material.DEEPSLATE,
        Material.BASALT,
        Material.BLACKSTONE,
        Material.COAL_BLOCK,
        Material.BLACK_CONCRETE_POWDER,
        Material.BLACK_CONCRETE,
    )

    private val blockChanger = BlockChanger.getInstance(world)

    // Define chunk dimensions as constants to avoid repeated calculations
    private val CHUNK_SIZE = 16

    // Cache the directions for rim detection
    private val RIM_DIRECTIONS = arrayOf(
        1 to 0, -1 to 0, 0 to 1, 0 to -1,
        1 to 1, 1 to -1, -1 to 1, -1 to -1
    )

    suspend fun create(): Int {
        try {
            return createOptimizedCrater()
        } catch (e: Exception) {
            e.printStackTrace()
            return max(radiusX, radiusZ)
        } finally {
            chunkCache.cleanupCache()
        }
    }

    private suspend fun createOptimizedCrater(): Int = coroutineScope {
        // Use a direct bitset representation instead of HashMap for crater shape
        // This allows us to track which (x,z) coordinates are part of the crater
        val craterBounds = IntArray((maxX - minX + 1) * (maxZ - minZ + 1))

        // Generate crater shape and immediately process chunks
        val effectiveRadius = processCraterInParallel(craterBounds)

        effectiveRadius
    }

    private suspend fun processCraterInParallel(craterBounds: IntArray): Int = coroutineScope {
        // Divide the work by chunk regions for better parallelism
        val chunkRegions = mutableListOf<ChunkRegion>()

        for (chunkX in minX / CHUNK_SIZE..maxX / CHUNK_SIZE) {
            for (chunkZ in minZ / CHUNK_SIZE..maxZ / CHUNK_SIZE) {
                val startX = chunkX * CHUNK_SIZE
                val startZ = chunkZ * CHUNK_SIZE
                val endX = min(startX + CHUNK_SIZE - 1, maxX)
                val endZ = min(startZ + CHUNK_SIZE - 1, maxZ)

                chunkRegions.add(ChunkRegion(startX, startZ, endX, endZ))
            }
        }

        // Process all regions in parallel - combining shape generation and processing
        val regionJobs = chunkRegions.map { region ->
            async {
                processRegion(region, craterBounds)
            }
        }

        // Calculate maximum effective radius from all processed regions
        val results = regionJobs.awaitAll()
        val maxDistSq = results.maxOfOrNull { it } ?: 0.0

        // Apply rim scorching as a second pass once we know the crater shape
        applyRimScorching(craterBounds)

        sqrt(maxDistSq).roundToInt()
    }

    private data class ChunkRegion(val startX: Int, val startZ: Int, val endX: Int, val endZ: Int)

    private suspend fun processRegion(region: ChunkRegion, craterBounds: IntArray): Double {
        var maxDistSq = 0.0

        for (x in region.startX..region.endX) {
            val xDist = x - centerX
            val xComponent = xDist * xDist * invRadiusXSquared

            if (xComponent > 1.0) continue

            for (z in region.startZ..region.endZ) {
                val zDist = z - centerZ
                val zComponent = zDist * zDist * invRadiusZSquared

                val normalizedDistSquared = xComponent + zComponent

                // Only process points inside the elliptical boundary
                if (normalizedDistSquared <= 1.0) {
                    // Calculate depth at this point (paraboloid equation)
                    val depth = radiusY * (1.0 - normalizedDistSquared)
                    val craterFloorY = (centerY - depth).roundToInt()

                    // Mark this position as part of the crater for rim detection later
                    val indexX = x - minX
                    val indexZ = z - minZ
                    val index = indexX * (maxZ - minZ + 1) + indexZ
                    craterBounds[index] = craterFloorY

                    // Only process if floor is within valid range
                    if (craterFloorY >= minY) {
                        // Process crater floor scorching
                        val scorchDist = applyFloorScorching(x, z, craterFloorY, normalizedDistSquared)
                        maxDistSq = max(maxDistSq, scorchDist)

                        // Remove blocks from the floor up to the destruction height
                        for (y in craterFloorY + 1..min(centerY + destructionHeight, maxY)) {
                            val blockType = chunkCache.getBlockMaterial(x, y, z)
                            if (!blockType.isAir && blockType !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST) {
                                blockChanger.addBlockChange(x, y, z, Material.AIR, updateBlock = false)
                            }
                        }
                    }
                }
            }
        }

        return maxDistSq
    }

    private suspend fun applyFloorScorching(x: Int, z: Int, floorY: Int, normalizedDistSquared: Double): Double {
        if (floorY < minY) return 0.0

        val blockType = chunkCache.getBlockMaterial(x, floorY, z)

        // Found a valid block to scorch
        if (!blockType.isAir &&
            blockType !in TransformationRule.LIQUID_MATERIALS &&
            blockType !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST
        ) {
            val normalizedDist = sqrt(normalizedDistSquared)
            val scorchMaterial = selectScorchMaterial(normalizedDist, x, z)
            blockChanger.addBlockChange(x, floorY, z, scorchMaterial, updateBlock = false)

            return (x - centerX).toDouble().pow(2) + (z - centerZ).toDouble().pow(2)
        }

        return 0.0
    }

    private suspend fun applyRimScorching(craterBounds: IntArray) = coroutineScope {
        // Process rim positions in parallel - batch them by rows for better performance
        val rimJobs = (minX..maxX).map { x ->
            async {
                for (z in minZ..maxZ) {
                    val indexX = x - minX
                    val indexZ = z - minZ
                    val index = indexX * (maxZ - minZ + 1) + indexZ

                    // Skip if this position is inside the crater
                    if (index >= 0 && index < craterBounds.size && craterBounds[index] != 0) continue

                    // Check if this position is adjacent to the crater
                    if (isRimPosition(x, z, craterBounds)) {
                        processRimPosition(x, z)
                    }
                }
            }
        }

        rimJobs.awaitAll()
    }

    private fun isRimPosition(x: Int, z: Int, craterBounds: IntArray): Boolean {
        // Check surrounding positions to see if any are part of the crater
        for ((dx, dz) in RIM_DIRECTIONS) {
            val nx = x + dx
            val nz = z + dz

            // Skip if out of bounds
            if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue

            val indexX = nx - minX
            val indexZ = nz - minZ
            val index = indexX * (maxZ - minZ + 1) + indexZ

            if (index >= 0 && index < craterBounds.size && craterBounds[index] != 0) {
                return true
            }
        }

        return false
    }

    private suspend fun processRimPosition(x: Int, z: Int) {
        // Find top non-air block in this rim column
        var topY = -1

        // Binary search to find the top block - faster than sequential search
        var low = minY
        var high = maxY

        while (low <= high) {
            val mid = (low + high) / 2
            val isAir = isAirColumn(x, z, mid, maxY)

            if (isAir) {
                // Look lower
                high = mid - 1
            } else {
                // Found a potential top block
                topY = mid
                break
            }
        }

        // If binary search didn't find a block, perform a focused linear search
        if (topY == -1) {
            for (y in maxY downTo minY) {
                val blockType = chunkCache.getBlockMaterial(x, y, z)
                if (!blockType.isAir &&
                    blockType !in TransformationRule.LIQUID_MATERIALS &&
                    blockType !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST
                ) {
                    topY = y
                    break
                }
            }
        }

        // If found a block to scorch
        if (topY != -1) {
            // Calculate distance from center for rim intensity
            val xNorm = (x - centerX) * invRadiusX
            val zNorm = (z - centerZ) * invRadiusZ
            val distance = sqrt(xNorm * xNorm + zNorm * zNorm)

            // Apply gradually increasing scorch effect based on distance from rim
            val offsetDist = 1.4
            if (distance < offsetDist) {
                // Calculate scorch intensity
                val intensity = max(0.0, 1.0 - (distance / offsetDist))
                val material = selectScorchMaterial(1.0 - intensity, x, z)
                blockChanger.addBlockChange(x, topY, z, material, updateBlock = true)
            }
        }
    }

    // Helper method to check if a column from y to maxY is all air blocks
    private fun isAirColumn(x: Int, z:Int, fromY: Int, toY: Int): Boolean {
        for (y in fromY..toY) {
            val blockType = chunkCache.getBlockMaterial(x, y, z)
            if (!blockType.isAir) {
                return false
            }
        }
        return true
    }

    /**
     * Optimized scorch material selection with cached calculations
     */
    private fun selectScorchMaterial(normalizedDistance: Double, x: Int, z: Int): Material {
        val clampedDistance = normalizedDistance.coerceIn(0.0, 1.0)

        // Add some noise for variation
        val variation = 0.25
        val noise = wangNoise(x, 0, z)
        val distortion = (noise - 0.5) * variation
        val distortedDistance = (clampedDistance + distortion).coerceIn(0.0, 1.0)

        // Select material based on distance - closer to center means more intense scorching
        val materialIndex = ((1.0 - distortedDistance) * (scorchMaterials.size - 1)).roundToInt()
            .coerceIn(0, scorchMaterials.size - 1)

        return scorchMaterials[materialIndex]
    }
}