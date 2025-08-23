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

package me.mochibit.defcon.config

import me.mochibit.defcon.enums.ItemBehaviour
import org.bukkit.NamespacedKey
import org.bukkit.inventory.EquipmentSlot
import kotlin.text.get

object ItemsConfiguration : PluginConfiguration<List<ItemsConfiguration.ItemDefinition>>("items") {

    data class ItemDefinition(
        val id: String,
        val displayName: String,
        val description: String,

        val minecraftId: String,
        val legacyMinecraftId: String,

        val itemModel: NamespacedKey?,
        val legacyItemModel: Int,

        val equipmentSlot: EquipmentSlot?,
        val maxStackSize: Int,
        val itemBehaviour: ItemBehaviour,
        val properties: Map<String, Any> = emptyMap(),
    )

    override suspend fun cleanupSchema() {}

    override suspend fun loadSchema(): List<ItemDefinition> {
        val tempItems = mutableListOf<ItemDefinition>()
        val itemsList = config.getList("items") ?: return listOf()
        itemsList.forEach { item ->
            val id = item.toString()

            val displayName = config.getString("$id.display-name") ?: return@forEach

            val description = config.getString("$id.description") ?: return@forEach

            val minecraftId = config.getString("$id.minecraft-id") ?: return@forEach

            val legacyMinecraftId = config.getString("$id.legacy-minecraft-id") ?: return@forEach

            val itemModel = config.getString("$id.model", null)?.let {
                NamespacedKey.fromString(it)
            }

            val legacyModelId = config.getInt("$id.legacy-model-id", 0)


            val equipmentSlot = config.getString("$id.equipment-slot", null).let {
                if (it != null)
                    EquipmentSlot.valueOf(it.uppercase())
                else
                    null
            }


            val maxStackSize = config.getInt("$id.max-stack-size", 64)

            val itemBehaviour =
                ItemBehaviour.valueOf(config.getString("$id.behaviour", "GENERIC")?.uppercase() ?: "GENERIC")

            val properties = mutableMapOf<String, Any>()
            config.getConfigurationSection("$id.properties")?.let { propertiesSection ->
                propertiesSection.getKeys(false).forEach { key ->
                    properties[key] = propertiesSection.get(key) ?: ""
                }
            }
            tempItems.add(
                ItemDefinition(
                    id = id,
                    displayName = displayName,
                    description = description,
                    minecraftId = minecraftId,
                    legacyMinecraftId = legacyMinecraftId,
                    itemModel = itemModel,
                    legacyItemModel = legacyModelId,
                    equipmentSlot = equipmentSlot,
                    maxStackSize = maxStackSize,
                    itemBehaviour = itemBehaviour,
                    properties = properties,
                )
            )
        }

        return tempItems.toList()
    }
}

