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

package me.mochibit.defcon.content.items.radiationHealer

import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.content.items.PluginItemProperties

data class RadiationHealerItem(
    override val properties: PluginItemProperties,
    override val unparsedBehaviourData: Map<String, Any>,
) : PluginItem(properties, unparsedBehaviourData, RadiationHealerDataParser) {
    override val behaviourProperties: RadiationHealerProperties by lazy {
        super.behaviourProperties as RadiationHealerProperties
    }

    override fun copy(): RadiationHealerItem = copy()
}