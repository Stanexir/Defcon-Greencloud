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
import me.mochibit.defcon.registry.BlockRegistry
import me.mochibit.defcon.utils.Logger

object PluginItemFactory :
    AbstractElementFactory<PluginItemProperties, PluginItem, ItemsConfiguration.ItemDefinition>() {

    private const val DEFAULT_MINECRAFT_ID = "minecraft:stick"

    override fun create(elementDefinition: ItemsConfiguration.ItemDefinition): PluginItem {
        val properties = createItemProperties(elementDefinition)
        val customItem = createCustomItem(elementDefinition, properties)
        return customItem
    }

    private fun createItemProperties(elementDefinition: ItemsConfiguration.ItemDefinition): PluginItemProperties {
        return PluginItemProperties(
            id = elementDefinition.id,
            displayName = elementDefinition.displayName,
            description = elementDefinition.description,
            minecraftId = resolveMinecraftId(elementDefinition),
            itemModel = elementDefinition.itemModel,
            equipmentSlot = elementDefinition.equipmentSlot,
            maxStackSize = elementDefinition.maxStackSize,
            legacyProperties = createLegacyProperties(elementDefinition)
        )
    }

    private fun resolveMinecraftId(elementDefinition: ItemsConfiguration.ItemDefinition): String {
        return when {
            elementDefinition.minecraftId != null -> elementDefinition.minecraftId
            elementDefinition.isBlockItem -> getBlockMinecraftId(elementDefinition.id)
            else -> DEFAULT_MINECRAFT_ID
        }
    }

    private fun getBlockMinecraftId(blockId: String): String {
        return BlockRegistry.getBlockTemplate(blockId)
            ?.properties
            ?.blockBasis
            ?: DEFAULT_MINECRAFT_ID
    }

    private fun createLegacyProperties(elementDefinition: ItemsConfiguration.ItemDefinition): PluginItemProperties.LegacyProperties {
        return PluginItemProperties.LegacyProperties(
            legacyMinecraftId = elementDefinition.legacyMinecraftId,
            legacyItemModel = elementDefinition.legacyItemModel
        )
    }

    private fun createCustomItem(
        elementDefinition: ItemsConfiguration.ItemDefinition,
        properties: PluginItemProperties
    ): PluginItem {
        return elementDefinition.behaviour.elementConstructor(
            properties,
            elementDefinition.behaviourData
        )
    }
}