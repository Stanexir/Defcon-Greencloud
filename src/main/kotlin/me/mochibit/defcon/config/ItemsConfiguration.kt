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

import kotlinx.coroutines.sync.withLock
import me.mochibit.defcon.enums.ItemBehaviour
import org.bukkit.NamespacedKey
import org.bukkit.inventory.EquipmentSlot

object ItemsConfiguration : PluginConfiguration<List<ItemsConfiguration.ItemDefinition>>("items") {

    data class ItemDefinition(
        val id: String,
        val name: String,
        val description: String,

        val minecraftId: String,
        val legacyMinecraftId: String,

        val itemModel: NamespacedKey?,
        val itemModelId: Int,

        val itemBlockId: String? = null,

        val isUsable: Boolean,
        val isEquippable: Boolean,
        val equipmentSlot: EquipmentSlot,
        val maxStackSize: Int,
        val itemBehaviour: ItemBehaviour,
    )

    override suspend fun cleanupSchema() {}

    override suspend fun loadSchema(): List<ItemDefinition> {
        val tempItems = mutableListOf<ItemDefinition>()
        val itemsList = config.getList("enabled-items") ?: return listOf()
        itemsList.forEach { item ->
            val id = item.toString()

            val name = config.getString("$id.name") ?: return@forEach

            val description = config.getString("$id.description") ?: return@forEach

            val minecraftId = config.getString("$id.minecraft-id") ?: return@forEach

            val legacyMinecraftId = config.getString("$id.legacy-minecraft-id") ?: return@forEach

            val itemModel = config.getString("$id.item-model-name", null)?.let {
                NamespacedKey.fromString(it)
            }

            val itemModelId = config.getInt("$id.item-model-id", 0)

            val isUsable = config.getBoolean("$id.is-usable", false)

            val isEquippable = config.getBoolean("$id.is-equippable", false)

            val equipmentSlot =
                EquipmentSlot.valueOf(config.getString("$id.equipment-slot", "HAND")?.uppercase() ?: "HAND")

            val maxStackSize = config.getInt("$id.max-stack-size", 64)

            val itemBehaviour =
                ItemBehaviour.valueOf(config.getString("$id.item-behaviour", "GENERIC")?.uppercase() ?: "GENERIC")

            val itemBlockId = config.getString("$id.item-block-id")
            tempItems.add(
                ItemDefinition(
                    id = id,
                    name = name,
                    description = description,
                    minecraftId = minecraftId,
                    legacyMinecraftId = legacyMinecraftId,
                    itemModel = itemModel,
                    itemModelId = itemModelId,
                    isUsable = isUsable,
                    isEquippable = isEquippable,
                    equipmentSlot = equipmentSlot,
                    maxStackSize = maxStackSize,
                    itemBehaviour = itemBehaviour,
                    itemBlockId = itemBlockId
                )
            )
        }

        return tempItems.toList()
    }
}

