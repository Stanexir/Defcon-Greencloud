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

package me.mochibit.defcon.content.items

import me.mochibit.defcon.config.ItemsConfiguration
import me.mochibit.defcon.content.element.AbstractElementFactory
import me.mochibit.defcon.content.items.BaseItem


object ItemFactory : AbstractElementFactory<BaseItemProperties, BaseItem, ItemsConfiguration.ItemDefinition>() {
    override fun makeBaseProperties(elementDefinition: ItemsConfiguration.ItemDefinition): BaseItemProperties {
        return BaseItemProperties(
            id = elementDefinition.id,
            displayName = elementDefinition.displayName,
            description = elementDefinition.description,

            minecraftId = elementDefinition.minecraftId,
            itemModel = elementDefinition.itemModel,
            equipmentSlot = elementDefinition.equipmentSlot,
            maxStackSize = elementDefinition.maxStackSize,
            legacyProperties = ItemProperties.LegacyProperties(
                legacyMinecraftId = elementDefinition.legacyMinecraftId,
                legacyItemModel = elementDefinition.legacyItemModel
            )
        )
    }

    override fun getAdditionalData(elementDefinition: ItemsConfiguration.ItemDefinition): Map<String, Any> {
        return elementDefinition.properties
    }
}