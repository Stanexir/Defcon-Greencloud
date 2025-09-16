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

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.content.items.gasMask.GasMaskItem
import me.mochibit.defcon.content.items.radiationHealer.RadiationHealerItem
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe

abstract class BaseItem(
    override val properties: ItemProperties,
    private val mini: MiniMessage = MiniMessage.miniMessage(),
    private val itemStackFactory: ItemStackFactory = ApplierSupplier.getApplier()
) : PluginItem {
    val name: String
        get() = mini.stripTags(properties.displayName)

    override val itemStack: ItemStack
        get() = itemStackFactory.create(properties)
}