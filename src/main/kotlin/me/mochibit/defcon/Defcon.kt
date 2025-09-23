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
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.config.PluginConfiguration
import me.mochibit.defcon.content.listeners.EventRegister
import me.mochibit.defcon.content.pack.DatapackRegistry
import me.mochibit.defcon.content.pack.ResourcePackRegistry
import me.mochibit.defcon.notification.NotificationManager
import me.mochibit.defcon.radiation.RadiationManager
import me.mochibit.defcon.registry.BlockRegistry
import me.mochibit.defcon.registry.CommandRegistry
import me.mochibit.defcon.registry.ItemRegistry
import me.mochibit.defcon.registry.StructureRegistry
import me.mochibit.defcon.server.ResourcePackServer
import me.mochibit.defcon.utils.Logger.info
import org.bukkit.Bukkit


class Defcon : SuspendingJavaPlugin() {
    override fun onLoad() {
        info("Defcon is starting up ☢️")
        _instance = this

        EventRegister.registerPacketListeners()
    }

    override suspend fun onEnableAsync() {
        PluginConfiguration.loadAll()

        DatapackRegistry.register()
        ResourcePackRegistry.register()

        NotificationManager.startBroadcastTask()

        EventRegister.registerBukkitListeners()

        BlockRegistry.registerBlocks()
        ItemRegistry.registerItems()
        StructureRegistry.registerStructures()
        CommandRegistry.registerCommands()

        RadiationManager.start()
        CustomBiomeHandler.initialize()
        ResourcePackServer.startServer()
    }

    override suspend fun onDisableAsync() {
        NotificationManager.saveNotifications()
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

internal val pluginInstance
    get() =
        instance

internal fun pluginNamespacedKey(key: String) = org.bukkit.NamespacedKey(instance, key)
