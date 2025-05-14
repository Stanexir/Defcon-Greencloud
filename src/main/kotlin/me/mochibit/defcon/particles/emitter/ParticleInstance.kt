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

package me.mochibit.defcon.particles.emitter

import org.bukkit.Color
import org.bukkit.entity.Player
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sign

/**
 * Base particle instance that manages state and physics
 */
abstract class ParticleInstance(
    private val maxLife: Long,
    val position: Vector3d,
    var velocity: Vector3d = Vector3d(0.0, 0.0, 0.0),
    private var damping: Vector3d = Vector3d(0.0, 0.0, 0.0),
    private var acceleration: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
    var color: Color = Color.WHITE
) {
    var age: Int = 0
    val particleID: Int = ENTITY_ID_COUNTER.getAndIncrement()
    protected val particleUUID: UUID = UUID.randomUUID() // Consider lazily initializing if needed

    private var summoned = false
    private var removed = false

    private val isMoving
        get() = velocity.x != 0.0 || velocity.y != 0.0 || velocity.z != 0.0 ||
                acceleration.x != 0f || acceleration.y != 0f || acceleration.z != 0f

    private val dampingFactor = Vector3f()

    /**
     * Apply force as increased acceleration
     * @param force The force to apply.
     */
    fun applyForce(force: Vector3f) {
        this.acceleration.add(force.x, force.y, force.z)
    }

    /**
     * Apply a damping factor to the particle instance.
     * @param damping The damping factor to apply.
     */
    fun setDamping(damping: Vector3d) {
        this.damping.set(damping.x, damping.y, damping.z)
    }

    /**
     * Apply a force to the particle instance (it's like applying an impulse)
     * @param acceleration The force to apply.
     */
    fun setAcceleration(acceleration: Vector3f) {
        this.acceleration.set(acceleration.x, acceleration.y, acceleration.z)
    }

    /**
     * Increases the velocity of the particle instance.
     * @param velocity The new velocity.
     */
    fun addVelocity(velocity: Vector3d) {
        this.velocity.add(velocity.x, velocity.y, velocity.z)
    }

    /**
     * Update particle physics state
     */
    fun update(delta: Double): Boolean {
        if (isDead() || removed) return false

        // Initialize if needed
        if (!summoned) {
            summoned = true
        }

        // Physics update - only if particle is moving
        if (isMoving) {
            // Apply acceleration to velocity
            if (acceleration.x != 0f || acceleration.y != 0f || acceleration.z != 0f) {
                velocity.add(acceleration)
            }

            // Apply damping to velocity - reuse dampingFactor vector
            if (damping.x != 0.0 || damping.y != 0.0 || damping.z != 0.0) {
                // Calculate damping factor with sign preservation
                dampingFactor.set(
                    if (velocity.x != 0.0) minOf(abs(velocity.x), damping.x) * sign(velocity.x) else 0.0,
                    if (velocity.y != 0.0) minOf(abs(velocity.y), damping.y) * sign(velocity.y) else 0.0,
                    if (velocity.z != 0.0) minOf(abs(velocity.z), damping.z) * sign(velocity.z) else 0.0
                )
                velocity.sub(dampingFactor)
            }

            // Update position based on velocity with boost factor
            position.add(
                (velocity.x * delta),
                (velocity.y * delta),
                (velocity.z * delta)
            )

            // Check if movement has effectively stopped
//            if (abs(velocity.x) < 1e-4f && abs(velocity.y) < 1e-4f && abs(velocity.z) < 1e-4f &&
//                abs(acceleration.x) < 1e-4f && abs(acceleration.y) < 1e-4f && abs(acceleration.z) < 1e-4f
//            ) {
//                velocity.set(0f, 0f, 0f)  // Explicitly zero out for clarity
//            }
        }

        age++

        // Mark as removed if reached end of life
        if (isDead()) {
            removed = true
        }

        return isMoving
    }

    /**
     * Check if particle has reached end of life
     */
    fun isDead() = age >= maxLife

    /**
     * Check if particle is marked as removed
     */
    fun isRemoved() = removed


    abstract fun show(player: Player)
    abstract fun hide(player: Player)
    abstract fun updatePosition(player: Player)

    companion object {
        @JvmStatic
        private val ENTITY_ID_COUNTER = AtomicInteger(ThreadLocalRandom.current().nextInt(10000, Int.MAX_VALUE / 4))
    }
}


fun Vector3d.toPacketWrapper(): com.github.retrooper.packetevents.util.Vector3d {
    return com.github.retrooper.packetevents.util.Vector3d(this.x, this.y, this.z)
}

fun Vector3f.toPacketWrapper(): com.github.retrooper.packetevents.util.Vector3f {
    return com.github.retrooper.packetevents.util.Vector3f(this.x, this.y, this.z)
}

fun Quaternionf.toPacketWrapper(): com.github.retrooper.packetevents.util.Quaternion4f {
    return com.github.retrooper.packetevents.util.Quaternion4f(this.x, this.y, this.z, this.w)
}
