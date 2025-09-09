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

object StructuresConfiguration : PluginConfiguration<List<StructuresConfiguration.StructureDefinition>>("structures") {
    data class StructureDefinition(
        val displayName: String,
        val description: String,
        val formation: StructureFormation,
    )

    data class StructureFormation(
        val type: FormationType,
        val pattern: StructurePattern? = null,
        val requiredBlocks: List<String> = emptyList(),
        val optionalBlocks: List<String> = emptyList()
    ) {
        enum class FormationType {
            SHAPED,
            SHAPELESS
        }
    }

    data class StructurePattern(
        val levels: List<Level>,
        val mappings: List<Mapping>,
        val mappingRules: List<MappingRule>
    ) {
        data class Level(
            val rows: List<String>
        )

        data class Mapping(
            val char: Char,
            val block: String,
            val anyOf: List<String> = emptyList(),
            val recommendedOf: List<String> = emptyList(),
        )

        data class MappingRule(
            val char: Char,
            val min: Int,
            val max: Int,
            val anyLocationNonAir: Boolean = false
        )
    }


    override suspend fun loadSchema(): List<StructureDefinition> {
        TODO("Not yet implemented")
    }

    override suspend fun cleanupSchema() {
        TODO("Not yet implemented")
    }

}