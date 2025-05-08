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

package me.mochibit.defcon.vertexgeometry.particle

import me.mochibit.defcon.particles.emitter.EmitterShape
import me.mochibit.defcon.particles.emitter.ParticleEmitter
import me.mochibit.defcon.particles.templates.AbstractParticle
import me.mochibit.defcon.vertexgeometry.VertexShapeBuilder
import me.mochibit.defcon.vertexgeometry.vertexes.SpawnableVertex
import me.mochibit.defcon.vertexgeometry.vertexes.Vertex
import org.bukkit.*
import kotlin.random.Random

class ParticleShape<T: EmitterShape>(
    var particle: AbstractParticle,
    val emitter : ParticleEmitter<T>,
    shapeBuilder: VertexShapeBuilder, spawnPoint: Location
) : AbstractShape(shapeBuilder, spawnPoint) {
    override fun buildVertexes(): Array<Vertex> {
        return shapeBuilder.build().map { SpawnableVertex(it) }.toTypedArray()
    }

    fun emit(chance: Double = 0.8, repetitions: Int = 10) {
        if (!visible) return
        if (vertexes.isEmpty()) return
        //Call draw (emitter), get a random vertex and draw it a random number of times consecutively
        val randomCount = Random.nextInt(1, repetitions) * (if (Random.nextDouble() < chance) 1 else 0)
        for (i in 0 until randomCount) {
            draw(vertexes.random())
        }
    }

    override fun effectiveDraw(vertex: Vertex, worldName: String) {
        if (vertex !is SpawnableVertex) return
        if (vertex.spawnTime != 0L && System.currentTimeMillis() - vertex.spawnTime < particle.particleProperties.maxLife) return
        //emitter.spawnParticle(particle, vertex.globalPosition, worldName)
        vertex.spawnTime = System.currentTimeMillis()
    }

}