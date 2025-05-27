package me.mochibit.defcon.explosions.processor

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.observer.Completable
import me.mochibit.defcon.observer.CompletionDispatcher
import me.mochibit.defcon.utils.*
import org.bukkit.Location
import org.bukkit.Material
import org.joml.SimplexNoise
import org.joml.Vector3i
import org.joml.Vector3ic
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.math.roundToInt

class Shockwave(
    private val center: Location,
    private val radiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Int,
    private val minDestructionPower: Double = 2.0,
    private val maxDestructionPower: Double = 5.0,
    private val transformationRule: TransformationRule = TransformationRule(maxDestructionPower),
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
    private val treeBurner = TreeBurner(world, center.toVector3i(), maxDestructionPower)
    private val chunkCache = ChunkCache.getInstance(world)
    private val rayCaster = RayCaster(world)
    private val blockChanger = BlockChanger.getInstance(world)

    private val processedBlocks = ConcurrentHashMap.newKeySet<Long>()

    private fun isBlockProcessed(x: Int, y: Int, z: Int): Boolean {
        return !processedBlocks.add(Geometry.packIntegerCoordinates(x, y, z))
    }


    // Processed rings counter for progress tracking
    private val processedRings = AtomicInteger(0)


    fun explode(): Job {
        val blockProcessingDispatcher = Dispatchers.Default
        val ringCounter = AtomicInteger(0)

        return Defcon.instance.launch(Dispatchers.Default) {
            try {
                val workChannel = Channel<Pair<Vector3i, Double>>(CHANNEL_CAPACITY)

                // Block processors
                val processors = List(4) {
                    launch(blockProcessingDispatcher) {
                        for ((location, power) in workChannel) {
                            if (treeBurner.isTreeBlock(location)) {
                                processTrees(location, power)
                            } else {
                                processBlock(location, power)
                            }
                        }
                    }
                }

                // Process shockwave by radius
                for (radius in radiusStart..shockwaveRadius) {
                    val radiusProgress = radius.toDouble() / shockwaveRadius.toDouble()
                    val explosionPower = MathFunctions.lerp(
                        maxDestructionPower,
                        minDestructionPower,
                        radiusProgress
                    )

                    // Generate columns for this radius and process visual effects and blocks
                    generateShockwaveCircleAsFlow(radius)
                        .buffer(FLOW_BUFFER_SIZE)
                        .collect { loc ->
                            workChannel.send(loc to explosionPower)
                        }

                    ringCounter.incrementAndGet()
                }

                workChannel.close()
                processors.forEach { it.join() }

            } finally {
                processedRings.set(ringCounter.get())
                cleanup()
            }
        }
    }

    private suspend fun processTrees(location: Vector3i, explosionPower: Double) {
        treeBurner.processTreeBurn(location, explosionPower)
        treeBurner.processTreeBurn(treeBurner.getTreeTerrain(location), explosionPower)
    }


    // Fast wall detection using cached materials
    private fun detectWall(x: Int, y: Int, z: Int): Boolean {
        return BASE_DIRECTIONS.any { dir ->
            chunkCache.getBlockMaterial(x + dir.x, y, z + dir.z) == Material.AIR
        }
    }

    // Check if a block has attached blocks (signs, torches, etc)
    private fun detectAttached(x: Int, y: Int, z: Int): Boolean {
        return BASE_DIRECTIONS.any { dir ->
            chunkCache.getBlockMaterial(x + dir.x, y + dir.y, z + dir.z) in TransformationRule.ATTACHED_BLOCKS
        }
    }

    private suspend fun processBlock(
        blockLocation: Vector3i,
        normalizedExplosionPower: Double
    ) {
        // Skip if power is too low - lowering threshold to allow more distant effects
        if (normalizedExplosionPower < 0.02) return

        // Enhanced penetration with exponential scaling for more realistic results
        val powerFactor = normalizedExplosionPower.pow(1.2)
        val basePenetration = (powerFactor * 12).roundToInt().coerceIn(1, 15)


        // Use simplex noise for more natural terrain-like variation
        val noiseX = blockLocation.x * 0.1
        val noiseY = blockLocation.y * 0.1
        val noiseZ = blockLocation.z * 0.1

        // Generate noise values in range [-1, 1]
        val noise = SimplexNoise.noise(noiseX.toFloat(), noiseY.toFloat(), noiseZ.toFloat())

        // Use noise for variation (transforms noise from [-1,1] to [0,1] range)
        val noiseFactor = (noise + 1) * 0.5

        // Randomize penetration with noise
        val varianceFactor = (normalizedExplosionPower * 0.6).coerceIn(0.2, 0.5)
        val distanceNormalized = 1.0 - normalizedExplosionPower.coerceIn(0.0, 1.0)

        // Use noise instead of random for more coherent patterns
        val randomOffset = (noiseFactor * 2 - 1) * basePenetration * varianceFactor * (1 - distanceNormalized * 0.5)

        // Calculate max penetration - ensure at least 1 block penetration even at low power
        val maxPenetration = (basePenetration + randomOffset).roundToInt()
            .coerceIn(1, (basePenetration * 1.4).toInt())

        // Optimized wall detection
        val isWall = detectWall(blockLocation.x, blockLocation.y, blockLocation.z) ||
                detectWall(blockLocation.x, blockLocation.y - 1, blockLocation.z)

        // Process differently based on structure type
        if (isWall) {
            processWall(blockLocation, normalizedExplosionPower)
        } else {
            processRoof(blockLocation, normalizedExplosionPower, maxPenetration)
        }
    }

    // Process vertical walls more efficiently with simplex noise
    private suspend fun processWall(
        blockLocation: Vector3i, normalizedExplosionPower: Double
    ) {
        // Return if already processed
        if (isBlockProcessed(blockLocation.x, blockLocation.y, blockLocation.z)) return

        val x = blockLocation.x
        val z = blockLocation.z
        val startY = blockLocation.y

        // Enhanced depth calculation
        val maxDepth = (shockwaveHeight * (0.7 + normalizedExplosionPower * 0.6)).toInt()

        for (depth in 0 until maxDepth) {
            val currentY = startY - depth

            // Stop if no longer a wall structure
            if (depth > 0 && !detectWall(x, currentY, z)) break

            val blockType = chunkCache.getBlockMaterial(x, currentY, z)

            // Skip blacklisted materials
            if (blockType in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST || blockType == Material.AIR) continue

            // Stop at liquids
            if (blockType in TransformationRule.LIQUID_MATERIALS) break

            // Use simplex noise for material determination
            val noiseValue = (SimplexNoise.noise(x * 0.3f, currentY * 0.3f, z * 0.3f) + 1) * 0.5

            // Determine final material
            val finalMaterial = if (depth > 0 && noiseValue < normalizedExplosionPower) {
                transformationRule.transformMaterial(blockType, normalizedExplosionPower)
            } else {
                Material.AIR
            }

            // Check if we need to copy block data
            val shouldCopyData = finalMaterial in TransformationRule.SLABS || finalMaterial in TransformationRule.WALLS || finalMaterial in TransformationRule.STAIRS

            // Add to batch
            blockChanger.addBlockChange(
                x, currentY, z,
                finalMaterial,
                shouldCopyData,
                (finalMaterial == Material.AIR && currentY == startY) || detectAttached(x, currentY, z)
            )
        }
    }

    // Process roof/floor structures with optimized algorithm and simplex noise
    private suspend fun processRoof(
        blockLocation: Vector3i, normalizedExplosionPower: Double, maxPenetration: Int
    ) {
        var currentY = blockLocation.y
        var currentPower = normalizedExplosionPower
        var penetrationCount = 0

        // Enhanced power decay based on normalized power
        val powerDecay = 0.85 - (0.15 * (1 - normalizedExplosionPower.pow(1.5)))

        val x = blockLocation.x
        val z = blockLocation.z

        // Surface effect - always apply some surface damage even at low power
        if (normalizedExplosionPower >= 0.02 && normalizedExplosionPower < 0.05) {
            // For very low power explosions, still create surface effects
            val surfaceNoise = (SimplexNoise.noise(x * 0.5f, currentY * 0.5f, z * 0.5f) + 1) * 0.5
            if (surfaceNoise < normalizedExplosionPower * 10) {  // Scale up chance for low power
                val blockType = chunkCache.getBlockMaterial(x, currentY, z)
                if (blockType != Material.AIR && blockType !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST && blockType !in TransformationRule.LIQUID_MATERIALS) {
                    // Apply surface transformation
                    val surfaceMaterial = if (surfaceNoise < normalizedExplosionPower * 5) {
                        Material.AIR
                    } else {
                        transformationRule.transformMaterial(blockType, normalizedExplosionPower * 0.5)
                    }

                    val shouldCopyData =
                        surfaceMaterial in TransformationRule.SLABS || surfaceMaterial in TransformationRule.WALLS || surfaceMaterial in TransformationRule.STAIRS
                    blockChanger.addBlockChange(x, currentY, z, surfaceMaterial, shouldCopyData, true)
                }
            }
        }

        // Main penetration loop
        while (penetrationCount < maxPenetration && currentPower > 0.05 && currentY > 0) {
            // Calculate current position with offset
            val currentX = x
            val currentZ = z

            // Skip if already processed
            if (!isBlockProcessed(currentX, currentY, currentZ)) {
                val blockType = chunkCache.getBlockMaterial(currentX, currentY, currentZ)

                // Handle air blocks
                if (blockType == Material.AIR) {


                    val maxSearchDepth = (10.0 + normalizedExplosionPower * 15.0).toInt()
                    val nextSolidY =
                        rayCaster.cachedRayTrace(currentX, currentY, currentZ, maxSearchDepth.toDouble())

                    if (nextSolidY < currentY - maxSearchDepth) break // Too far down

                    // Jump to next solid block
                    currentY = nextSolidY
                    currentPower *= 0.7
                }

                // Process the current block
                processRoofBlock(
                    currentX, currentY, currentZ, blockType,
                    currentPower, penetrationCount, maxPenetration
                )

                // Move down and update state
                currentY--

                // Apply power decay with slight randomization based on noise
                val noiseDecay = (SimplexNoise.noise(currentX * 0.2f, currentY * 0.2f, currentZ * 0.2f) + 1) * 0.05
                val powerDecayFactor = powerDecay + noiseDecay + (normalizedExplosionPower * 0.08)

                currentPower *= powerDecayFactor.coerceIn(0.7, 0.95)
                penetrationCount++
            } else {
                currentY--
                penetrationCount++
                continue
            }
        }
    }

    // Process individual roof blocks efficiently with simplex noise
    private suspend fun processRoofBlock(
        x: Int, y: Int, z: Int,
        blockType: Material,
        power: Double,
        penetrationCount: Int,
        maxPenetration: Int,
    ) {
        // Use simplex noise for power adjustment
        val noiseValue = (SimplexNoise.noise(x * 0.4f, y * 0.4f, z * 0.4f) + 1) * 0.5
        val adjustedPower = (power + (noiseValue * 0.2 - 0.1) * power).coerceIn(0.0, 1.0)
        val penetrationRatio = penetrationCount.toDouble() / maxPenetration

        // Fast material selection using efficient branching with noise influence
        val finalMaterial = when {
            // Surface layers - mostly air but allow some blocks to remain with very low power
            penetrationRatio < 0.3 -> {
                if (noiseValue < 0.1 && adjustedPower < 0.3) {
                    transformationRule.transformMaterial(blockType, adjustedPower * 0.5)
                } else {
                    Material.AIR
                }
            }

            // High power explosions create more cavities deeper
            adjustedPower > 0.7 && noiseValue < adjustedPower * 0.9 -> Material.AIR

            // Mid-depth with medium-high power - mix of air and debris
            penetrationRatio < 0.6 && adjustedPower > 0.5 -> {
                if (noiseValue < adjustedPower * 0.8) Material.AIR
                else transformationRule.transformMaterial(blockType, adjustedPower * 0.8)
            }

            // Deeper layers - scattered blocks/rubble pattern
            penetrationRatio >= 0.6 -> {
                if (noiseValue < 0.7 - (adjustedPower * 0.3))
                    transformationRule.transformMaterial(blockType, adjustedPower)
                else Material.AIR
            }

            // Noise-based destruction pockets
            noiseValue < adjustedPower * 0.8 -> Material.AIR

            // Some blocks remain slightly transformed
            else -> transformationRule.transformMaterial(blockType, adjustedPower * 0.6)
        }

        // Optimize physics update flags
        val updatePhysics = penetrationCount == 0 ||
                (finalMaterial != Material.AIR && noiseValue < 0.2)

        val shouldCopyData = finalMaterial in TransformationRule.SLABS ||
                finalMaterial in TransformationRule.STAIRS ||
                finalMaterial in TransformationRule.WALLS

        // Add to block change queue
        if (!isBlockProcessed(x, y + 1, z)) {
            val topBlock = chunkCache.getBlockMaterial(x, y + 1, z)
            if ((topBlock != Material.AIR && (topBlock in TransformationRule.PLANTS || topBlock in TransformationRule.LIGHT_WEIGHT_BLOCKS)) && topBlock !in TransformationRule.LIQUID_MATERIALS) {
                if (blockType.isFlammable) {
                    blockChanger.addBlockChange(x, y + 1, z, Material.FIRE, updateBlock = true)
                } else {
                    val topBlockMat = transformationRule.transformMaterial(topBlock, adjustedPower)
                    blockChanger.addBlockChange(x, y + 1, z, topBlockMat, shouldCopyData, updateBlock = false)
                }

            }
        }

        blockChanger.addBlockChange(x, y, z, finalMaterial, shouldCopyData, updatePhysics)
    }

    private fun generateShockwaveCircleAsFlow(radius: Int): Flow<Vector3i> = flow {
        // Get center coordinates
        val centerX = center.blockX
        val centerZ = center.blockZ

        // Special case for radius 0
        if (radius == 0) {
            val highestY = chunkCache.highestBlockYAt(centerX, centerZ)
            emit(Vector3i(centerX, highestY, centerZ))
            return@flow
        }

        // For a Minecraft circle perimeter, we need to check if points are near the edge
        // Use a tolerance value to determine how thick the perimeter should be
        val tolerance = 0.5 // Can be adjusted to change the thickness of the perimeter
        val outerRadiusSquared = (radius + 0.5) * (radius + 0.5)
        val innerRadiusSquared = (radius - tolerance) * (radius - tolerance)

        // Iterate through all blocks in the bounding square
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                // Calculate distance squared from center
                val distanceSquared = x * x + z * z

                // If the block is close to the circle perimeter
                if (distanceSquared <= outerRadiusSquared && distanceSquared >= innerRadiusSquared) {
                    try {
                        // Apply center offset to coordinates
                        val worldX = centerX + x
                        val worldZ = centerZ + z
                        val highestY = chunkCache.highestBlockYAt(worldX, worldZ)
                        emit(Vector3i(worldX, highestY, worldZ))
                    } catch (e: Exception) {
                        // Log the error but continue processing other points
                        Defcon.instance.logger.warning("Error processing point ($x, $z): ${e.message}")
                    }
                }
            }
        }
    }

    // Clean up resources
    private fun cleanup() {
        // Clean up services
        chunkCache.cleanupCache()
        processedBlocks.clear()
        complete()
    }
}