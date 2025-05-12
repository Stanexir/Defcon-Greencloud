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

import me.mochibit.defcon.utils.Gradient
import me.mochibit.defcon.utils.MathFunctions
import org.bukkit.Color
import kotlin.math.exp
import kotlin.math.pow

/**
 * Simulates nuclear explosion temperature effects with a bright yellow core transitioning to red,
 * eventually fading to smoke shades.
 *
 * @property minTemperatureEmission Minimum temperature for emission calculation (Kelvin)
 * @property maxTemperatureEmission Maximum temperature for emission calculation (Kelvin)
 * @property minTemperature Absolute minimum temperature the component can reach (Kelvin)
 * @property maxTemperature Absolute maximum temperature the component can reach (Kelvin)
 * @property baseCoolingRate Base rate at which temperature decreases per update
 * @property coolingExponent Exponential factor affecting cooling rate (higher values = faster cooling at high temperatures)
 * @property ambientTemperature Temperature that the component will eventually reach when cooling
 * @property smokeTransitionTemperature Temperature below which the component starts transitioning to smoke
 */
class TemperatureComponent(
    var minTemperatureEmission: Double = 600.0,
    var maxTemperatureEmission: Double = 9000.0,
    var minTemperature: Double = 300.0,
    var maxTemperature: Double = 10000.0,
    var baseCoolingRate: Double = 3.8,
    var coolingExponent: Double = 1.25,
    var ambientTemperature: Double = 300.0,
    var smokeTransitionTemperature: Double = 1200.0,
    var temperatureStartPoint: Double = 0.5,       // Default: temperature rises from middle
    var temperatureRampWidth: Double = 0.2,        // Width of temperature transition
    var temperatureProfile: TemperatureProfile = TemperatureProfile.SMOOTH_RISE
) : CycledColorSupplier {

    enum class TemperatureProfile {
        /** Smooth exponential rise */
        SMOOTH_RISE,

        /** Linear rise */
        LINEAR,

        /** Sharp rise with steep gradient */
        SHARP_RISE,

        /** Plateau with sudden increase */
        PLATEAU
    }

    override val colorSupplier: () -> Color
        get() = { calculateExplosionColor() }

    override val shapeColorSupplier: PositionColorSupply
        get() = { position, shape ->
            // Calculate temperature based on position and profile
            val normalizedHeight = position.y / shape.maxHeight
            val temperatureMultiplier = calculateTemperatureMultiplier(normalizedHeight)

            // Calculate color with temperature multiplier
            val baseTemp = smoothedTemperature * temperatureMultiplier
            val effectiveTemp = baseTemp.coerceIn(minTemperatureEmission, maxTemperatureEmission)

            if (usePhysicalModel) {
                calculatePhysicalExplosionColor(effectiveTemp)
            } else {
                val ratio = MathFunctions.remap(effectiveTemp, minTemperatureEmission, maxTemperatureEmission, 0.0, 1.0)
                temperatureGradient.getColorAt(ratio)
            }
        }

    /**
     * Calculate temperature multiplier based on height and selected profile
     * @param normalizedHeight Normalized height (0.0 to 1.0)
     * @ return Temperature multiplier (0.0 to 1.0)
     */
    private fun calculateTemperatureMultiplier(normalizedHeight: Double): Double {
        // Adjust for temperature start point and ramp width
        val startPoint = temperatureStartPoint
        val rampWidth = temperatureRampWidth

        return when (temperatureProfile) {
            TemperatureProfile.SMOOTH_RISE -> {
                // Smooth exponential rise
                val normalizedRamp = (normalizedHeight - startPoint) / rampWidth
                when {
                    normalizedHeight < startPoint -> 0.0
                    normalizedHeight > startPoint + rampWidth -> 1.0
                    else -> (1 - exp(-normalizedRamp * 3)).coerceIn(0.0, 1.0)
                }
            }

            TemperatureProfile.LINEAR -> {
                // Linear rise
                when {
                    normalizedHeight < startPoint -> 0.0
                    normalizedHeight > startPoint + rampWidth -> 1.0
                    else -> (normalizedHeight - startPoint) / rampWidth
                }
            }

            TemperatureProfile.SHARP_RISE -> {
                // Sharp rise with steeper gradient
                val normalizedRamp = (normalizedHeight - startPoint) / rampWidth
                when {
                    normalizedHeight < startPoint -> 0.0
                    normalizedHeight > startPoint + rampWidth -> 1.0
                    else -> (normalizedRamp.pow(2)).coerceIn(0.0, 1.0)
                }
            }

            TemperatureProfile.PLATEAU -> {
                // Plateau with sudden increase
                when {
                    normalizedHeight < startPoint -> 0.0
                    normalizedHeight < startPoint + (rampWidth / 2) -> 0.5
                    normalizedHeight > startPoint + rampWidth -> 1.0
                    else -> 0.5 + 0.5 * ((normalizedHeight - (startPoint + rampWidth / 2)) / (rampWidth / 2))
                }
            }
        }
    }

    var temperature: Double = maxTemperature
        set(value) {
            field = value.coerceIn(minTemperature, maxTemperature)
        }

    val color: Color
        get() = calculateExplosionColor()

    // Smoothed temperature for visual transitions (prevents rapid flickering)
    private var smoothedTemperature: Double = temperature
    private val smoothingFactor: Double = 0.15

    /**
     * Implements physically-based cooling following Newton's law of cooling
     * with additional exponential factor for more dramatic effects at high temperatures
     */
    fun coolDown(delta: Float = 1.0f) {
        // Dynamic cooling rate increases with temperature difference from ambient
        val temperatureDifference = temperature - ambientTemperature
        val coolingRate = baseCoolingRate * (temperatureDifference / 1000.0).pow(coolingExponent)

        // Apply cooling with delta time scaling
        temperature -= coolingRate * delta

        // Ensure we don't cool below ambient temperature
        if (temperature < ambientTemperature) {
            temperature = ambientTemperature
        }

        // Update smoothed temperature for visual rendering
        smoothedTemperature += (temperature - smoothedTemperature) * smoothingFactor * delta
    }

    /**
     * Calculate explosion color based on temperature, transitioning from bright yellow
     * to red and finally to smoke shades as it cools
     */
    private fun calculateExplosionColor(): Color {
        // Use smoothed temperature to prevent flickering during rendering
        val effectiveTemp = smoothedTemperature.coerceIn(minTemperatureEmission, maxTemperatureEmission)

        // For higher temperatures, use either physical model or gradient
        return if (usePhysicalModel) {
            calculatePhysicalExplosionColor(effectiveTemp)
        } else {
            // Otherwise use the color gradient approach
            val ratio = MathFunctions.remap(effectiveTemp, minTemperatureEmission, maxTemperatureEmission, 0.0, 1.0)
            temperatureGradient.getColorAt(ratio)
        }
    }

    /**
     * Calculate color using a modified black body approximation that emphasizes
     * yellow to red transition for nuclear explosion effects
     */
    private fun calculatePhysicalExplosionColor(temp: Double): Color {
        var r = 0.0
        var g = 0.0
        var b = 0.0

        // Red component - always high with slight increase at higher temperatures
        r = when {
            temp <= 1500 -> 200.0 + (temp / 1500.0) * 55.0  // Start at 200, go up to 255
            else -> 255.0  // Maximum red for higher temperatures
        }

        // Green component - high at top temperatures (for yellow), decreases as it cools (toward red)
        g = when {
            temp >= 6000 -> 230.0  // Very bright yellow at highest temperatures
            temp >= 4000 -> 180.0 + ((temp - 4000) / 2000.0) * 50.0  // Transition from orange to yellow
            temp >= 2000 -> 100.0 + ((temp - 2000) / 2000.0) * 80.0  // Transition from red to orange
            else -> (temp / 2000.0) * 100.0  // Fade from dark red to red
        }

        // Blue component - minimal except at the very highest temperatures
        b = when {
            temp >= 7000 -> 70.0 + ((temp - 7000) / 2000.0) * 60.0  // Slight blue in the brightest yellow
            temp >= 5000 -> 30.0 + ((temp - 5000) / 2000.0) * 40.0  // Very minimal blue in yellow phase
            else -> 10.0 * (temp / 5000.0)  // Almost no blue in red phase
        }

        // Clamp values to valid range
        r = r.coerceIn(0.0, 255.0)
        g = g.coerceIn(0.0, 255.0)
        b = b.coerceIn(0.0, 255.0)

        // Apply temperature-based intensity scaling for overall brightness
        val intensity = ((temp - minTemperatureEmission) / (maxTemperatureEmission - minTemperatureEmission))
            .coerceIn(0.3, 1.0)  // Minimum intensity increased to 0.3

        return Color.fromRGB(
            (r * intensity).toInt(),
            (g * intensity).toInt(),
            (b * intensity).toInt()
        )
    }

    /**
     * Calculate smoke color based on temperature
     * Transitions from dark red embers to various smoke shades
     */
    private fun calculateSmokeColor(temp: Double): Color {
        // Map temperature to smoke transition (1.0 = just starting smoke, 0.0 = fully cooled)
        val smokeRatio = ((temp - ambientTemperature) / (smokeTransitionTemperature - ambientTemperature))
            .coerceIn(0.0, 1.0)

        // Start with dark red embers that transition to smoke
        val emberFactor = smokeRatio.pow(0.5) // Non-linear transition

        // Calculate smoke colors - darker at first, then lighter as it disperses
        val smokeBase = if (smokeRatio > 0.7) {
            // Initial smoke is darker (dark gray)
            Color.fromRGB(40, 40, 40)
        } else if (smokeRatio > 0.3) {
            // Mid-phase smoke (medium gray)
            Color.fromRGB(80, 80, 80)
        } else {
            // Final smoke is lighter (light gray)
            Color.fromRGB(120, 120, 120)
        }

        // For ember phase, add red and orange glow
        val emberColor = Color.fromRGB(
            (80 + (175 * emberFactor)).toInt(),
            (30 + (60 * emberFactor)).toInt(),
            (20 * emberFactor).toInt()
        )

        // Blend between ember and smoke colors based on temperature
        return blendColors(emberColor, smokeBase, emberFactor)
    }

    /**
     * Blend two colors based on ratio
     * @param color1 First color
     * @param color2 Second color
     * @param ratio Blend ratio (1.0 = all color1, 0.0 = all color2)
     */
    private fun blendColors(color1: Color, color2: Color, ratio: Double): Color {
        val r = color1.red * ratio + color2.red * (1 - ratio)
        val g = color1.green * ratio + color2.green * (1 - ratio)
        val b = color1.blue * ratio + color2.blue * (1 - ratio)

        return Color.fromRGB(r.toInt(), g.toInt(), b.toInt())
    }

    /**
     * Allows switching between physical model and gradient-based approach
     */
    var usePhysicalModel: Boolean = false

    /**
     * Enhanced yellow-to-red gradient for nuclear effects
     * Creates a bright yellow core that transitions to red, then to smoke
     */
    private val temperatureGradient = Gradient(
        arrayOf(
            Color.fromRGB(40, 40, 40),     // Dark smoke (lowest temp)
            Color.fromRGB(70, 70, 70),     // Medium smoke
            Color.fromRGB(100, 100, 100),  // Light smoke
            Color.fromRGB(120, 80, 60),    // Dark ember
            Color.fromRGB(150, 60, 30),    // Ember
            Color.fromRGB(180, 50, 20),    // Deep red
            Color.fromRGB(210, 60, 20),    // Rich red
            Color.fromRGB(230, 80, 30),    // Bright red
            Color.fromRGB(240, 100, 30),   // Red-orange
            Color.fromRGB(250, 130, 40),   // Orange
            Color.fromRGB(250, 160, 50),   // Golden orange
            Color.fromRGB(250, 190, 60),   // Gold
            Color.fromRGB(250, 210, 70),   // Bright gold
            Color.fromRGB(250, 230, 100),  // Yellow-gold
            Color.fromRGB(255, 250, 130)   // Brilliant yellow (highest temp)
        )
    )

    /**
     * Set temperature with optional instant effect (no smoothing)
     * @param value New temperature value
     * @param instant If true, sets both actual and smoothed temperature together
     */
    fun setTemperature(value: Double, instant: Boolean = false) {
        temperature = value
        if (instant) {
            smoothedTemperature = temperature
        }
    }

    /**
     * Heats up the object by the specified amount
     * @param amount Amount to increase temperature
     */
    fun heatUp(amount: Double) {
        temperature += amount
    }

    override fun start() {
        // Initialize smoothed temperature to match starting temperature
        smoothedTemperature = temperature
    }

    override fun update(delta: Float) {
        coolDown(delta)
    }

    override fun stop() {
        // Clean up if needed
    }
}