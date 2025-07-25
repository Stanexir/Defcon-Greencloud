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

object MainConfiguration : PluginConfiguration<MainConfiguration.BaseConfiguration>("config") {

    data class BaseConfiguration(
        val resourcePackConfig: ResourcePackConfig,
        val nuclearExplosionConfig: NuclearExplosionConfig
    ) {
        data class ResourcePackConfig(
            val enabled: Boolean,
            val serverPort: Int,

            val fallbackResourceInteger: FallbackResourceInteger,
            val fallbackDatapackInteger: FallbackDatapackInteger
        ) {
            @JvmInline
            value class FallbackResourceInteger(val value: Int)

            @JvmInline
            value class FallbackDatapackInteger(val value: Int)

        }

        data class NuclearExplosionConfig(
            val shockwaveConfig: ShockwaveConfig,
            val craterConfig: CraterConfig,
            val falloutConfig: FalloutConfig,
            val flashConfig: FlashConfig,
            val thermalConfig: ThermalConfig,
            val soundConfig: SoundConfig
        ) {
            data class ShockwaveConfig(
                val baseRadius: Int,
                val baseHeight: Int,
            )

            data class CraterConfig(
                val baseRadius: Int,
            )

            data class FalloutConfig(
                val baseRadius: Int,
                val baseSpreadHeight: Int,
                val baseSpreadDepth: Int
            )

            data class FlashConfig(
                val baseRadius: Int
            )

            data class ThermalConfig(
                val baseRadius: Int
            )

            data class SoundConfig(
                val speed: Int
            )
        }

    }

    override suspend fun loadSchema(): BaseConfiguration {
        val resourcePackConfig = BaseConfiguration.ResourcePackConfig(
            enabled = config.getBoolean("pack-generator.resource-pack.automatic-generation", true),
            serverPort = config.getInt("pack-generator.resource-pack.resource-server-port", 8000),
            fallbackResourceInteger = BaseConfiguration.ResourcePackConfig.FallbackResourceInteger(
                config.getInt("pack-generator.pack-format-fallback.resource-pack", 46)
            ),
            fallbackDatapackInteger = BaseConfiguration.ResourcePackConfig.FallbackDatapackInteger(
                config.getInt("pack-generator.pack-format-fallback.data-pack", 71)
            )
        )

        val nuclearExplosionConfig = BaseConfiguration.NuclearExplosionConfig(
            shockwaveConfig = BaseConfiguration.NuclearExplosionConfig.ShockwaveConfig(
                baseRadius = config.getInt("nuclear-explosion-settings.shockwave-config.base-radius", 800),
                baseHeight = config.getInt("nuclear-explosion-settings.shockwave-config.base-height", 300)
            ),
            craterConfig = BaseConfiguration.NuclearExplosionConfig.CraterConfig(
                baseRadius = config.getInt("nuclear-explosion-settings.crater-config.base-radius", 100)
            ),
            falloutConfig = BaseConfiguration.NuclearExplosionConfig.FalloutConfig(
                baseRadius = config.getInt("nuclear-explosion-settings.fallout-config.base-radius", 1600),
                baseSpreadHeight = config.getInt("nuclear-explosion-settings.fallout-config.base-spread-height", 50),
                baseSpreadDepth = config.getInt("nuclear-explosion-settings.fallout-config.base-underground-spread-depth", 25)
            ),
            flashConfig = BaseConfiguration.NuclearExplosionConfig.FlashConfig(
                baseRadius = config.getInt("nuclear-explosion-settings.flash-config.base-radius", 1000)
            ),
            thermalConfig = BaseConfiguration.NuclearExplosionConfig.ThermalConfig(
                baseRadius = config.getInt("nuclear-explosion-settings.thermal-config.base-radius", 1000)
            ),
            soundConfig = BaseConfiguration.NuclearExplosionConfig.SoundConfig(
                speed = config.getInt("nuclear-explosion-settings.sound-config.sound-speed", 50)
            )
        )

        return BaseConfiguration(
            resourcePackConfig = resourcePackConfig,
            nuclearExplosionConfig = nuclearExplosionConfig
        )

    }

    override suspend fun cleanupSchema() {}

}