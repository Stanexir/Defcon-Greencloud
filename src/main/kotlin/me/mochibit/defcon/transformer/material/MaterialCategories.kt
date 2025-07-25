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

package me.mochibit.defcon.transformer.material

import org.bukkit.Material
import java.util.EnumSet

object MaterialCategories {
    val INDESTRUCTIBLE_BLOCKS: Set<Material> = EnumSet.of(
        Material.BEDROCK,
        Material.BARRIER,
        Material.COMMAND_BLOCK,
        Material.COMMAND_BLOCK_MINECART,
        Material.END_PORTAL_FRAME,
        Material.END_PORTAL,
    )

    val LIQUID_MATERIALS: EnumSet<Material> = EnumSet.of(
        Material.WATER,
        Material.LAVA
    )

    val DEAD_PLANTS: EnumSet<Material> = EnumSet.of(
        Material.DEAD_BUSH,
        Material.WITHER_ROSE
    )

    val DESTROYED_BLOCK: EnumSet<Material> = EnumSet.of(
        Material.COBBLESTONE,
        Material.COBBLED_DEEPSLATE
    )

    val TERRAIN_BLOCKS: EnumSet<Material> = EnumSet.of(
        Material.GRASS_BLOCK,
        Material.DIRT,
        Material.COARSE_DIRT,
        Material.MYCELIUM,
        Material.SAND,
        Material.RED_SAND,
        Material.GRAVEL,
        Material.CLAY,
        Material.SOUL_SAND,
        Material.SOUL_SOIL,
        Material.MUD,
        Material.MUDDY_MANGROVE_ROOTS,
        Material.STONE,
        Material.TERRACOTTA
    )

    val LIGHT_WEIGHT_BLOCKS: EnumSet<Material> = EnumSet.of(
        Material.ICE,
        Material.PACKED_ICE,
        Material.BLUE_ICE,
        Material.FROSTED_ICE,
        Material.SNOW,
        Material.SNOW_BLOCK,
        Material.POWDER_SNOW
    )

    val PLANTS: EnumSet<Material> = EnumSet.noneOf(Material::class.java).apply {
        // Grass types
        addAll(
            listOf(
                Material.SHORT_GRASS,
                Material.TALL_GRASS,
                Material.FERN,
                Material.LARGE_FERN
            )
        )

        // Saplings
        for (material in Material.entries) {
            if (material.name.contains("SAPLING", ignoreCase = true)) {
                add(material)
            }
        }

        // Flowers
        addAll(
            listOf(
                Material.POPPY, Material.DANDELION, Material.BLUE_ORCHID,
                Material.ALLIUM, Material.AZURE_BLUET, Material.OXEYE_DAISY,
                Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.PINK_PETALS,
                Material.LILAC, Material.PEONY, Material.SUNFLOWER,
                Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP
            )
        )
    }

    // Helper functions for categories
    fun isSlab(material: Material): Boolean = material.name.endsWith("_SLAB")
    fun isWall(material: Material): Boolean = material.name.endsWith("_WALL")
    fun isStairs(material: Material): Boolean = material.name.endsWith("_STAIRS")
    fun isGlass(material: Material): Boolean = material.name.contains("GLASS", ignoreCase = true)
}