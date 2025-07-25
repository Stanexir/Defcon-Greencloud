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
import java.util.*
import kotlin.random.Random


class MaterialTransformer(
    private val rules: List<TransformationRule> = defaultRules(),
    private val random: Random = Random.Default
) {
    private val sortedRules = rules.sortedByDescending { it.priority }

    fun transformMaterial(currentMaterial: Material, explosionPower: Float, x: Int = 0, z: Int = 0, y: Int = 0): Material {
        for (rule in sortedRules) {
            if (rule.matches(currentMaterial, explosionPower)) {
                return rule.transform(currentMaterial, explosionPower, random, x, z, y)
            }
        }
        return currentMaterial
    }

    companion object {
        fun defaultRules(): List<TransformationRule> = listOf(
            TransformationRule(
                name = "Blacklisted Blocks",
                priority = 100,
                condition = TransformationCondition.MaterialSet(MaterialCategories.INDESTRUCTIBLE_BLOCKS),
                outcome = TransformationOutcome.NoTransformation
            ),

            TransformationRule(
                name = "Glass Destruction",
                priority = 90,
                condition = TransformationCondition.MaterialCategory(MaterialCategories::isGlass),
                outcome = TransformationOutcome.ToMaterial(Material.AIR)
            ),

            TransformationRule(
                name = "Brick to Iron Bars",
                priority = 85,
                condition = TransformationCondition.MaterialSet(setOf(Material.BRICKS, Material.BRICK_WALL, Material.BRICK_STAIRS, Material.BRICK_SLAB)),
                outcome = TransformationOutcome.ChanceOutcome(
                    chance = 0.2f,
                    trueOutcome = TransformationOutcome.ToMaterial(Material.IRON_BARS),
                    falseOutcome = TransformationOutcome.NoTransformation
                )
            ),

            // Example: Concrete blocks transform to terracotta/andesite palette
            TransformationRule(
                name = "Concrete to Terracotta Palette",
                priority = 82,
                condition = TransformationCondition.MaterialSet(setOf(
                    Material.WHITE_CONCRETE, Material.RED_CONCRETE, Material.BLUE_CONCRETE
                )),
                outcome = TransformationOutcome.ToPalette(
                    MaterialPalette(
                        materials = setOf(
                            MaterialPaletteEntry(Material.WHITE_TERRACOTTA, 3),
                            MaterialPaletteEntry(Material.RED_TERRACOTTA, 3),
                            MaterialPaletteEntry(Material.BLUE_TERRACOTTA, 3),
                            MaterialPaletteEntry(Material.ANDESITE, 1)
                        )
                    )
                )
            ),

            TransformationRule(
                name = "Slab Destruction",
                priority = 80,
                condition = TransformationCondition.MaterialCategory(MaterialCategories::isSlab),
                outcome = TransformationOutcome.ToRandomMaterial(
                    EnumSet.of(
                        Material.COBBLED_DEEPSLATE_SLAB,
                        Material.COBBLESTONE_SLAB
                    )
                )
            ),

            TransformationRule(
                name = "Wall Destruction",
                priority = 80,
                condition = TransformationCondition.MaterialCategory(MaterialCategories::isWall),
                outcome = TransformationOutcome.ToRandomMaterial(
                    EnumSet.of(
                        Material.COBBLED_DEEPSLATE_WALL,
                        Material.COBBLESTONE_WALL
                    )
                )
            ),

            TransformationRule(
                name = "Stairs Destruction",
                priority = 80,
                condition = TransformationCondition.MaterialCategory(MaterialCategories::isStairs),
                outcome = TransformationOutcome.ToRandomMaterial(
                    EnumSet.of(
                        Material.COBBLED_DEEPSLATE_STAIRS,
                        Material.COBBLESTONE_STAIRS
                    )
                )
            ),

            TransformationRule(
                name = "Light Weight Block Destruction",
                priority = 70,
                condition = TransformationCondition.MaterialSet(MaterialCategories.LIGHT_WEIGHT_BLOCKS),
                outcome = TransformationOutcome.ToMaterial(Material.AIR)
            ),

            TransformationRule(
                name = "Plant Destruction - High Power",
                priority = 60,
                condition = TransformationCondition.PowerThreshold(
                    TransformationCondition.MaterialSet(MaterialCategories.PLANTS),
                    minPower = 0.5f
                ),
                outcome = TransformationOutcome.ToMaterial(Material.AIR)
            ),

            TransformationRule(
                name = "Plant Destruction - Low Power",
                priority = 59,
                condition = TransformationCondition.MaterialSet(MaterialCategories.PLANTS),
                outcome = TransformationOutcome.ToRandomMaterial(MaterialCategories.DEAD_PLANTS)
            ),

            // Example: Terrain blocks use noise-based palette transformation
            TransformationRule(
                name = "Terrain Transformation with Noise",
                priority = 55,
                condition = TransformationCondition.MaterialSet(MaterialCategories.TERRAIN_BLOCKS),
                outcome = TransformationOutcome.ToPaletteWithNoise(
                    MaterialPalette(
                        materials = setOf(
                            MaterialPaletteEntry(Material.COARSE_DIRT, 4),
                            MaterialPaletteEntry(Material.MUD, 2),
                            MaterialPaletteEntry(Material.MUDDY_MANGROVE_ROOTS, 1),
                            MaterialPaletteEntry(Material.GRAVEL, 3)
                        ),
                        noiseScale = 0.05f
                    )
                )
            ),

            TransformationRule(
                name = "Dirt/Grass Transformation",
                priority = 50,
                condition = TransformationCondition.MaterialSet(setOf(Material.DIRT, Material.GRASS_BLOCK)),
                outcome = TransformationOutcome.ToRandomMaterial(
                    EnumSet.of(
                        Material.COARSE_DIRT,
                        Material.MUD,
                        Material.MUDDY_MANGROVE_ROOTS
                    )
                )
            ),

            TransformationRule(
                name = "Default Block Destruction",
                priority = 0,
                condition = TransformationCondition.MaterialCategory { true },
                outcome = TransformationOutcome.ToRandomMaterial(MaterialCategories.DESTROYED_BLOCK)
            )
        )
    }
}