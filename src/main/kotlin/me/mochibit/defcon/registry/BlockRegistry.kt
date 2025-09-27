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

package me.mochibit.defcon.registry

import me.mochibit.defcon.config.BlocksConfiguration
import me.mochibit.defcon.content.blocks.PluginBlock
import me.mochibit.defcon.content.blocks.PluginBlockFactory
import me.mochibit.defcon.enums.BlockDataKey
import me.mochibit.defcon.utils.Logger.info
import me.mochibit.defcon.utils.Logger.warn
import me.mochibit.defcon.utils.MetaManager
import org.bukkit.Location
import org.bukkit.World
import org.joml.Vector3i

/**
 * This class handles the registration of the definitions blocks
 * All the registered blocks are stored and returned in a form of a Map(id, PluginBlock)
 */
object BlockRegistry {
    private var _registeredBlocks: MutableMap<String, PluginBlock> = mutableMapOf()
    val registeredBlocks: Map<String, PluginBlock> get() = _registeredBlocks

    /**
     * Registers all plugin blocks from configuration
     * @return True if all blocks are registered successfully, false otherwise
     */
    suspend fun registerBlocks(): Boolean {
        info("Registering plugin blocks...")
        _registeredBlocks.clear()

        val configurationBlocks = BlocksConfiguration.getSchema()
        if (configurationBlocks.isEmpty()) {
            warn("No blocks found in the configuration, skipping block registration")
            return false
        }

        configurationBlocks.forEach { blockDef ->
            if (_registeredBlocks.containsKey(blockDef.id)) {
                warn("Block ${blockDef.id} is already registered (probably duplicated?), skipping")
                return@forEach
            }

            val customBlock = PluginBlockFactory.create(blockDef)
            info("Registered block ${blockDef.id}")
            _registeredBlocks[customBlock.properties.id] = customBlock
        }

        return true
    }

    /**
     * Retrieves a block by location. Returns a copy to prevent shared state issues.
     */
    fun getBlock(location: Location): PluginBlock? {
        val customBlockId = MetaManager.getBlockData<String>(location, BlockDataKey.CustomBlockId) ?: return null
        return getBlock(customBlockId)
    }

    /**
     * Retrieves a block by Vector3i location. Returns a copy to prevent shared state issues.
     */
    fun getBlock(loc: Vector3i, world: World): PluginBlock? {
        val location = Location(world, loc.x.toDouble(), loc.y.toDouble(), loc.z.toDouble())
        return getBlock(location)
    }

    /**
     * Retrieves a block by ID. Returns a copy to prevent shared state issues.
     * This is the main retrieval method that ensures thread safety and state isolation.
     */
    fun getBlock(id: String): PluginBlock? = _registeredBlocks[id]?.copied()

    /**
     * Gets the original registered block template (not a copy).
     * USE WITH CAUTION: This returns the actual registered instance.
     * Only use this for template inspection, never for runtime block instances.
     */
    fun getBlockTemplate(id: String): PluginBlock? = _registeredBlocks[id]

    /**
     * Returns copies of all registered blocks to prevent shared state issues.
     */
    fun getAllBlocks(): Collection<PluginBlock> = _registeredBlocks.values.map { it.copied() }

    /**
     * Returns the original templates of all registered blocks.
     * USE WITH CAUTION: These are the actual registered instances.
     */
    fun getAllBlockTemplates(): Collection<PluginBlock> = _registeredBlocks.values

    fun isBlockRegistered(id: String): Boolean = _registeredBlocks.containsKey(id)
}