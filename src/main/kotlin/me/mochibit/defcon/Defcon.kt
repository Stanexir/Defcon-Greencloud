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

package me.mochibit.defcon

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import me.mochibit.defcon.Defcon.Companion.instance
import me.mochibit.defcon.utils.Logger.info
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.config.PluginConfiguration
import me.mochibit.defcon.notification.NotificationManager
import me.mochibit.defcon.radiation.RadiationManager
import me.mochibit.defcon.content.*
import me.mochibit.defcon.content.items.ItemRegistry
import me.mochibit.defcon.content.listeners.EventRegister
import me.mochibit.defcon.content.pack.DatapackRegister
import me.mochibit.defcon.content.pack.ResourcePackRegister
import me.mochibit.defcon.server.ResourcePackServer
import org.bukkit.Bukkit


class Defcon : SuspendingJavaPlugin() {
    override fun onLoad() {
        _instance = this
//        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
//        PacketEvents.getAPI().load()

        EventRegister.registerPacketListeners()
        info("Defcon is starting up ☢️")
    }

    override suspend fun onEnableAsync() {
        ResourcePackServer.startServer()
//        PacketEvents.getAPI().init()
        info("Plugin is enabled! Loading configurations...")
        PluginConfiguration.loadAll()

        info("Registering resource pack and datapack")
        DatapackRegister.register()
        ResourcePackRegister.register()

        NotificationManager.startBroadcastTask()

        /* Register all plugin's listeners */
        EventRegister.registerAllListeners()

        /* Register definitions items */
        if (!ItemRegistry().registerItems()) {
            info("Some items were not registered!")
        }

        /* Register definitions blocks */
        BlockRegister().registerBlocks()

        /* Register commands */
        CommandRegister().registerCommands()

        /* Register structures */
//        StructureRegister().registerStructures()


        // Start radiation processor
        RadiationManager.start()


        // Start the custom biome handler
        CustomBiomeHandler.initialize()
    }

    override suspend fun onDisableAsync() {
        NotificationManager.saveNotifications()
//        PacketEvents.getAPI().terminate()
        ResourcePackServer.stopServer()
        CustomBiomeHandler.shutdown()
        info("Plugin disabled!")
    }

    companion object {
        private lateinit var _instance: Defcon
        val instance get() = _instance
        val minecraftVersion = Bukkit.getServer().bukkitVersion.split("-")[0]
    }
}

internal fun pluginNamespacedKey(key: String) = org.bukkit.NamespacedKey(instance, key)
