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

package me.mochibit.defcon.save.schemas

import me.mochibit.defcon.biomes.CustomBiomeHandler
import org.bukkit.NamespacedKey
import org.bukkit.event.EventPriority

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class BiomeAreaSaveSchema(
    var biomeAreas: HashSet<BoundarySaveSchema> = HashSet()
) : SaveSchema {
    override fun getMaxID(): Int {
        return biomeAreas.maxOfOrNull { it.id } ?: 0
    }

    override fun getSize(): Int {
        return biomeAreas.size
    }

    override fun getAllItems(): List<Any> {
        return biomeAreas.toList()
    }

    data class BoundarySaveSchema(
        val id: Int = 0,
        val uuid: String = "",
        val biome: NamespacedKey,
        val worldName: String = "",
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val minZ: Int,
        val maxZ: Int,
        val priority: Int = 0,
        val transition: List<BiomeTransitionSaveSchema> = emptyList()
    )

    data class BiomeTransitionSaveSchema(
        val transitionDuration: String,
        val targetBiome: NamespacedKey,
        val transitionTime: Long,
        val completed: Boolean = false,
        val targetPriority: Int = 0,
    )
}

@OptIn(ExperimentalTime::class)
fun CustomBiomeHandler.CustomBiomeBoundary.toSchema(): BiomeAreaSaveSchema.BoundarySaveSchema {
    return BiomeAreaSaveSchema.BoundarySaveSchema(
        id = id,
        uuid = uuid.toString(),
        biome = biome,
        worldName = worldName,
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        minZ = minZ,
        maxZ = maxZ,
        priority = priority,
        transition = transitions.map {
            BiomeAreaSaveSchema.BiomeTransitionSaveSchema(
                it.transitionDuration.toString(),
                it.targetBiome,
                it.transitionTime.toEpochMilliseconds(),
                it.completed,
                it.targetPriority
            )
        }
    )
}

@OptIn(ExperimentalTime::class)
fun BiomeAreaSaveSchema.BoundarySaveSchema.toCustomBiomeBoundary(): CustomBiomeHandler.CustomBiomeBoundary {
    return CustomBiomeHandler.CustomBiomeBoundary(
        id = id,
        uuid = java.util.UUID.fromString(uuid),
        biome = biome,
        worldName = worldName,
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        minZ = minZ,
        maxZ = maxZ,
        priority = priority,
        transitions = transition.map {
            CustomBiomeHandler.CustomBiomeBoundary.BiomeTransition(
                Duration.parse(it.transitionDuration),
                it.targetBiome,
                it.targetPriority,
                Instant.fromEpochMilliseconds(it.transitionTime),
                it.completed,
            )
        }
    )
}