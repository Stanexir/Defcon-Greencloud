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

import me.mochibit.defcon.content.element.ElementBehaviour
import me.mochibit.defcon.content.items.radiationHealer.RadiationHealerDataParser
import me.mochibit.defcon.content.items.gasMask.GasMaskItem
import me.mochibit.defcon.content.items.radiationMeasurer.RadiationMeasurerItem
import me.mochibit.defcon.content.items.radiationHealer.RadiationHealerItem
import me.mochibit.defcon.content.items.structureAssembler.StructureAssemblerItem


enum class ItemBehaviour(
    override val factory: (BaseItemProperties, Map<String, Any>) -> BaseItem,
) : ElementBehaviour<BaseItemProperties, BaseItem> {
    GAS_MASK({ baseProperties, _ ->
        GasMaskItem(baseProperties)
    }),
    RADIATION_HEALER(
        RadiationHealerDataParser.withItemFactory { baseProperties, additionalData ->
            RadiationHealerItem(baseProperties, additionalData)
        }),
    RADIATION_MEASURER({ baseProperties, _ ->
        RadiationMeasurerItem(baseProperties)
    }),
    STRUCTURE_ASSEMBLER({ baseProperties, _ ->
        StructureAssemblerItem(baseProperties)
    }),
}