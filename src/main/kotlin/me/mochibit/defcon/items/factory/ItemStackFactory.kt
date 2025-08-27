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

package me.mochibit.defcon.items.factory

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.extensions.setStringData
import me.mochibit.defcon.items.properties.ItemProperties
import me.mochibit.defcon.utils.versionGreaterOrEqualThan
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

interface ItemStackFactory {
    fun create(properties: ItemProperties): ItemStack
}

open class BaseItemStackFactory() : ItemStackFactory {
    override fun create(properties: ItemProperties): ItemStack {
        val baseMaterial = getMaterial(properties)
        val item = ItemStack(baseMaterial)
        val meta = item.itemMeta
        applyId(meta, properties)
        applyDisplayName(meta, properties)
        applyDescription(meta, properties)
        applyItemModel(meta, properties)
        applyEquipmentSlot(meta, properties)
        return item
    }

    open fun getMaterial(properties: ItemProperties): Material =
        Material.getMaterial(properties.minecraftId)
            ?: throw IllegalArgumentException("Material ${properties.minecraftId} does not exist")


    open fun applyId(baseMeta: ItemMeta, properties: ItemProperties) {
        baseMeta.setStringData(Defcon.namespacedKey("item-id"), properties.id)
    }

    open fun applyDisplayName(baseMeta: ItemMeta, properties: ItemProperties) {
        baseMeta.customName(MiniMessage.miniMessage().deserialize(properties.displayName))
    }

    open fun applyDescription(baseMeta: ItemMeta, properties: ItemProperties) {
        properties.description?.let { desc ->
            baseMeta.lore(
                desc.split("\n").map { MiniMessage.miniMessage().deserialize(it) }
            )
        }
    }

    open fun applyItemModel(baseMeta: ItemMeta, properties: ItemProperties) {
        properties.itemModel?.let {
            baseMeta.itemModel = it
        }
    }

    open fun applyEquipmentSlot(baseMeta: ItemMeta, properties: ItemProperties) {
        properties.equipmentSlot?.let {
            val component = baseMeta.equippable
            component.slot = it
            baseMeta.setEquippable(component)
        }
    }
}

class LegacyItemStackFactory(
) : BaseItemStackFactory() {
    override fun applyItemModel(baseMeta: ItemMeta, properties: ItemProperties) {
        properties.legacyProperties.legacyItemModel?.let {
            baseMeta.setCustomModelData(it)
        }
    }

    override fun getMaterial(properties: ItemProperties): Material {
        val materialName = properties.legacyProperties.legacyMinecraftId ?: properties.minecraftId
        val material = Material.getMaterial(materialName) ?: throw IllegalArgumentException("Material $materialName does not exist")
        return material
    }
}

object ApplierSupplier {
    fun getApplier(itemBaseProperties: ItemProperties): ItemStackFactory {
        return if (versionGreaterOrEqualThan("1.21.3"))
            BaseItemStackFactory()
        else
            LegacyItemStackFactory()
    }
}

