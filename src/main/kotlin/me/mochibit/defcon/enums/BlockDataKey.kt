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

package me.mochibit.defcon.enums

import me.mochibit.defcon.Defcon
import org.bukkit.NamespacedKey

enum class BlockDataKey(val key: NamespacedKey) {
    CustomBlockId(NamespacedKey(Defcon, "definitions-block-id")),
    ItemId(NamespacedKey(Defcon, "item-id")),
    StructureId(NamespacedKey(Defcon, "structure-id")),

    RadiationAreaId(NamespacedKey(Defcon, "radiation-area-id")),
    RadiationLevel(NamespacedKey(Defcon, "radiation-level")),
}