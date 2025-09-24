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

package me.mochibit.defcon.content.blocks

import me.mochibit.defcon.content.element.Element
import me.mochibit.defcon.content.element.ElementBehaviourPropParser
import me.mochibit.defcon.content.element.ElementBehaviourProperties
import me.mochibit.defcon.content.items.PluginItem
import net.kyori.adventure.text.minimessage.MiniMessage

abstract class PluginBlock(
    override val properties: PluginBlockProperties,
    override val unparsedBehaviourData: Map<String, Any>,
    override val behaviourPropParser: ElementBehaviourPropParser? = null,
    override val behaviourProperties: ElementBehaviourProperties? = behaviourPropParser?.parse(unparsedBehaviourData),

    private val mini: MiniMessage = MiniMessage.miniMessage(),

    private var _linkedItem: PluginItem? = null
): Element {
    /**
     * Links this block to an item. This creates a bidirectional relationship.
     * @param item The item to link to this block
     */
    fun linkItem(item: PluginItem) {
        if (_linkedItem == item) return

        _linkedItem?.unlinkBlock() // Unlink from previous item if exists
        _linkedItem = item

        if (item.linkedBlock != this) {
            item.linkBlock(this)
        }
    }

    /**
     * Unlinks this block from its associated item
     */
    fun unlinkItem() {
        _linkedItem?.let { item ->
            _linkedItem = null
            if (item.linkedBlock == this) {
                item.unlinkBlock()
            }
        }
    }

    val linkedItem: PluginItem?
        get() = _linkedItem

    val hasLinkedItem: Boolean
        get() = _linkedItem != null

    abstract override fun copy(): PluginBlock
}
