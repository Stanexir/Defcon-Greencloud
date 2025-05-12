/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024-2025 mochibit.
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
import me.mochibit.defcon.effects.TemperatureComponent
import me.mochibit.defcon.explosions.ExplosionComponent
import me.mochibit.defcon.particles.emitter.CylinderShape
import me.mochibit.defcon.particles.emitter.ParticleEmitter
import me.mochibit.defcon.particles.emitter.SphereShape
import me.mochibit.defcon.particles.emitter.SphereSurfaceShape
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import org.bukkit.Color
import org.bukkit.Location
import org.joml.Vector3f
import kotlin.time.Duration.Companion.seconds

class NuclearExplosionVFX(private val nuclearComponent: ExplosionComponent, val center: Location) :
    AnimatedEffect(3600) {
    private val maxHeight = 250.0
    private var currentHeight = 0.0f
    private var riseSpeed = 5.0f

    private val coreCloud = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = SphereShape(
                xzRadius = 30.0f,
                yRadius = 50.0f
            ),
        ),
        TemperatureComponent()
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                scale(30.0f, 30.0f, 30.0f)
                initialVelocity(0.0, 1.0, 0.0)
            }
    )


    private val secondaryCloud = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = SphereSurfaceShape(
                xzRadius = 50.0f,
                yRadius = 50.0f,
            ),
        ),
        TemperatureComponent(baseCoolingRate = 15.0)
    ).addSpawnableParticle(
        ExplosionDustParticle().apply {
            scale(45.0f, 45.0f, 45.0f)
            initialVelocity(0.0, .8, 0.0)
        }
    )

    private val tertiaryCloud = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = SphereSurfaceShape(
                xzRadius = 70.0f,
                yRadius = 70.0f,
            ),
        ),
        TemperatureComponent(baseCoolingRate = 40.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                scale(50.0f, 50.0f, 50.0f)
                initialVelocity(0.0, .6, 0.0)
            }
    )

    private val quaterniaryCloud = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = SphereSurfaceShape(
                xzRadius = 90.0f,
                yRadius = 60.0f,
            ),
        ),
        TemperatureComponent(baseCoolingRate = 50.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                scale(55.0f, 55.0f, 55.0f)
                initialVelocity(0.0, -1.0, 0.0)
            }
    )
        .translate(Vector3f(0.0f, -5.0f, 0.0f))

    private val coreNeck = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = CylinderShape(
                radiusX = 30.0f,
                radiusZ = 30.0f,
                height = 60.0f,
            ),
        ),
        TemperatureComponent(baseCoolingRate = 8.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                initialVelocity(0.0, -1.2, 0.0)
            }
    )
        .translate(Vector3f(0.0f, -30.0f, 0.0f))

    private val neckSkirt = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = SphereShape(
                xzRadius = 40.0f,
                yRadius = 70.0f,
                minY = -15.0
            ),
        ),
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                defaultColor = Color.GRAY
                initialVelocity(0.0, -5.0, 0.0)
            }
    )
        .apply {
            visible = false
        }
        .translate(Vector3f(0.0f, -60.0f, 0.0f))
        .setVisibilityAfterDelay(true, 50.seconds)
        .applyRadialVelocityFromCenter(Vector3f(5.0f, 0f, 5.0f))

    private val stem = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = CylinderShape(
                radiusX = 15.0f,
                radiusZ = 15.0f,
                height = 1f,
            ),
        ),
        TemperatureComponent(baseCoolingRate = 30.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                scale(40.0f, 40.0f, 40.0f)
                initialVelocity(0.0, 1.0, 0.0)
            }
    ).apply {
        positionBasedColoring = true
    }

    private val stemShape = stem.shape
    private val neckConeShape = neckSkirt.shape


    init {
        effectComponents.addAll(
            listOf(
                coreCloud,
                coreNeck,
                secondaryCloud,
                tertiaryCloud,
                quaterniaryCloud,
                neckSkirt,
                stem
            )
        )

    }

    override fun animate(delta: Float) {
        processRise(delta)
    }


    private fun processRise(delta: Float) {
        if (currentHeight > maxHeight) return
        val deltaMovement = riseSpeed * delta
        val movementVector = Vector3f(0.0f, deltaMovement, 0.0f)
        // Elevate the sphere using transform translation
        coreCloud.translate(movementVector)
        coreNeck.translate(movementVector)
        secondaryCloud.translate(movementVector)
        tertiaryCloud.translate(movementVector)
        quaterniaryCloud.translate(movementVector)
        neckSkirt.translate(movementVector)
        currentHeight += deltaMovement

        // Gradually increase the displayed height of the cone to simulate the nuke skirt
        if (neckSkirt.visible)
            neckConeShape.maxY += deltaMovement / 5

        stemShape.height = (currentHeight - 70)
    }

}