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

package me.mochibit.defcon.listeners.items

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.github.shynixn.mccoroutine.bukkit.ticks
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent
import kotlinx.coroutines.delay
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.content.items.ItemBehaviour
import me.mochibit.defcon.content.items.gasMask.GasMaskItem
import me.mochibit.defcon.events.equip.CustomItemEquipEvent
import me.mochibit.defcon.events.radiationarea.RadiationSuffocationEvent
import me.mochibit.defcon.extensions.random
import me.mochibit.defcon.content.listeners.UniversalVersionIndicator
import me.mochibit.defcon.content.listeners.VersionIndicator
import me.mochibit.defcon.extensions.getPluginItem
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener


@UniversalVersionIndicator
class GasMaskProtectListener: Listener {
    @EventHandler
    fun protectFromGas(event: RadiationSuffocationEvent) {
        val player = event.getPlayer()

        // Check if the player has a gas mask
        val helmet = player.inventory.helmet ?: return

        val helmetPluginItem = helmet.getPluginItem() ?: return
        if (helmetPluginItem !is GasMaskItem) return

        // Cancel the event
        event.setCancelled(true)
    }
}

@VersionIndicator("1.21.4")
class GasMaskEquipListener : Listener {
    @EventHandler
    fun onGasMaskEquip(event: EntityEquipmentChangedEvent) {
        val player = event.entity as? Player ?: return

        // Check if the player has equipped a gas mask
        val helmet = player.inventory.helmet ?: return
        val helmetPluginItem = helmet.getPluginItem() ?: return
        if (helmetPluginItem !is GasMaskItem) return

        // Play the sound
        playGasMaskSound(player)
    }

    private fun playGasMaskSound(player: Player) {
        Defcon.launch(Defcon.minecraftDispatcher) {
            val randomizedPitch = (0.6f..0.9f).random()
            player.world.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 2.0f, randomizedPitch)
        }
    }
}

@VersionIndicator("1.20", "1.21.3")
class GasMaskEquipListenerLegacy: Listener {

    @EventHandler
    fun onGasMaskEquip(event: CustomItemEquipEvent) {
        // Check if the item is a gas mask
        if (event.equippedItem !is GasMaskItem) return

        // Check if the player has no helmet (so right-click would equip it)
        val currentHelmet = event.player.inventory.helmet
        if (currentHelmet != null && !currentHelmet.type.isAir) return

        // The right-click auto-equip happens AFTER this event fires
        // So we need a delayed task to check if it was actually equipped
        Defcon.launch {
            delay(1.ticks)
            val helmet = event.player.inventory.helmet
            val helmetPluginItem = helmet?.getPluginItem() ?: return@launch
            if (helmetPluginItem is GasMaskItem) {
                playGasMaskSound(event.player)
            }
        }
    }
}

// Helper function to play the sound
private fun playGasMaskSound(player: Player) {
    Defcon.launch(Defcon.minecraftDispatcher) {
        val randomizedPitch = (0.6f..0.9f).random()
        player.world.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 2.0f, randomizedPitch)
    }
}
