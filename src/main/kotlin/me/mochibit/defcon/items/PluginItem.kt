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

package me.mochibit.defcon.items

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.enums.ItemBehaviour
import me.mochibit.defcon.enums.ItemDataKey
import me.mochibit.defcon.extensions.setIntData
import me.mochibit.defcon.extensions.setStringData
import me.mochibit.defcon.utils.MetaManager
import me.mochibit.defcon.utils.versionGreaterOrEqualThan
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

interface PluginItem {
    fun getItemStack(): ItemStack
}


class BaseItem(
    val id: String,
    val displayName: String,
    val description: String?,

    val minecraftId: String,
    val itemModel: NamespacedKey?,

    val equipmentSlot: EquipmentSlot?,
    val maxStackSize: Int,
    val behaviour: ItemBehaviour,
    private val mini: MiniMessage = MiniMessage.miniMessage()
) : PluginItem {
    val name: String
        get() = mini.stripTags(displayName)

    override fun getItemStack(): ItemStack {
        val material =
            Material.getMaterial(minecraftId) ?: throw IllegalArgumentException("Material $minecraftId does not exist")
        val customItem = ItemStack(material)

        val itemMeta = customItem.itemMeta

        itemMeta.displayName(MiniMessage.miniMessage().deserialize(displayName))

        if (description != null) {
            itemMeta.lore(
                description.split("\n").map { MiniMessage.miniMessage().deserialize(it) }
            )
        }

        itemMeta.apply {
            setStringData(Defcon.namespacedKey("item-id"), id)
        }

        if (versionGreaterOrEqualThan("1.21.3")) {
            itemModel?.let {
                itemMeta.itemModel = it
            }
            this.equipmentSlot?.let {
                val component = itemMeta.equippable
                component.slot = it
                itemMeta.setEquippable(component)
            }
        } else {
            itemModelId?.let {
                itemMeta.setCustomModelData(it)
            }
        }

        customItem.setItemMeta(itemMeta)

        return customItem
    }
}

class LegacyPluginItem(
    private val pluginItem: PluginItem,
    val legacyMinecraftId: String,
    val legacyItemModel: Int,
) : PluginItem {
    override fun getItemStack(): ItemStack {

    }

}

