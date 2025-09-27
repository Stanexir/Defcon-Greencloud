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

import me.mochibit.defcon.content.blocks.PluginBlock
import me.mochibit.defcon.content.element.Element
import me.mochibit.defcon.content.element.ElementBehaviourPropParser
import me.mochibit.defcon.content.element.ElementBehaviourProperties
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.inventory.ItemStack


abstract class PluginItem(
    override val properties: PluginItemProperties,
    override val unparsedBehaviourData: Map<String, Any>,
    override val behaviourPropParser: ElementBehaviourPropParser? = null,
    override val behaviourProperties: ElementBehaviourProperties? = behaviourPropParser?.parse(unparsedBehaviourData),

    private val mini: MiniMessage = MiniMessage.miniMessage(),
    private val itemStackFactory: ItemStackFactory = FactoryMetaStrategies.getFactory(),

    private var _linkedBlock: PluginBlock? = null
) : Element {
    val name: String
        get() = mini.stripTags(properties.displayName)

    val itemStack: ItemStack
        get() = itemStackFactory.create(properties)

    val isEquippable: Boolean
        get() = properties.equipmentSlot?.isArmor ?: false

    /**
     * Links this item to a block. This creates a bidirectional relationship.
     * @param block The block to link to this item
     */
    fun linkBlock(block: PluginBlock) {
        if (_linkedBlock == block) return

        _linkedBlock?.unlinkItem() // Unlink from previous block if exists
        _linkedBlock = block

        if (block.linkedItem != this) {
            block.linkItem(this)
        }
    }

    /**
     * Unlinks this item from its associated block
     */
    fun unlinkBlock() {
        _linkedBlock?.let { block ->
            _linkedBlock = null
            if (block.linkedItem == this) {
                block.unlinkItem()
            }
        }
    }

    val linkedBlock: PluginBlock?
        get() = _linkedBlock

    val hasLinkedBlock: Boolean
        get() = _linkedBlock != null

    abstract override fun copied(): PluginItem
}