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

package me.mochibit.defcon.biomes.data

import me.mochibit.defcon.biomes.enums.SpawnerCategory
import org.bukkit.entity.EntityType


data class BiomeSpawner (
    var spawnerCategory: SpawnerCategory = SpawnerCategory.CREATURE,
    var type: EntityType = EntityType.ZOMBIE,
    var weight: Int = 1,
    var minCount: Int = 0,
    var maxCount: Int = 0
)
