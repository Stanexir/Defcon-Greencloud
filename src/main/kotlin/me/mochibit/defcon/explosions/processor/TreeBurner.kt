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
import org.bukkit.Material
import org.bukkit.World
import org.joml.Vector3f
import org.joml.Vector3i
import java.util.*
import kotlin.math.roundToInt

class TreeBurner(
    private val world: World,
    private val center: Vector3i,
    private val maxExplosionPower: Double,
) {
    companion object {
        private const val LEAF_SUFFIX = "_LEAVES"
        private const val LOG_SUFFIX = "_LOG"
        private const val WOOD_SUFFIX = "_WOOD"

        // Optimized properties for the tree falling feature
        private const val MIN_POWER_FOR_AUTOMATIC_DESTRUCTION = 0.4

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

    suspend fun processTreeBurn(initialBlock: Vector3i, explosionPower: Double) {
        try {
            val normalizedExplosionPower = explosionPower / maxExplosionPower

            // Early exit if block is not part of a tree
            if (!isTreeBlock(initialBlock)) {
                return
            }

            // Find the base of the tree by going down from the initial block
            val treeMaxHeight = initialBlock.y
            val treeMinHeight = findTreeBase(initialBlock)

            // Enforce maximum tree height limit
            val effectiveMaxHeight = minOf(treeMinHeight + MAX_TREE_HEIGHT, treeMaxHeight)
            val heightRange = (effectiveMaxHeight - treeMinHeight).coerceAtLeast(1)

            // Calculate shockwave direction once
            val shockwaveDirection = Vector3f(
                (initialBlock.x - center.x).toFloat(),
                (initialBlock.y - center.y).toFloat(),
                (initialBlock.z - center.z).toFloat()
            ).normalize()

            // Process the vertical column from top to bottom, limited by MAX_TREE_HEIGHT
            for (y in effectiveMaxHeight downTo treeMinHeight) {
                val currentX = initialBlock.x
                val currentZ = initialBlock.z
                val material = chunkCache.getBlockMaterial(currentX, y, currentZ)

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
                            normalizedExplosionPower
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

    private fun findTreeBase(startBlock: Vector3i): Int {
        var currentY = startBlock.y
        val minY = maxOf(0, currentY - MAX_TREE_HEIGHT)
        val currentX = startBlock.x
        val currentZ = startBlock.z

        // Go down until we hit terrain or non-tree block, with a limit
        while (currentY > minY) {
            val material = chunkCache.getBlockMaterial(currentX, currentY, currentZ)

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

    fun getTreeTerrain(startLoc: Vector3i): Vector3i {
        return Vector3i(
            startLoc.x,
            findTreeBase(startLoc) - 1,
            startLoc.z
        )
    }

    private fun isTreeBlock(x: Int, y: Int, z: Int): Boolean {
        val material = chunkCache.getBlockMaterial(x, y, z)
        return material in TREE_BLOCKS
    }

    fun isTreeBlock(block: Vector3i): Boolean {
        return isTreeBlock(block.x, block.y, block.z)
    }

    private suspend fun processWoodBlock(
        x: Int, y: Int, z: Int,
        originalMaterial: Material,
        treeMinHeight: Int,
        heightRange: Int,
        shockwaveDirection: Vector3f,
        normalizedExplosionPower: Double
    ) {
        // Completely destroy wood blocks if explosion power is strong enough
        if (normalizedExplosionPower > MIN_POWER_FOR_AUTOMATIC_DESTRUCTION) {
            blockChanger.addBlockChange(x, y, z, Material.AIR, updateBlock = true)
            return
        }

        // Calculate tilt based on height
        val blockHeight = y - treeMinHeight
        val heightFactor = blockHeight.toDouble() / heightRange
        val tiltFactor = if (y == treeMinHeight) {
            0.0 // Base of tree doesn't move
        } else {
            heightFactor * normalizedExplosionPower * 6 // Smooth gradient tilt
        }

        val newX = (x + shockwaveDirection.x * tiltFactor).roundToInt()
        val newZ = (z + shockwaveDirection.z * tiltFactor).roundToInt()

        // Get appropriate burnt wood replacement
        val burntMaterial = getBurntWoodReplacement(originalMaterial)

        // Change the block and mark it as processed
        blockChanger.addBlockChange(
            newX,
            y,
            newZ,
            burntMaterial,
            updateBlock = true
        )

        // Remove the original block if it moved
        if (newX != x || newZ != z) {
            blockChanger.addBlockChange(x, y, z, Material.AIR, updateBlock = true)
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