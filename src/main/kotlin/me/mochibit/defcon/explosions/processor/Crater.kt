package me.mochibit.defcon.explosions.processor

import kotlinx.coroutines.*
import me.mochibit.defcon.explosions.MaterialCategories
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.Geometry.wangNoise
import org.bukkit.HeightMap
import org.bukkit.Location
import org.bukkit.Material
import org.joml.Vector3i
import kotlin.math.*

/**
 * Optimized crater generation with improved performance and cleaner architecture
 */
class Crater(
    private val center: Location,
    private val radiusX: Int,
    private val radiusY: Int, // Depth for the paraboloid
    private val radiusZ: Int,
    private val destructionHeight: Int,
) {
    private val config = CraterConfig(center, radiusX, radiusY, radiusZ, destructionHeight)
    private val world = center.world!!
    private val chunkCache = ChunkCache.getInstance(world)
    private val blockChanger = BlockChanger.getInstance(world)

    // Scorch materials (ordered from least to most intense)
    private val scorchMaterials = arrayOf(
        Material.TUFF,
        Material.DEEPSLATE,
        Material.BASALT,
        Material.BLACKSTONE,
        Material.COAL_BLOCK,
        Material.BLACK_CONCRETE_POWDER,
        Material.BLACK_CONCRETE,
    )

    suspend fun create() {
        generateCrater()

    }

    private fun generateCraterEffectivePlane(): List<Vector3i> {
        val planePoints = mutableListOf<Vector3i>()

        val centerX = config.centerX
        val centerZ = config.centerZ

        for (x in config.minX..config.maxX) {
            for (z in config.minZ..config.maxZ) {
                // Fix ellipse boundary check using normalized coordinates
                val dx = x - centerX
                val dz = z - centerZ
                val normalizedDistance = (dx.toDouble() / radiusX).pow(2) + (dz.toDouble() / radiusZ).pow(2)

                if (normalizedDistance > 1.0) continue

                // Get terrain height at this position
                val terrainY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES)

                // Calculate ideal crater floor based on center height
                val rSquared = dx * dx + dz * dz
                val maxRadiusSquared = maxOf(radiusX * radiusX, radiusZ * radiusZ)
                val depthFactor = rSquared.toDouble() / maxRadiusSquared.toDouble()
                val idealCraterFloorY = config.centerY - (radiusY * (1.0 - depthFactor)).toInt()

                // Blend crater floor with terrain to avoid sharp edges
                val blendFactor = 0.7 // How much to follow crater shape vs terrain
                val adaptiveCraterFloorY = (idealCraterFloorY * blendFactor + terrainY * (1.0 - blendFactor)).toInt()

                // For areas near the edge, blend more with terrain
                val edgeBlendDistance = 0.8 // Start blending when closer to edge
                val adjustedBlendFactor = if (depthFactor > edgeBlendDistance) {
                    val edgeFactor = (depthFactor - edgeBlendDistance) / (1.0 - edgeBlendDistance)
                    blendFactor * (1.0 - edgeFactor * 0.6) // Reduce crater influence near edges
                } else {
                    blendFactor
                }

                val finalCraterFloorY = (idealCraterFloorY * adjustedBlendFactor + terrainY * (1.0 - adjustedBlendFactor)).toInt()

                // Ensure the crater floor doesn't go below world limits or above terrain
                val finalY = maxOf(minOf(finalCraterFloorY, terrainY), config.minY)

                planePoints.add(Vector3i(x, finalY, z))
            }
        }

        return planePoints
    }

    private suspend fun generateCrater() = coroutineScope {
        val points = generateCraterEffectivePlane()
        println("Generated ${points.size} crater points")

        for (point in points) {
            processPoint(point)
        }
    }

    private suspend fun processPoint(point: Vector3i) {
        applyFloorScorching(point)
        removeBlocksAboveFloor(point)
    }

    private suspend fun applyFloorScorching(point: Vector3i) {
        val blockType = chunkCache.getBlockMaterialAsync(point.x, point.y, point.z)
        if (!canScorchBlock(blockType)) return

        val xDist = point.x - config.centerX
        val zDist = point.z - config.centerZ
        val distSquared = xDist * xDist + zDist * zDist
        val maxRadiusSquared = maxOf(radiusX * radiusX, radiusZ * radiusZ)
        val normalizedDistance = sqrt(distSquared.toDouble() / maxRadiusSquared.toDouble())

        val material = selectScorchMaterial(normalizedDistance, point.x, point.z)
        blockChanger.addBlockChange(point.x, point.y, point.z, material, updateBlock = false)
    }

    private suspend fun removeBlocksAboveFloor(point: Vector3i) {
        val x = point.x
        val z = point.z

        // Get the current terrain height at this position
        val terrainY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES)
        val maxRemovalY = minOf(config.centerY + destructionHeight, config.maxY)

        // Remove blocks from crater floor up to destruction height
        for (y in (point.y + 1)..maxRemovalY) {
            val blockType = chunkCache.getBlockMaterialAsync(x, y, z)
            if (canRemoveBlock(blockType)) {
                blockChanger.addBlockChange(x, y, z, Material.AIR, updateBlock = false)
            }
        }
    }

    private fun selectScorchMaterial(normalizedDistance: Double, x: Int, z: Int): Material {
        val clampedDistance = normalizedDistance.coerceIn(0.0, 1.0)

        // Add noise variation
        val noise = wangNoise(x, 0, z)
        val distortion = (noise - 0.5) * 0.25
        val finalDistance = (clampedDistance + distortion).coerceIn(0.0, 1.0)

        // Select material based on intensity (closer to center = more intense)
        val index = ((1.0 - finalDistance) * (scorchMaterials.size - 1))
            .roundToInt()
            .coerceIn(0, scorchMaterials.lastIndex)

        return scorchMaterials[index]
    }

    private fun canScorchBlock(material: Material): Boolean {
        return !material.isAir &&
                material !in MaterialCategories.LIQUID_MATERIALS &&
                material !in MaterialCategories.INDESTRUCTIBLE_BLOCKS
    }

    private fun canRemoveBlock(material: Material): Boolean {
        return canScorchBlock(material)
    }

    private class CraterConfig(
        center: Location,
        val radiusX: Int,
        val radiusY: Int,
        val radiusZ: Int,
        destructionHeight: Int
    ) {
        val world = center.world!!
        val centerX = center.blockX
        val centerY = center.blockY // Use actual center Y instead of sea level
        val centerZ = center.blockZ

        val minX = centerX - radiusX - 2
        val maxX = centerX + radiusX + 2
        val minZ = centerZ - radiusZ - 2
        val maxZ = centerZ + radiusZ + 2
        val minY = maxOf(centerY - radiusY, world.minHeight)
        val maxY = minOf(centerY + destructionHeight, world.maxHeight - 1)
    }
}