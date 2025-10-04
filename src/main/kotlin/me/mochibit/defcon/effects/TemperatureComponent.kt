/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
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

package me.mochibit.defcon.effects

import me.mochibit.defcon.lifecycle.Lifecycled
import me.mochibit.defcon.utils.Gradient
import me.mochibit.defcon.utils.MathFunctions
import org.bukkit.Color

class TemperatureComponent(
    var minTemperatureEmission: Double = 1500.0,
    var maxTemperatureEmission: Double = 4000.0,
    var minTemperature: Double = 40.0,
    var maxTemperature: Double = 6000.0,
    var temperatureCoolingRate: Double = .0
) : ColorSuppliable, Lifecycled {
    override val colorSupplier: () -> Color
        get() = { blackBodyEmission() }

    var temperature: Double = maxTemperature
        set(value) {
            field = value.coerceIn(minTemperature, maxTemperature)
        }

    val color: Color
        get() = blackBodyEmission()

    fun coolDown(delta: Float = 1.0f) {
        temperature -= temperatureCoolingRate * delta
    }


    private fun blackBodyEmission(): Color {
        val ratio = MathFunctions.remap(temperature, minTemperature, maxTemperature, 0.0, 1.0)
        return temperatureEmissionGradient.getColorAt(ratio)
    }

    private val temperatureEmissionGradient = Gradient(
        arrayOf(
            Color.fromRGB(23, 19, 25),
            Color.fromRGB(9, 0, 51),
            Color.fromRGB(0, 33, 51),
            Color.fromRGB(1, 76, 47),
            Color.fromRGB(1, 99, 34),
            Color.fromRGB(7, 127, 33),
            Color.fromRGB(13, 226, 34),
            Color.fromRGB(38, 255, 63),
            Color.fromRGB(48, 255, 144),
            Color.fromRGB(127, 255, 127)
        )
    )

    override fun start() {}

    override fun update(delta: Float) {
        coolDown(delta)
    }

    override fun stop() {}


}