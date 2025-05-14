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

package me.mochibit.defcon.effects.explosion.nuclear

import me.mochibit.defcon.effects.AnimatedEffect
import me.mochibit.defcon.effects.ParticleComponent
import me.mochibit.defcon.explosions.ExplosionComponent
import me.mochibit.defcon.particles.emitter.ParticleEmitter
import me.mochibit.defcon.particles.emitter.RingSurfaceShape
import me.mochibit.defcon.particles.emitter.SphereSurfaceShape
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import org.bukkit.Color
import org.bukkit.Location
import org.joml.Vector3f
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class CondensationCloudVFX(
    private val nuclearComponent: ExplosionComponent,
    position: Location,
    duration: Duration = 40.seconds,
) :
    AnimatedEffect(maxAliveDuration = duration) {
    private var riseSpeed = 6.0f
    private var ringRiseSpeed = 3.0f
    private var expandSpeed = 10.0f


    private val condensationRing = ParticleComponent(
        particleEmitter = ParticleEmitter(
            position = position,
            3000.0,
            emitterShape = RingSurfaceShape(
                ringRadius = 110.0f,
                tubeRadius = 5.0f,
            )
        )
    ).addSpawnableParticle(
        ExplosionDustParticle().apply {
            scale(60.0f, 60.0f, 60.0f)
            defaultColor = Color.WHITE
        }
    )
        .applyRadialVelocityFromCenter(Vector3f(6.0f, 0.0f, 6.0f))
        .translate(Vector3f(0.0f, 110.0f, 0.0f))

    private val condensationRingShape = condensationRing.shape

    private val condensationDome = ParticleComponent(
        particleEmitter = ParticleEmitter(
            position = position,
            500.0,
            emitterShape = SphereSurfaceShape(
                xzRadius = 110.0f,
                yRadius = 110.0f,
                skipBottomFace = true,
                minY = 0.0
            )
        )
    ).addSpawnableParticle(
        ExplosionDustParticle().apply {
            scale(80.0f, 80.0f, 80.0f)
            defaultColor = Color.WHITE
        }
    )
        .applyRadialVelocityFromCenter(Vector3f(6.0f, 0.0f, 6.0f))
        .translate(
            Vector3f(0.0f, 150.0f, 0.0f)
        )

    private val condensationDomeShape = condensationDome.shape

    init {
        effectComponents = mutableListOf(
            condensationRing,
            condensationDome
        )
    }

    override fun animate(delta: Float) {
        val deltaMovement = riseSpeed * delta

        val deltaRingMovement = ringRiseSpeed * delta
        val deltaExpansion = expandSpeed * delta


        condensationRing.translate(Vector3f(0.0f, deltaRingMovement, 0.0f))

        condensationRingShape.ringRadius += deltaExpansion

        condensationDome.translate(Vector3f(0.0f, deltaMovement, 0.0f))

        condensationDomeShape.apply {
            xzRadius += deltaExpansion
            yRadius = (yRadius + deltaExpansion).coerceIn(0.0f, 160.0f)
        }
    }

}