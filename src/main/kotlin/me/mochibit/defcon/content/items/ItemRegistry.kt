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

import me.mochibit.defcon.config.ItemsConfiguration
import me.mochibit.defcon.utils.Logger

/**
 * This class handles the registration of the definitions items
 * All the registered items are stored and returned in a form of a HashMap(id, CustomItem)
 *
 * To initialize create an instance of ItemRegister and execute the method registerItems, it will automatically
 * load up correctly the definitions items
 *
 */
class ItemRegistry {
    /**
     *
     * @return boolean - True if all items are registered, false if some error occurred.
     */
    suspend fun registerItems(): Boolean {
        registeredItems = HashMap()

        val configurationItems = ItemsConfiguration.getSchema()
        if (configurationItems.isEmpty()) {
            Logger.warn("No items found in the configuration, skipping item registration")
            return false
        }

        configurationItems.forEach { item ->
            registeredItems[item.id]?.let {
                Logger.warn("Item ${item.id} is already registered (probably duplicated?), skipping")
                return@forEach
            }
            val customItem = PluginItemFactory.create(item)
            Logger.info("Registered item ${item.id}")
            registeredItems[customItem.properties.id] = customItem
        }

        return true
    }

    companion object {
        /**
         * Static member to access the registered items
         */
        var registeredItems: HashMap<String?, PluginItem?> = HashMap()

    }
}