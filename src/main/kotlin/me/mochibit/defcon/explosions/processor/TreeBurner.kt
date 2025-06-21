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

package me.mochibit.defcon.explosions.processor

import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.collection.Vector3iSetFull
import org.bukkit.Material
import org.bukkit.World
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3i
import java.util.*
import kotlin.math.floor
import kotlin.math.roundToInt

class TreeBurner(
    private val world: World,
    private val center: Vector3i,
    val distanceRatioCompletelyDestroy: Double = 0.1,
) {
    companion object {
        private const val LEAF_SUFFIX = "_LEAVES"
        private const val LOG_SUFFIX = "_LOG"
        private const val WOOD_SUFFIX = "_WOOD"

        // Maximum tree height to process
        private const val MAX_TREE_HEIGHT = 60

        private val TREE_BLOCKS = EnumSet.noneOf(Material::class.java).apply {
            for (material in Material.entries) {
                with(material.name) {
                    when {
                        endsWith(LEAF_SUFFIX) || endsWith(LOG_SUFFIX) || endsWith(WOOD_SUFFIX) -> add(material)
                        else -> Unit
                    }
                }
            }
        }

        // Separate collections for better type handling
        private val LEAF_BLOCKS = TREE_BLOCKS.filter { it.name.endsWith(LEAF_SUFFIX) }.toSet()
        private val LOG_BLOCKS = TREE_BLOCKS.filter { it.name.endsWith(LOG_SUFFIX) }.toSet()
        private val WOOD_BLOCKS = TREE_BLOCKS.filter { it.name.endsWith(WOOD_SUFFIX) }.toSet()
    }

    private val chunkCache = ChunkCache.getInstance(world)
    private val blockChanger = BlockChanger.getInstance(world)

    private val processedTreeBlocks = Vector3iSetFull()

    fun isPosProcessed(x: Int, y: Int, z: Int): Boolean {
        return processedTreeBlocks.contains(x, y, z).also {
            processedTreeBlocks.remove(x, y, z)
        }
    }

    suspend fun processTreeBurn(initialBlock: Vector3i, explosionPower: Double) {
        try {
            // Early exit if block is not part of a tree
            if (!isTreeBlock(initialBlock)) {
                return
            }

            if (processedTreeBlocks.contains(initialBlock.x, initialBlock.y, initialBlock.z)) {
                return
            }

            // Find the base of the tree by going down from the initial block
            val treeMaxHeight = initialBlock.y
            val treeMinHeight = findTreeBase(initialBlock)

            // Enforce maximum tree height limit
            val effectiveMaxHeight = minOf(treeMinHeight + MAX_TREE_HEIGHT, treeMaxHeight)
            val heightRange = (effectiveMaxHeight - treeMinHeight).coerceAtLeast(1)

            // Calculate shockwave direction once
            val shockwaveDirection = Vector2f(
                (initialBlock.x - center.x).toFloat(),
                (initialBlock.z - center.z).toFloat()
            ).normalize()

            // Process the vertical column from top to bottom, limited by MAX_TREE_HEIGHT
            for (y in effectiveMaxHeight downTo treeMinHeight) {
                val currentX = initialBlock.x
                val currentZ = initialBlock.z
                val material = chunkCache.getBlockMaterialAsync(currentX, y, currentZ)

                // Skip if not a tree block
                if (material !in TREE_BLOCKS) {
                    continue
                }

                when (material) {
                    in LEAF_BLOCKS -> {
                        // Process leaves - always remove them
                        blockChanger.addBlockChange(currentX, y, currentZ, Material.AIR, updateBlock = true)
                    }

                    in LOG_BLOCKS, in WOOD_BLOCKS -> {
                        // Process both log and wood blocks with tilt based on height
                        processWoodBlock(
                            currentX, y, currentZ,
                            material,
                            treeMinHeight,
                            heightRange,
                            shockwaveDirection,
                            explosionPower
                        )
                    }

                    else -> {
                        continue
                    }
                }
            }

        } catch (e: Exception) {
            // Log the error but prevent it from crashing the server
            println("Error in TreeBurner: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun findTreeBase(startBlock: Vector3i): Int {
        var currentY = startBlock.y
        val minY = maxOf(0, currentY - MAX_TREE_HEIGHT)
        val currentX = startBlock.x
        val currentZ = startBlock.z

        // Go down until we hit terrain or non-tree block, with a limit
        while (currentY > minY) {
            val material = chunkCache.getBlockMaterialAsync(currentX, currentY, currentZ)

            if (material == Material.AIR) {
                currentY--
                continue
            }

            if (material !in TREE_BLOCKS) {
                return currentY + 1
            }

            currentY--
        }

        // Fallback to the minimum height we're willing to check
        return minY
    }

    suspend fun getTreeTerrain(startLoc: Vector3i): Vector3i {
        return Vector3i(
            startLoc.x,
            findTreeBase(startLoc) - 1,
            startLoc.z
        )
    }

    private suspend fun isTreeBlock(x: Int, y: Int, z: Int): Boolean {
        val material = chunkCache.getBlockMaterialAsync(x, y, z)
        return material in TREE_BLOCKS
    }

    suspend fun isTreeBlock(block: Vector3i): Boolean {
        return isTreeBlock(block.x, block.y, block.z)
    }

    fun isTreeBlock(material: Material): Boolean {
        return material in TREE_BLOCKS
    }

    /**
     * Processes a wood block during tree destruction, handling both standalone and shockwave contexts.
     *
     * In shockwave context, uses a two-pass system:
     * 1. First pass: Tilt blocks but keep original material (so shockwave expansion recognizes them)
     * 2. Second pass: Transform tilted blocks to burnt material
     *
     * @param x, y, z Block coordinates
     * @param originalMaterial The original wood material
     * @param treeMinHeight Base height of the tree
     * @param heightRange Total height range of the tree
     * @param shockwaveDirection Direction vector for tilting
     * @param burnerDistanceRatio Distance ratio from explosion center (0.0 = center, 1.0 = edge)
     */
    private suspend fun processWoodBlock(
        x: Int, y: Int, z: Int,
        originalMaterial: Material,
        treeMinHeight: Int,
        heightRange: Int,
        shockwaveDirection: Vector2f,
        burnerDistanceRatio: Double
    ) {
        // Complete destruction for blocks very close to explosion
        if (burnerDistanceRatio <= distanceRatioCompletelyDestroy) {
            blockChanger.addBlockChange(x, y, z, Material.AIR, updateBlock = true)
            return
        }

        // Calculate tilt intensity based on height and distance from explosion
        val tiltFactor = calculateTiltFactor(y, treeMinHeight, heightRange, burnerDistanceRatio)
        val shouldTilt = tiltFactor > 0.0

        val burntMaterial = getBurntWoodReplacement(originalMaterial)

        if (shouldTilt) {
            val newX = (x + floor(shockwaveDirection.x * tiltFactor).toInt())
            val newZ = (z + floor(shockwaveDirection.y * tiltFactor).toInt())

            tiltBlock(x, y, z, newX, newZ, burntMaterial)
        } else {
            // If no tilt, just change to burnt material directly
            blockChanger.addBlockChange(x, y, z, burntMaterial, updateBlock = true)
            processedTreeBlocks.add(x, y, z)
        }

    }

    /**
     * Calculates how much a block should tilt based on its height and distance from explosion
     */
    private fun calculateTiltFactor(
        blockY: Int,
        treeMinHeight: Int,
        heightRange: Int,
        burnerDistanceRatio: Double
    ): Double {
        // Base of tree doesn't tilt
        if (blockY == treeMinHeight) return 0.0

        val blockHeight = blockY - treeMinHeight
        val heightFactor = blockHeight.toDouble() / heightRange

        // Higher blocks tilt more, closer to explosion tilts more
        return heightFactor * (1 - burnerDistanceRatio) * 6
    }


    /**
     * Moves a block from original position to new tilted position
     */
    private suspend fun tiltBlock(
        originalX: Int, originalY: Int, originalZ: Int,
        newX: Int, newZ: Int,
        material: Material
    ) {
        // Only move if position actually changed
        if (newX != originalX || newZ != originalZ) {
            blockChanger.addBlockChange(originalX, originalY, originalZ, Material.AIR, updateBlock = true)
            blockChanger.addBlockChange(newX, originalY, newZ, material, updateBlock = true)
            processedTreeBlocks.add(newX, originalY, originalZ)
        } else {
            // No movement, just change material
            blockChanger.addBlockChange(originalX, originalY, originalZ, material, updateBlock = true)
            processedTreeBlocks.add(originalX, originalY, originalZ)
        }
    }

    private fun getBurntWoodReplacement(originalMaterial: Material): Material {
        // Different burnt appearance based on wood type
        return when {
            originalMaterial in LEAF_BLOCKS -> Material.AIR
            originalMaterial.name.contains("WARPED") || originalMaterial.name.contains("CRIMSON") -> Material.BLACKSTONE
            else -> Material.POLISHED_BASALT
        }
    }
}