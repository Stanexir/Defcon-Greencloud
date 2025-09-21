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
import me.mochibit.defcon.content.items.gasMask.GasMaskItem
import me.mochibit.defcon.content.items.radiationHealer.RadiationHealerDataParser
import me.mochibit.defcon.content.items.radiationHealer.RadiationHealerItem
import me.mochibit.defcon.content.items.radiationMeasurer.RadiationMeasurerItem
import me.mochibit.defcon.content.items.structureAssembler.StructureAssemblerItem


object PluginItemFactory :
    AbstractElementFactory<PluginItemProperties, PluginItem, ItemsConfiguration.ItemDefinition>() {
    override fun create(elementDefinition: ItemsConfiguration.ItemDefinition): PluginItem {
        val properties = PluginItemProperties(
            id = elementDefinition.id,
            displayName = elementDefinition.displayName,
            description = elementDefinition.description,

            minecraftId = elementDefinition.minecraftId,
            itemModel = elementDefinition.itemModel,
            equipmentSlot = elementDefinition.equipmentSlot,
            maxStackSize = elementDefinition.maxStackSize,
            legacyProperties = PluginItemProperties.LegacyProperties(
                legacyMinecraftId = elementDefinition.legacyMinecraftId,
                legacyItemModel = elementDefinition.legacyItemModel
            )
        )

        return elementDefinition.behaviour.elementConstructor(
            properties,
            elementDefinition.behaviourData
        )
    }
}