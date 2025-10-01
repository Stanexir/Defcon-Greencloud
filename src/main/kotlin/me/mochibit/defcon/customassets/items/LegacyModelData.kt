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

package me.mochibit.defcon.customassets.items

import kotlinx.serialization.Serializable
import org.bukkit.Color
import org.bukkit.Material
import java.util.*

data class LegacyModelData(
    val originalItem : Material = Material.FLINT,
    val originalItemName: String = originalItem.name.lowercase(Locale.getDefault()),
    val modelName: String,
    val parent: ParentType = ParentType.ITEM_GENERATED,
    val textures: Map<String, String> = mapOf("layer0" to "${if (originalItem.isBlock) "block" else "item"}/${originalItemName}"),
    val overrides: Set<Override> = setOf(),
    val customModelData: Int = 1,
    val model: String = "${if (originalItem.isBlock) "block" else "item"}/$modelName/$modelName",
    val animationFrames: Map<Int, String> = mapOf(),
    )

@Serializable
data class ModelData(
    val name: String,
    val type: String = "model",
    val isItem : Boolean = true,
    val model: String = "${if (isItem) "item" else "block"}/$name/$name",
    val tints: List<TintDefinition> = emptyList()
) {
    @Serializable
    sealed interface TintDefinition {
        val type: String
    }

    data class DyeTint(
        override val type: String = "minecraft:dye",
        val default: Int = Color.WHITE.asRGB()
    ) : TintDefinition
}


