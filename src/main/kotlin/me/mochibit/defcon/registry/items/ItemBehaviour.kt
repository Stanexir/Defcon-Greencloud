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

package me.mochibit.defcon.registry.items

import me.mochibit.defcon.registry.behavioural.ElementBehaviour
import me.mochibit.defcon.registry.behavioural.ElementProperties
import me.mochibit.defcon.registry.items.properties.BaseProperties
import me.mochibit.defcon.registry.items.properties.RadiationHealerData
import me.mochibit.defcon.registry.items.variants.BaseItem
import me.mochibit.defcon.registry.items.variants.GasMaskItem
import me.mochibit.defcon.registry.items.variants.GeigerCounterItem
import me.mochibit.defcon.registry.items.variants.RadiationHealerItem
import me.mochibit.defcon.registry.items.variants.StructureAssemblerItem


enum class ItemBehaviour(
    override val factory: (BaseProperties, Map<String, Any>) -> BaseItem
): ElementBehaviour<BaseProperties, BaseItem> {
    GAS_MASK({ baseProperties, additionalData ->
        GasMaskItem(baseProperties)
    }),
    RADIATION_HEALER({ baseProperties, additionalData ->
        val healAmount = (additionalData["heal-amount"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("heal-amount is required and must be a number")
        val durationSeconds = (additionalData["duration-seconds"] as? Number)?.toLong()
            ?: throw IllegalArgumentException("duration-seconds is required and must be a number")
        val data = RadiationHealerData(baseProperties, healAmount, durationSeconds)
        RadiationHealerItem(data)
    }),
    GEIGER_COUNTER({ baseProperties, additionalData ->
        GeigerCounterItem(baseProperties)
    }),
    STRUCTURE_ASSEMBLER({ baseProperties, additionalData ->
        StructureAssemblerItem(baseProperties)
    }),
}