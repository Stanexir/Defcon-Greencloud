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

package me.mochibit.defcon.registry.items

import me.mochibit.defcon.config.ItemsConfiguration
import me.mochibit.defcon.registry.items.properties.BaseProperties
import me.mochibit.defcon.registry.items.variants.PluginItem


object PluginItemFactory {
    fun create(item: ItemsConfiguration.ItemDefinition): PluginItem {
        val baseProperties = BaseProperties(
            id = item.id,
            displayName = item.displayName,
            description = item.description,

            minecraftId = item.minecraftId,
            itemModel = item.itemModel,
            equipmentSlot = item.equipmentSlot,
            maxStackSize = item.maxStackSize,
            legacyProperties = BaseProperties.LegacyProperties(
                legacyMinecraftId = item.legacyMinecraftId,
                legacyItemModel = item.legacyItemModel
            )
        )

        val itemSubtypeFactory = item.behaviour.factory
        val item = itemSubtypeFactory(baseProperties, item.properties)
        return item
    }
}