/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
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

package me.mochibit.defcon.biomes

import me.mochibit.defcon.biomes.data.*
import me.mochibit.defcon.biomes.enums.PrecipitationType
import me.mochibit.defcon.biomes.enums.TemperatureModifier
import me.mochibit.defcon.utils.MetaManager
import org.bukkit.NamespacedKey


open class CustomBiome {
    // Get key from annotation
    val biomeKey: NamespacedKey =
        MetaManager.convertStringToNamespacedKey(this::class.java.getAnnotation(BiomeInfo::class.java)?.key ?: "minecraft:forest")

    // Default values from FOREST biome
    var temperature = 0.7f
    var downfall = 0.8f
    var precipitation = PrecipitationType.RAIN
    var temperatureModifier = TemperatureModifier.NONE
    var hasPrecipitation = true

    var effects: BiomeEffects = BiomeEffects(
        skyColor = 7972607,
        fogColor = 12638463,
        waterColor = 4159204,
        waterFogColor = 329011,
        moodSound = BiomeMoodSound(
            sound = "minecraft:ambient.cave",
            tickDelay = 6000,
            blockSearchExtent = 8,
            offset = 2.0f
        ),
    )

    // Spawners
    val monsterSpawners: HashSet<BiomeSpawner> = HashSet()
    val creatureSpawners: HashSet<BiomeSpawner> = HashSet()
    val ambientSpawners: HashSet<BiomeSpawner> = HashSet()
    val axolotlSpawners: HashSet<BiomeSpawner> = HashSet()
    val undergroundWaterCreatureSpawners: HashSet<BiomeSpawner> = HashSet()
    val waterCreatureSpawners: HashSet<BiomeSpawner> = HashSet()
    val waterAmbientSpawners: HashSet<BiomeSpawner> = HashSet()
    val miscSpawners: HashSet<BiomeSpawner> = HashSet()

    // Spawn costs (this is not an array but an object)
    val spawnCosts: HashSet<BiomeSpawnCost> = HashSet()

    // Features
    val features: HashSet<BiomeFeature> = HashSet()

    // Carvers
    val carvers: HashSet<BiomeCarver> = HashSet()

}