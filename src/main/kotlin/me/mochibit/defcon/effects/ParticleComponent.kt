package me.mochibit.defcon.effects

import me.mochibit.defcon.particles.emitter.EmitterShape
import me.mochibit.defcon.particles.emitter.ParticleEmitter
import me.mochibit.defcon.particles.templates.AbstractParticle
import me.mochibit.defcon.threading.scheduling.runLater
import org.joml.Matrix4d
import org.joml.Vector3f
import kotlin.time.Duration

/**
 * Represents an effect component that manages particle emission and transformation.
 */
open class ParticleComponent<T: EmitterShape>(
    private val particleEmitter: ParticleEmitter<T>,
    private val colorSupplier: CycledColorSupplier? = null,
) : EffectComponent {
    var positionBasedColoring: Boolean = false

    // Matrix transformation for particleEmitter
    val transform: Matrix4d
        get() = particleEmitter.transform

    var visible: Boolean
        get() = particleEmitter.visible
        set(value) {
            particleEmitter.visible = value
        }

    val shape: T
        get() = particleEmitter.emitterShape

    var density
        get() = particleEmitter.getShape().density
        set(value) {
            particleEmitter.getShape().density = value
        }


    fun adaptAtLeast(particleCount: Int) {
        particleEmitter.setSpawnRate(particleCount)
        particleEmitter.adaptParticleCount(particleCount)
    }

    /**
     * Adds a spawnable particle with optional color supplier attachment.
     */
    fun addSpawnableParticle(particle: AbstractParticle): ParticleComponent<T> {
        particleEmitter.spawnableParticles.add(particle)
        return this
    }

    fun particleRate(particlesPerSecond: Int) = apply {
        particleEmitter.setSpawnRate(particlesPerSecond)
    }

    fun addSpawnableParticles(
        particles: List<AbstractParticle>,
        attachColorSupplier: Boolean = false
    ): ParticleComponent<T> {
        particleEmitter.spawnableParticles.addAll(particles)
        return this
    }

    /**
     * Set the visibility of the particle component after a specified delay.
     */
    fun setVisibilityAfterDelay(visible: Boolean, delay: Duration) = apply {
        runLater(delay) {
            particleEmitter.visible = visible
        }
    }

    /**
     * Translates the particle emitter by a specified vector.
     */
    fun translate(translation: Vector3f): ParticleComponent<T> {
        transform.translate(translation, transform)
        return this
    }

    /**
     * Rotates the particle emitter around an axis by a specified angle.
     */
    fun rotate(axis: Vector3f, angle: Double): ParticleComponent<T> {
        transform.rotate(angle, axis, transform)
        return this
    }

    /**
     * Apply radial velocity to particles moving them from the center outward.
     */
    fun applyRadialVelocityFromCenter(velocity: Vector3f) = apply {
        particleEmitter.radialVelocity.set(velocity)
    }

    // Lifecycle management for starting, updating, and stopping the particle component.
    override fun start() {
        colorSupplier?.let {
            if (positionBasedColoring) {
                particleEmitter.positionColorSupply = it.shapeColorSupplier
            } else
                particleEmitter.colorSupply = it.colorSupplier
            it.start()
        }
        particleEmitter.start()
    }

    override fun update(delta: Float) {
        colorSupplier?.update(delta)
        particleEmitter.update(delta)
    }

    override fun stop() {
        colorSupplier?.stop()
        particleEmitter.stop()
    }
}
