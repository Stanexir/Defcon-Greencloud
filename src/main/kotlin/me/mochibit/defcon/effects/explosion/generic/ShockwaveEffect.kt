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
package me.mochibit.defcon.effects.explosion.generic

import me.mochibit.defcon.Defcon.Logger.err
import me.mochibit.defcon.effects.AnimatedEffect
import me.mochibit.defcon.effects.ParticleComponent
import me.mochibit.defcon.extensions.toTicks
import me.mochibit.defcon.particles.emitter.ParticleEmitter
import me.mochibit.defcon.particles.emitter.RingSurfaceShape
import me.mochibit.defcon.particles.mutators.SimpleFloorSnap
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import org.bukkit.Color
import org.bukkit.Location
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class ShockwaveEffect(
    private val center: Location,
    private val shockwaveRadius: Int,
    private val initialRadius: Int = 0,
    private val expansionSpeed: Float = 50f,
    duration: Duration = ((shockwaveRadius - initialRadius) / expansionSpeed).roundToInt().seconds,
    private val particleDensityFactor: Float = 1.0f, // Allows fine-tuning of particle density
) : AnimatedEffect(duration.toTicks()) {

    private val shockwave = ParticleComponent(
        ParticleEmitter(
            center, 300.0,
            maxParticlesInitial = 2000, // Start with a higher initial value
            emitterShape = RingSurfaceShape(
                ringRadius = initialRadius.toFloat(),
                tubeRadius = 1f,
            ),
            shapeMutator = SimpleFloorSnap(center),
        ),
    ).addSpawnableParticle(
        ExplosionDustParticle().apply {
            defaultColor = Color.WHITE
            scale(60f, 50f, 60f)
            maxLife = 20
        }
    ).applyRadialVelocityFromCenter(
        Vector3f(1f, .0f, 1f)
    )

    private val shockwaveShape = shockwave.shape

    init {
        effectComponents.add(shockwave)

        // Initial density and particle count calculation
        updateDensityAndParticles()
        shockwave.particleRate(1000)
    }

    /**
     * Updates both density and max particles based on the current radius
     * This unified approach ensures consistent behavior
     */

    private val baseScale = Vector3f(60f, 40f, 60f)

    private fun updateDensityAndParticles() {
        try {
            // Calculate circumference to determine appropriate particle count
            val circumference = 2 * PI * shockwaveShape.ringRadius

            // Set emitter spawn rate in particles per second

            // Calculate appropriate particle count based on circumference with improved scaling
            // Increased multiplier from 3.5 to 5.0 for better density
            val particlesNeeded = (circumference * particleDensityFactor).roundToInt()

            val newMaxParticles = max(500, particlesNeeded)

            // Update particle scale

            shockwave.maxParticles = newMaxParticles

        } catch (e: Exception) {
            err("Error updating shockwave parameters: ${e.message}")
        }
    }

    override fun animate(delta: Float) {
        try {
            // Prevent exceeding maximum radius
            if (shockwaveShape.ringRadius >= shockwaveRadius) {
                return
            }

            val shockwaveDelta = delta * expansionSpeed

            // Calculate new radius with bounds checking
            val newRadius = min(
                shockwaveRadius.toFloat(),
                shockwaveShape.ringRadius + shockwaveDelta
            )

            // Update radius
            shockwaveShape.ringRadius = newRadius

            updateDensityAndParticles()

        } catch (e: Exception) {
            err("Error in shockwave animation: ${e.message}")
        }
    }
}
