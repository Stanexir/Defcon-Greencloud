/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024-2025 mochibit.
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

package me.mochibit.defcon.events.handlers.blocks

import me.mochibit.defcon.events.AutoRegisterHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent

@AutoRegisterHandler
class RadiationAreaExpand: Listener {

    // On block update, if the block has a neighbor wich is a radioactive block, expand the radiation area

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {

    }

}