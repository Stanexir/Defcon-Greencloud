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

import me.mochibit.defcon.enums.BlockBehaviour
import me.mochibit.defcon.utils.Logger

object BlocksConfiguration : PluginConfiguration<List<BlocksConfiguration.BlockDefinition>>("blocks") {
    data class BlockDefinition(
        val id: String,
        val displayName: String,
        val description: String,
        val minecraftId: String,
        val behaviour: BlockBehaviour
    )

    override suspend fun loadSchema(): List<BlockDefinition> {
        val tempBlocks = mutableListOf<BlockDefinition>()
        val blocksList = config.getList("blocks") ?: listOf()

        blocksList.forEach { block ->
            val id = block.toString()
            val displayName = config.getString("$id.display-name") ?: return@forEach
            val description = config.getString("$id.description") ?: return@forEach
            val minecraftId = config.getString("$id.minecraft-id") ?: return@forEach
            val behaviourValue = config.getString("$id.block-behaviour") ?: return@forEach
            val blockBehaviour = try {
                BlockBehaviour.valueOf(behaviourValue.uppercase())
            } catch (ex: IllegalArgumentException) {
                Logger.err("Invalid block behaviour, skipping.. CAUSE: $behaviourValue")
                return@forEach
            }

            tempBlocks.add(
                BlockDefinition(
                    id = id,
                    displayName = displayName,
                    description = description,
                    minecraftId = minecraftId,
                    behaviour = blockBehaviour
                )
            )
        }

        return tempBlocks.toList()
    }

    override suspend fun cleanupSchema() {}
}