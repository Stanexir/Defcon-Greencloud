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
import kotlin.random.Random

data class TransformationRule(
    val name: String,
    val priority: Int = 0,
    val condition: TransformationCondition,
    val outcome: TransformationOutcome
) {
    fun matches(material: Material, explosionPower: Float): Boolean {
        return condition.matches(material, explosionPower)
    }

    fun transform(material: Material, explosionPower: Float, random: Random, x: Int = 0, z: Int = 0, y: Int = 0): Material {
        return outcome.transform(material, explosionPower, random, x, z, y)
    }
}