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

package me.mochibit.defcon.config

import me.mochibit.defcon.content.blocks.PluginBlock
import me.mochibit.defcon.content.blocks.PluginBlockProperties
import me.mochibit.defcon.content.element.ElementBehaviour
import me.mochibit.defcon.content.element.ElementDefinition
import me.mochibit.defcon.enums.BlockBehaviour
import me.mochibit.defcon.utils.Logger
import org.bukkit.configuration.ConfigurationSection

object BlocksConfiguration : PluginConfiguration<List<BlocksConfiguration.BlockDefinition>>("blocks") {
    data class BlockDefinition(
        val id: String,
        override val behaviour: ElementBehaviour<PluginBlockProperties, PluginBlock>,
        override val behaviourData: Map<String, Any>
    ): ElementDefinition<PluginBlockProperties, PluginBlock>


    override suspend fun loadSchema(): List<BlockDefinition> {
        val tempBlocks = mutableListOf<BlockDefinition>()

        config.getConfigurationSection("blocks")?.let { section ->
            tempBlocks += parseBlocksFromSection(section)
        }

        return tempBlocks.toList()
    }

    private fun parseBlocksFromSection(section: ConfigurationSection) : List<BlockDefinition> {
        return section.getKeys(false).mapNotNull { blockId ->
            val blockSection = section.getConfigurationSection(blockId) ?: run {
                Logger.warn("Block $blockId has no configuration section, skipping")
                return@mapNotNull null
            }

            val behaviourStr = blockSection.getString("behaviour") ?: run {
                Logger.warn("Block $blockId has no behaviour defined, skipping")
                return@mapNotNull null
            }

            val behaviour = try {
                BlockBehaviour.valueOf(behaviourStr.uppercase())
            } catch (e: IllegalArgumentException) {
                Logger.warn("Block $blockId has invalid behaviour '$behaviourStr', skipping")
                return@mapNotNull null
            }

            val behaviourData = mutableMapOf<String, Any>()
            blockSection.getConfigurationSection("properties")?.let { propertiesSection ->
                propertiesSection.getKeys(false).forEach { key ->
                    behaviourData[key] = propertiesSection.get(key) ?: ""
                }
            }

            BlockDefinition(
                id = blockId,
                behaviour = behaviour,
                behaviourData = behaviourData
            )
        }
    }



    override suspend fun cleanupSchema() {}
}