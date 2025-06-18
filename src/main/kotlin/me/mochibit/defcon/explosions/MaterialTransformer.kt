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

package me.mochibit.defcon.explosions

import org.bukkit.Material
import java.util.*
import kotlin.random.Random

/**
 * Represents a weighted list of materials for random selection
 */
data class WeightedMaterialList(val materials: List<Pair<Material, Double>>) {
    private val totalWeight = materials.sumOf { it.second }

    fun random(random: Random): Material {
        val randomValue = random.nextDouble() * totalWeight
        var currentWeight = 0.0

        for ((material, weight) in materials) {
            currentWeight += weight
            if (randomValue <= currentWeight) {
                return material
            }
        }

        // Fallback to last material
        return materials.lastOrNull()?.first ?: Material.STONE
    }
}

// Data classes for transformation rules
data class TransformationRule(
    val name: String,
    val priority: Int = 0,
    val condition: TransformationCondition,
    val outcome: TransformationOutcome
) {
    fun matches(material: Material, explosionPower: Float): Boolean {
        return condition.matches(material, explosionPower)
    }

    fun transform(material: Material, explosionPower: Float, random: Random): Material {
        return outcome.transform(material, explosionPower, random)
    }
}

sealed class TransformationCondition {
    abstract fun matches(material: Material, explosionPower: Float): Boolean

    data class MaterialSet(val materials: Set<Material>) : TransformationCondition() {
        override fun matches(material: Material, explosionPower: Float): Boolean = material in materials
    }

    data class MaterialCategory(val predicate: (Material) -> Boolean) : TransformationCondition() {
        override fun matches(material: Material, explosionPower: Float): Boolean = predicate(material)
    }

    data class SpecificMaterial(val material: Material) : TransformationCondition() {
        override fun matches(material: Material, explosionPower: Float): Boolean = this.material == material
    }

    data class PowerThreshold(
        val condition: TransformationCondition,
        val minPower: Float? = null,
        val maxPower: Float? = null
    ) : TransformationCondition() {
        override fun matches(material: Material, explosionPower: Float): Boolean {
            val powerMatches = (minPower == null || explosionPower >= minPower) &&
                    (maxPower == null || explosionPower <= maxPower)
            return powerMatches && condition.matches(material, explosionPower)
        }
    }

    data class Combined(
        val conditions: List<TransformationCondition>,
        val operator: LogicalOperator = LogicalOperator.AND
    ) : TransformationCondition() {
        override fun matches(material: Material, explosionPower: Float): Boolean {
            return when (operator) {
                LogicalOperator.AND -> conditions.all { it.matches(material, explosionPower) }
                LogicalOperator.OR -> conditions.any { it.matches(material, explosionPower) }
            }
        }
    }

    enum class LogicalOperator { AND, OR }
}

sealed class TransformationOutcome {
    abstract fun transform(material: Material, explosionPower: Float, random: Random): Material

    data class ToMaterial(val material: Material) : TransformationOutcome() {
        override fun transform(material: Material, explosionPower: Float, random: Random): Material = this.material
    }

    data class ToRandomMaterial(val materials: Set<Material>) : TransformationOutcome() {
        override fun transform(material: Material, explosionPower: Float, random: Random): Material {
            return materials.random(random)
        }
    }

    data class ConditionalOutcome(
        val condition: (Material, Float) -> Boolean,
        val trueOutcome: TransformationOutcome,
        val falseOutcome: TransformationOutcome
    ) : TransformationOutcome() {
        override fun transform(material: Material, explosionPower: Float, random: Random): Material {
            return if (condition(material, explosionPower)) {
                trueOutcome.transform(material, explosionPower, random)
            } else {
                falseOutcome.transform(material, explosionPower, random)
            }
        }
    }

    /**
     * Transform based on a palette mapping
     */
    data class ToPaletteMaterial(val palette: Map<Material, Material>) : TransformationOutcome() {
        override fun transform(material: Material, explosionPower: Float, random: Random): Material {
            return palette[material] ?: material
        }
    }

