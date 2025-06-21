package me.mochibit.defcon.explosions.processor

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.explosions.MaterialCategories
import me.mochibit.defcon.explosions.MaterialTransformer
import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.observer.Completable
import me.mochibit.defcon.observer.CompletionDispatcher
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.joml.Vector3i
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class Shockwave(
    private val center: Location,
    private val radiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Int,
    private val materialTransformer: MaterialTransformer = MaterialTransformer(),
) : Completable by CompletionDispatcher() {
    companion object {
        private val BASE_DIRECTIONS = arrayOf(
            Vector3i(1, 0, 0),  // East
            Vector3i(-1, 0, 0), // West
            Vector3i(0, 0, 1),  // South
            Vector3i(0, 0, -1)  // North
        )


        private const val FLOW_BUFFER_SIZE = 256

        // Channel capacity for work coordination
        private const val CHANNEL_CAPACITY = 512
    }

    private val world = center.world

    // Services
    private val treeBurner = TreeBurner(world, center.toVector3i())
    private val chunkCache = ChunkCache.getInstance(world)
    private val blockChanger = BlockChanger.getInstance(world)

    private val worldSeaLevel = world.seaLevel

    @OptIn(ExperimentalCoroutinesApi::class)
    fun explode(): Job {
        return Defcon.instance.launch(Dispatchers.IO) {
            try {
                for (currentRadius in radiusStart..shockwaveRadius) {
                    val radiusProgress = currentRadius / shockwaveRadius.toFloat()

                    // Process blocks in chunks to reduce memory pressure
                    generateShockwaveCirclePrecise(currentRadius)
                        .chunked(50000)
                        .flowOn(Dispatchers.Default)
                        .collect { locationBatch ->

                            locationBatch.forEach { loc ->
                                loc.y = chunkCache.highestBlockYAtAsync(loc.x, loc.z)
                                val firstMaterial = chunkCache.getBlockMaterialAsync(loc.x, loc.y, loc.z)
                                if (treeBurner.isTreeBlock(firstMaterial)) {
                                    processTrees(loc, radiusProgress, firstMaterial)
                                } else {
                                    processBlock(loc, radiusProgress, firstMaterial)
                                }
                            }
                        }
                }
            } finally {
                cleanup()
            }
        }
    }

    private suspend fun processTrees(location: Vector3i, radiusProgress: Float, first: Material) {
        treeBurner.processTreeBurn(location, radiusProgress.toDouble())
        processBlock(treeBurner.getTreeTerrain(location), radiusProgress, first)
    }

    private suspend fun processBlock(
        blockLocation: Vector3i,
        radiusProgress: Float,
        firstBlockType : Material,
    ) {
        val x = blockLocation.x
        val y = blockLocation.y
        val z = blockLocation.z

        // Pre-calculate values to avoid repeated calculations
        val randomOffset = (1..5).random()
        val convertToAirMinY = (worldSeaLevel + randomOffset) + (shockwaveHeight / 2) * radiusProgress
        val seaLevelMinus3 = worldSeaLevel - 3
        val seaLevelPlus5 = worldSeaLevel + 5

        // Noise parameters for terrain destruction - increased base chance
        val terrainNoiseStrength = 0.3f + (1.0f - radiusProgress) * 0.4f // Stronger noise closer to explosion
        val baseTerrainBreakChance = 0.7 + (1.0f - radiusProgress) * 0.25f // Higher base chance (was 0.5)

        // Skylight threshold - closer to explosion center requires less skylight to damage walls
        val skylightThreshold = (radiusProgress * 12).toInt().coerceIn(2, 15)

        // Use primitive counters to reduce object allocation
        var consecutiveTerrainBlocks = 0
        var consecutiveAirBlocks = 0
        var consecutiveFluids = 0
        var consecutiveBlacklisted = 0

        for (currentY in y downTo seaLevelMinus3) {
            if (treeBurner.isPosProcessed(x, currentY, z)) {
                continue
            }

            val currentBlock = if (currentY == y) {
                firstBlockType // Use the first block type for the initial position
            } else {
                chunkCache.getBlockMaterialAsync(x, currentY, z)
            }

            // Early exit conditions using when for better performance
            when (currentBlock) {
                in MaterialCategories.INDESTRUCTIBLE_BLOCKS -> {
                    consecutiveBlacklisted++
                    consecutiveTerrainBlocks = 0
                    consecutiveAirBlocks = 0
                    if (consecutiveBlacklisted >= 2) break
                    continue
                }

                in MaterialCategories.LIQUID_MATERIALS -> {
                    consecutiveFluids++
                    consecutiveTerrainBlocks = 0
                    consecutiveAirBlocks = 0
                    if (consecutiveFluids >= 2) break
                    continue
                }

                Material.AIR -> {
                    consecutiveAirBlocks++
                    consecutiveTerrainBlocks = 0
                    if (consecutiveAirBlocks >= 10) break
                    continue
                }

                else -> {
                    consecutiveBlacklisted = 0
                    consecutiveFluids = 0
                    consecutiveAirBlocks = 0
                }
            }

            val isTerrainBlock = currentBlock in MaterialCategories.TERRAIN_BLOCKS
            val shouldConvertToAir = currentY > convertToAirMinY

            // Get skylight level for this position (used for both walls and transformations)
            val skylightLevel = chunkCache.getSkyLightLevelAsync(x, currentY, z)

            // Handle wall blocks with skylight detection
            if (isHeuristicallyWallBlock(x, currentY, z)) {
                consecutiveTerrainBlocks = 0

                val shouldDamageWall = skylightLevel >= skylightThreshold

                if (currentY > seaLevelPlus5) {
                    // Always destroy walls above sea level + 5
                    blockChanger.addBlockChange(x, currentY, z, Material.AIR, updateBlock = true)
                } else if (shouldDamageWall) {
                    // Damage walls that are sufficiently exposed to skylight
                    if (Random.nextDouble() > 0.3) { // 70% chance to destroy exposed walls
                        blockChanger.addBlockChange(x, currentY, z, Material.AIR, updateBlock = true)
                    } else {
                        // Transform instead of destroy - enhanced by light exposure
                        val lightInfluence = (skylightLevel / 15.0f) * 0.3f // Light adds up to 30% more transformation
                        val transformationStrength = radiusProgress + lightInfluence
                        val transformedBlock =
                            materialTransformer.transformMaterial(currentBlock, transformationStrength)
                        blockChanger.addBlockChange(x, currentY, z, transformedBlock)
                    }
                } else {
                    // Less exposed walls - transform with light consideration
                    val lightInfluence = (skylightLevel / 15.0f) * 0.15f // Light adds up to 15% more transformation
                    val transformationStrength = radiusProgress + lightInfluence
                    val transformedBlock = materialTransformer.transformMaterial(currentBlock, transformationStrength)
                    blockChanger.addBlockChange(x, currentY, z, transformedBlock)
                }
                continue
            }

            if (isTerrainBlock) {
                consecutiveTerrainBlocks++
                if (consecutiveTerrainBlocks >= 3) break
                val heightFactor = (currentY - seaLevelMinus3).toFloat() / (y - seaLevelMinus3).coerceAtLeast(1)
                val noiseValue = generateTerrainNoise(x, currentY, z, terrainNoiseStrength)
                if (consecutiveTerrainBlocks == 1) {
                    val finalBreakChance =
                        baseTerrainBreakChance + noiseValue - (heightFactor * 0.15f) // Reduced height penalty

                    val shouldBreakTerrain = shouldConvertToAir ||
                            (Random.nextDouble() < finalBreakChance && currentY > seaLevelMinus3)

                    if (shouldBreakTerrain) {
                        blockChanger.addBlockChange(x, currentY, z, Material.AIR, updateBlock = true)
                        val adjacentNoise = generateTerrainNoise(x, currentY - 1, z, terrainNoiseStrength * 0.5f)
                        if (adjacentNoise > 0.15f) { // Lowered threshold from 0.2f
                            val belowMaterial = chunkCache.getBlockMaterialAsync(x, currentY - 1, z)
                            if (belowMaterial in MaterialCategories.TERRAIN_BLOCKS) {
                                blockChanger.addBlockChange(x, currentY - 1, z, Material.AIR, updateBlock = true)
                            }
                        }
                        continue
                    }
                }


                // Transform terrain block based on explosion power with noise variation and light influence
                val noiseInfluence = noiseValue * 0.3f
                val lightInfluence =
                    (skylightLevel / 15.0f) * 0.2f // Light adds up to 20% more transformation for terrain
                val transformationStrength = radiusProgress + noiseInfluence + lightInfluence
                val transformedBlock = materialTransformer.transformMaterial(currentBlock, transformationStrength)
                blockChanger.addBlockChange(x, currentY, z, transformedBlock)

                // Process block above if it exists - with noise and light consideration
                val aboveMaterial = chunkCache.getBlockMaterialAsync(x, currentY + 1, z)
                if (aboveMaterial != Material.AIR) {
                    val aboveSkylightLevel = chunkCache.getSkyLightLevelAsync(x, currentY + 1, z)
                    val aboveNoise = generateTerrainNoise(x, currentY + 1, z, terrainNoiseStrength * 0.7f)
                    val aboveLightInfluence = (aboveSkylightLevel / 15.0f) * 0.15f
                    val aboveTransformationStrength = radiusProgress + (aboveNoise * 0.2f) + aboveLightInfluence
                    val transformedAbove =
                        materialTransformer.transformMaterial(aboveMaterial, aboveTransformationStrength)
                    blockChanger.addBlockChange(x, currentY + 1, z, transformedAbove)
                }
            } else {
                consecutiveTerrainBlocks = 0

                if (shouldConvertToAir) {
                    blockChanger.addBlockChange(x, currentY, z, Material.AIR, updateBlock = true)
                } else {
                    // Apply noise and light influence to non-terrain blocks as well
                    val blockNoise = generateTerrainNoise(x, currentY, z, terrainNoiseStrength * 0.5f)
                    val lightInfluence =
                        (skylightLevel / 15.0f) * 0.1f // Light adds up to 10% more transformation for non-terrain
                    val transformationStrength = radiusProgress + (blockNoise * 0.2f) + lightInfluence
                    val transformedBlock = materialTransformer.transformMaterial(currentBlock, transformationStrength)
                    blockChanger.addBlockChange(x, currentY, z, transformedBlock, updateBlock = true)
                }
            }
        }
    }

    /**
     * Generates terrain noise for more natural destruction patterns
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param strength Noise strength multiplier
     * @return Noise value between -1.0 and 1.0
     */
    private fun generateTerrainNoise(x: Int, y: Int, z: Int, strength: Float): Float {
        // Simple pseudo-random noise based on coordinates
        val seed = (x * 374761393L + y * 668265263L + z * 1274126177L) and 0x7FFFFFFF
        val random = Random(seed.toInt())

        // Generate multiple octaves of noise for more natural patterns
        val noise1 = (random.nextDouble() - 0.5) * 2.0 // -1 to 1
        val noise2 = (random.nextDouble() - 0.5) * 1.0 // -0.5 to 0.5
        val noise3 = (random.nextDouble() - 0.5) * 0.5 // -0.25 to 0.25

        val combinedNoise = (noise1 + noise2 + noise3) / 1.75 // Normalize
        return (combinedNoise * strength).toFloat().coerceIn(-1.0f, 1.0f)
    }

    private suspend fun isHeuristicallyWallBlock(x: Int, y: Int, z: Int): Boolean {
        var airBlockCount = 0

        // Unrolled loop for better performance - check 4 cardinal directions
        if (chunkCache.getBlockMaterialAsync(x + 1, y, z) == Material.AIR) airBlockCount++

        if (chunkCache.getBlockMaterialAsync(x - 1, y, z) == Material.AIR) airBlockCount++
        if (airBlockCount >= 2) return true

        if (chunkCache.getBlockMaterialAsync(x, y, z + 1) == Material.AIR) airBlockCount++
        if (airBlockCount >= 2) return true

        if (chunkCache.getBlockMaterialAsync(x, y, z - 1) == Material.AIR) airBlockCount++

        return airBlockCount >= 2
    }

    private fun generateShockwaveCirclePrecise(radius: Int): Flow<Vector3i> = flow {
        val centerX = center.blockX
        val centerZ = center.blockZ

        // Special case for radius 0
        if (radius == 0) {
            emit(Vector3i(centerX, world.maxHeight, centerZ))
            return@flow
        }

        // Use Set to track emitted positions
        val emittedPositions = mutableSetOf<Pair<Int, Int>>()

        // Calculate radius bounds for precise circle generation
        val radiusSquared = radius * radius
        val innerRadiusSquared = (radius - 1) * (radius - 1)

        // Search in a square around the center, but only emit points that form the exact circle
        val searchRadius = radius + 1

        for (dx in -searchRadius..searchRadius) {
            for (dz in -searchRadius..searchRadius) {
                val distanceSquared = dx * dx + dz * dz

                // Check if this point is on the current radius circle
                // Point is on circle if: (radius-1)² < distance² <= radius²
                if (distanceSquared > innerRadiusSquared && distanceSquared <= radiusSquared) {
                    val worldX = centerX + dx
                    val worldZ = centerZ + dz

                    if (emittedPositions.add(worldX to worldZ)) {
                        try {
                            emit(Vector3i(worldX, world.maxHeight, worldZ))
                        } catch (e: Exception) {
                            Defcon.instance.logger.warning("Error processing point ($worldX, $worldZ): ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun cleanup() {
        chunkCache.cleanup()
        complete()
    }
}