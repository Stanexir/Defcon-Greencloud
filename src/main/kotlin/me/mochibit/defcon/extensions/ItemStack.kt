/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
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

package me.mochibit.defcon.extensions

import me.mochibit.defcon.content.items.ItemBehaviour
import me.mochibit.defcon.utils.MetaManager
import org.bukkit.inventory.ItemStack


fun ItemStack.getItemID(): String? {
    return "item_id"
}

fun ItemStack.equipSlotName(): String {
    return "HAND"
}

fun ItemStack.getBehaviour(): ItemBehaviour {
    return ItemBehaviour.RADIATION_MEASURER
}