    /**
     * Transform based on a palette with multiple possible outcomes per material
     */
    data class ToRandomPaletteMaterial(val palette: Map<Material, Set<Material>>) : TransformationOutcome() {
        override fun transform(material: Material, explosionPower: Float, random: Random): Material {
            val possibleMaterials = palette[material]
            return possibleMaterials?.random(random) ?: material
        }
    }

    data class ToWeightedPaletteMaterial(val palette: Map<Material, WeightedMaterialList>) : TransformationOutcome() {
        override fun transform(material: Material, explosionPower: Float, random: Random): Material {
            val weightedList = palette[material]
            return weightedList?.random(random) ?: material
        }
    }

    object NoTransformation : TransformationOutcome() {
        override fun transform(material: Material, explosionPower: Float, random: Random): Material = material
    }
}


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

    val BURNT_BLOCK: EnumSet<Material> = EnumSet.of(
        Material.COBBLED_DEEPSLATE,
        Material.BLACK_CONCRETE_POWDER,
        Material.OBSIDIAN
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

    val SLABS: EnumSet<Material> = EnumSet.noneOf(Material::class.java).apply {
        for (material in Material.entries) {
            if (material.name.endsWith("_SLAB")) {
                add(material)
            }
        }
    }

    val WALLS: EnumSet<Material> = EnumSet.noneOf(Material::class.java).apply {
        for (material in Material.entries) {
            if (material.name.endsWith("_WALL")) {
                add(material)
            }
        }
    }

    val STAIRS: EnumSet<Material> = EnumSet.noneOf(Material::class.java).apply {
        for (material in Material.entries) {
            if (material.name.endsWith("_STAIRS")) {
                add(material)
            }
        }
    }

    val GLASS: EnumSet<Material> = EnumSet.noneOf(Material::class.java).apply {
        for (material in Material.entries) {
            if (material.name.contains("GLASS", ignoreCase = true)) {
                add(material)
            }
        }
    }

    // Helper functions for categories
    fun isSlab(material: Material): Boolean = material.name.endsWith("_SLAB")
    fun isWall(material: Material): Boolean = material.name.endsWith("_WALL")
    fun isStairs(material: Material): Boolean = material.name.endsWith("_STAIRS")
    fun isGlass(material: Material): Boolean = material.name.contains("GLASS", ignoreCase = true)
}


class MaterialTransformer(
    private val rules: List<TransformationRule> = defaultRules(),
    private val random: Random = Random.Default
) {

    // Pre-sorted rules by priority for better performance
    private val sortedRules = rules.sortedByDescending { it.priority }

    fun transformMaterial(currentMaterial: Material, explosionPower: Float): Material {
        // Find the first matching rule and apply it
        for (rule in sortedRules) {
            if (rule.matches(currentMaterial, explosionPower)) {
                return rule.transform(currentMaterial, explosionPower, random)
            }
        }

        // Default fallback if no rule matches
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

            // Medium priority: Light materials
            TransformationRule(
                name = "Light Weight Block Destruction",
                priority = 70,
                condition = TransformationCondition.MaterialSet(MaterialCategories.LIGHT_WEIGHT_BLOCKS),
                outcome = TransformationOutcome.ToMaterial(Material.AIR)
            ),

            // Plant transformation with power consideration
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

            // Specific material transformations
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

            // Default transformation
            TransformationRule(
                name = "Default Block Destruction",
                priority = 0,
                condition = TransformationCondition.MaterialCategory { true }, // Matches everything
                outcome = TransformationOutcome.ToRandomMaterial(MaterialCategories.DESTROYED_BLOCK)
            )
        )

        /**
         * Create a transformer with palette-based rules
         */
        fun withPaletteRules(paletteRules: List<TransformationRule>): MaterialTransformer {
            return MaterialTransformer(paletteRules + defaultRules())
        }

        // Factory method for creating transformer with custom rules
        fun withCustomRules(customRules: List<TransformationRule>): MaterialTransformer {
            return MaterialTransformer(defaultRules() + customRules)
        }

        // Factory method for creating transformer with only custom rules
        fun withOnlyCustomRules(customRules: List<TransformationRule>): MaterialTransformer {
            return MaterialTransformer(customRules)
        }
    }
}