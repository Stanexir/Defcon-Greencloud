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

package me.mochibit.defcon.explosions.processor

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.*
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger.err
import me.mochibit.defcon.Defcon.Logger.info
import me.mochibit.defcon.effects.explosion.generic.ShockwaveEffect
import me.mochibit.defcon.explosions.effects.CameraShake
import me.mochibit.defcon.explosions.effects.CameraShakeOptions
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Simulates a shockwave from an explosion that affects entities within range
 *
 * @param center The center location of the explosion
 * @param shockwaveHeight The maximum height above center that the shockwave reaches
 * @param baseShockwaveGroundPenetration How far below center the shockwave penetrates (before terrain factors)
 * @param shockwaveRadius The maximum radius the shockwave will reach
 * @param initialRadius The starting radius of the shockwave
 * @param shockwaveSpeed The speed at which the shockwave expands in blocks per second
 * @param aboveSeaLevelPenetrationFactor Multiplier for ground penetration above sea level
 * @param belowSeaLevelPenetrationFactor Multiplier for ground penetration below sea level
 * @param entitySearchRadius Maximum distance from players to search for additional entities
 * @param entitySearchInterval How often to refresh the entity search in seconds
 */
class EntityShockwave(
    private val center: Location,
    private val shockwaveHeight: Int,
    private val baseShockwaveGroundPenetration: Int,
    private val shockwaveRadius: Int,
    private val initialRadius: Int = 0,
    private val shockwaveSpeed: Float = 50f,
    private val aboveSeaLevelPenetrationFactor: Float = 1.5f,
    private val belowSeaLevelPenetrationFactor: Float = 0.8f,
    private val entitySearchRadius: Double = 50.0,
    private val entitySearchInterval: Long = 30L,
    private val baseDamage: Double = 80.0
) {
    // Visual effect for the shockwave
    private val shockwaveEffect = ShockwaveEffect(
        center,
        shockwaveRadius,
        initialRadius,
        shockwaveSpeed,
    )

    // How long the shockwave will last
    val duration = ((shockwaveRadius - initialRadius) / shockwaveSpeed).toInt().seconds

    // Track elapsed time for radius calculations
    private val secondElapsed = AtomicInteger(0)

    // Track which entities have been processed to avoid duplicates
    private val processedEntities = ConcurrentHashMap.newKeySet<UUID>()

    // Cache of entities by chunk to improve lookup performance
    private val entityCache = ConcurrentHashMap<ChunkCoordinate, MutableSet<LivingEntity>>()

    // Calculate actual ground penetration based on sea level
    private val shockwaveGroundPenetration: Int by lazy {
        val seaLevel = center.world.seaLevel
        if (center.y > seaLevel) {
            (baseShockwaveGroundPenetration * aboveSeaLevelPenetrationFactor).toInt()
        } else {
            (baseShockwaveGroundPenetration * belowSeaLevelPenetrationFactor).toInt()
        }
    }

    private val computationDispatcher = Dispatchers.Default.limitedParallelism(4)

    /**
     * Starts the shockwave processing
     */
    suspend fun process() = coroutineScope {
        try {
            // Initialize the visual effect
            shockwaveEffect.instantiate()

            // Timer job to track elapsed time
            val timerJob = launch(Dispatchers.IO) {
                while (isActive && secondElapsed.get() < duration.inWholeSeconds) {
                    delay(1.seconds)
                    secondElapsed.incrementAndGet()
                }
            }

            // Entity discovery job
            val entityDiscoveryJob = launch(Dispatchers.IO) {
                while (isActive && secondElapsed.get() < duration.inWholeSeconds) {
                    try {
                        refreshEntityCache()
                        delay(entitySearchInterval.seconds)
                    } catch (e: Exception) {
                        err("Error refreshing entity cache: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            // Main shockwave processing job
            val processingJob = launch(computationDispatcher) {
                while (isActive && secondElapsed.get() < duration.inWholeSeconds) {
                    try {
                        processShockwaveEffects()
                        delay(500.milliseconds)  // Process twice per second
                    } catch (e: Exception) {
                        err("Error processing shockwave effects: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            // Wait for the shockwave to complete
            timerJob.join()

            // Cancel all jobs when done
            entityDiscoveryJob.cancel()
            processingJob.cancel()
        } catch (e: Exception) {
            err("Fatal error in shockwave processing: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Refreshes the cache of nearby entities
     */
    private suspend fun refreshEntityCache() = withContext(Dispatchers.IO) {
        // Clear the previous cache
        entityCache.clear()

        // Start with all players in the world
        val players = center.world.players.filter { it.isOnline }

        // Add all players to the cache
        players.forEach { player ->
            val chunk = ChunkCoordinate(player.location.chunk.x, player.location.chunk.z)
            entityCache.computeIfAbsent(chunk) { mutableSetOf() }.add(player)
        }

        // For each player, find nearby entities and add them to the cache
        players.forEach { player ->
            val nearbyEntities = withContext(Defcon.instance.minecraftDispatcher) {
                player.location.world.getNearbyEntities(
                    player.location,
                    entitySearchRadius,
                    entitySearchRadius,
                    entitySearchRadius
                ).filterIsInstance<LivingEntity>()
            }

            // Group entities by chunk for more efficient lookup
            nearbyEntities.forEach { entity ->
                if (entity !is Player) {  // Skip players as they're already added
                    val chunk = ChunkCoordinate(entity.location.chunk.x, entity.location.chunk.z)
                    entityCache.computeIfAbsent(chunk) { mutableSetOf() }.add(entity)
                }
            }
        }
    }

    /**
     * Process all entities that should be affected by the shockwave
     */
    private suspend fun processShockwaveEffects() {
        val currentShockwaveRadius = calculateCurrentRadius()

        // Convert the radius to chunks for more efficient entity filtering
        val chunkRadius = (currentShockwaveRadius / 16.0).toInt() + 1

        // Get relevant chunks within the current radius
        val centerChunkX = center.chunk.x
        val centerChunkZ = center.chunk.z

        // Process entities chunk by chunk
        for (x in -chunkRadius..chunkRadius) {
            for (z in -chunkRadius..chunkRadius) {
                val chunk = ChunkCoordinate(centerChunkX + x, centerChunkZ + z)
                val entitiesInChunk = entityCache[chunk] ?: continue

                // Process each entity in the chunk
                entitiesInChunk.forEach { entity ->
                    try {
                        if (entity.isValid && !processedEntities.contains(entity.uniqueId)) {
                            if (isEntityAffectedByShockwave(entity, currentShockwaveRadius)) {
                                processEntity(entity, currentShockwaveRadius)
                            }
                        }
                    } catch (e: Exception) {
                        err("Error processing entity ${entity.uniqueId}: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Determines if an entity should be affected by the current shockwave
     */
    private fun isEntityAffectedByShockwave(entity: Entity, currentRadius: Int): Boolean {
        val entityLocation = entity.location
        val distanceFromCenter = entityLocation.distance(center)

        // Check horizontal distance
        if (distanceFromCenter > currentRadius) return false

        // Check vertical range
        val entityHeight = entityLocation.y
        return !(entityHeight < center.y - shockwaveGroundPenetration ||
                entityHeight > center.y + shockwaveHeight)
    }

    /**
     * Calculate the current radius of the shockwave based on elapsed time
     */
    private fun calculateCurrentRadius(): Int {
        return min(
            shockwaveRadius.toFloat(),
            initialRadius + (shockwaveSpeed * secondElapsed.get())
        ).toInt()
    }

    /**
     * Process an affected entity by applying knockback and damage
     */
    private suspend fun processEntity(entity: Entity, currentShockwaveRadius: Int) {
        // Mark this entity as processed
        processedEntities.add(entity.uniqueId)

        val entityDistanceFromCenter = entity.location.distance(center)

        // Calculate explosion power based on distance from center
        val explosionPowerNormalized = ((shockwaveRadius - entityDistanceFromCenter) / shockwaveRadius)
            .coerceIn(0.0, 1.0)
            .toFloat()

        // Apply knockback and damage
        applyExplosionEffects(entity, explosionPowerNormalized)
    }

    /**
     * Apply explosion effects (knockback, damage, visual/audio effects)
     */
    private suspend fun applyExplosionEffects(
        entity: Entity,
        explosionPower: Float
    ) = withContext(Defcon.instance.minecraftDispatcher) {
        try {
            // Calculate knockback vector
            val knockbackVector = calculateKnockbackVector(entity, explosionPower)

            // Apply damage to living entities
            if (entity is LivingEntity) {
                val scaledDamage = baseDamage * explosionPower.coerceIn(0f, 1f)
                entity.damage(scaledDamage)
            }

            // Apply knockback
            entity.velocity = knockbackVector

            // Apply camera shake and sound effects for players
            if (entity is Player) {
                ExplosionSoundManager.playSounds(ExplosionSoundManager.DefaultSounds.ShockwaveHitSound, entity)
                CameraShake(
                    entity,
                    CameraShakeOptions(
                        magnitude = 2.6f,
                        decay = 0.04f,
                        pitchPeriod = 3.7f * explosionPower,
                        yawPeriod = 3.0f * explosionPower
                    )
                )
            }
        } catch (e: Exception) {
            err("Error applying explosion effects to entity ${entity.uniqueId}: ${e.message}")
        }
    }

    /**
     * Calculate the knockback vector for an entity
     */
    private fun calculateKnockbackVector(entity: Entity, explosionPower: Float): Vector {
        val entityLoc = entity.location

        // Calculate direction vector from explosion to entity
        val dx = entityLoc.x - center.x
        val dy = entityLoc.y - center.y
        val dz = entityLoc.z - center.z

        // Calculate horizontal distance (avoid division by zero)
        val horizontalDistance = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)

        // Scale knockback power by explosion power
        val knockbackPower = explosionPower * 2

        // Calculate knockback components
        val knockbackX = knockbackPower * (dx / horizontalDistance)
        val knockbackY = if (dy != 0.0) knockbackPower / (abs(dy) * 2) + 1.2 else 1.2
        val knockbackZ = knockbackPower * (dz / horizontalDistance)

        return Vector(knockbackX, knockbackY, knockbackZ)
    }

    /**
     * Data class to represent chunk coordinates for caching entities
     */
    private data class ChunkCoordinate(val x: Int, val z: Int)
}