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

package me.mochibit.defcon.registry.items.variants

import me.mochibit.defcon.registry.items.properties.ItemProperties
import me.mochibit.defcon.registry.items.factory.ApplierSupplier
import me.mochibit.defcon.registry.items.factory.ItemStackFactory
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.inventory.ItemStack

sealed class BaseItem(
    override val properties: ItemProperties,
    private val mini: MiniMessage = MiniMessage.miniMessage(),
    private val itemStackFactory: ItemStackFactory = ApplierSupplier.getApplier(properties)
) : PluginItem {
    val name: String
        get() = mini.stripTags(properties.displayName)

    override val itemStack: ItemStack
        get() = itemStackFactory.create(properties)
}

