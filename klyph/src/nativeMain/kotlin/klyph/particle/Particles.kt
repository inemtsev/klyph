package klyph.particle

import klyph.entity.Commands
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.render.DrawSpace
import klyph.render.QuadBuffer
import klyph.render.SPRITE_RECORD_FLOATS
import klyph.scene.SceneStack

/**
 * Queues spawning an entity at ([x], [y]) in world units that carries [emitter] and a [Transform]
 * there, and returns the new entity, as [Commands.spawn] does. Made for a one-shot effect from
 * [ParticleEmitter.burst], which destroys the entity once its particles are gone, but any emitter
 * works; one that keeps emitting keeps the entity until it is destroyed.
 *
 * Has no sample because creating a [klyph.render.Texture] needs a GPU device, which samples cannot
 * open.
 */
public fun Commands.spawnBurst(x: Float, y: Float, emitter: ParticleEmitter): Entity = spawn {
    add(Transform, Transform(x, y))
    add(ParticleEmitter, emitter)
}

/**
 * Advances every [ParticleEmitter] whose entity has a [Transform] by [delta] seconds, emitting at
 * the entity's [WorldTransform] if it has one and at its [Transform] otherwise, and returns the
 * number of live particles across them. When [scenes] is given, an emitter owned by a scene frozen
 * below its top neither advances nor emits, but its particles still count. When [commands] is
 * given, an [EmitterEnd.DESTROY] emitter left empty by its advance queues destroying its entity
 * there. The engine calls it once per frame after [klyph.app.Phase.UPDATE] with `App.scenes` and
 * `App.commands`, then applies the commands. Allocates nothing.
 *
 * @throws IllegalArgumentException if [delta] is negative or not finite.
 */
internal fun World.advanceParticles(delta: Float, scenes: SceneStack? = null, commands: Commands? = null): Int {
    require(delta >= 0f && delta.isFinite()) { "Delta $delta is not a finite number of at least zero." }
    if (count(ParticleEmitter) == 0) return 0
    val frozen = if (scenes != null && scenes.hasFrozen) scenes else null
    var total = 0
    forEach(ParticleEmitter, Transform) { entity, emitter, local ->
        if (frozen != null && frozen.isFrozen(ownerAt(entity.index))) {
            total += emitter.particleCount
            return@forEach
        }
        val hierarchy = getOrNull(entity, WorldTransform)
        total += if (hierarchy != null) {
            emitter.advance(delta, hierarchy.x, hierarchy.y, hierarchy.rotation)
        } else {
            emitter.advance(delta, local.x, local.y, local.rotation)
        }
        if (commands != null && emitter.whenEmpty == EmitterEnd.DESTROY && emitter.isSpent) commands.destroy(entity)
    }
    return total
}

/**
 * Appends one [DrawSpace.WORLD] quad per live particle of every visible [ParticleEmitter] whose
 * entity has a [Transform] to [destination], and returns how many were added. Each quad is centered
 * on its particle, turned by its rotation, in the emitter's layer, with the color and size blended
 * at the particle's age as [ParticleEmitter] describes.
 */
internal fun World.extractParticles(destination: QuadBuffer): Int {
    if (count(ParticleEmitter) == 0) return 0
    val before = destination.count
    forEach(ParticleEmitter, Transform) { _, emitter, _ ->
        if (emitter.isVisible) destination.addParticles(emitter)
    }
    return destination.count - before
}

/** Appends one quad per live particle of [emitter], writing the records straight into the buffer. */
private fun QuadBuffer.addParticles(emitter: ParticleEmitter) {
    val count = emitter.particleCount
    if (count == 0) return
    val region = emitter.region
    val first = reserve(count, region.texture, emitter.layer, DrawSpace.WORLD)
    val records = records
    val width = region.width.toFloat()
    val height = region.height.toFloat()
    val u0 = region.u0
    val v0 = region.v0
    val u1 = region.u1
    val v1 = region.v1
    val start = emitter.startColor
    val red = start.red
    val green = start.green
    val blue = start.blue
    val alpha = start.alpha
    // A null end color is the start color at zero alpha.
    val end = emitter.endColor
    val redChange = if (end == null) 0f else end.red - red
    val greenChange = if (end == null) 0f else end.green - green
    val blueChange = if (end == null) 0f else end.blue - blue
    val alphaChange = if (end == null) -alpha else end.alpha - alpha
    val scale = emitter.startScale
    val scaleChange = emitter.endScale - scale
    val xs = emitter.xs
    val ys = emitter.ys
    val ages = emitter.ages
    val lifetimes = emitter.lifetimes
    val rotations = emitter.rotations
    var base = first * SPRITE_RECORD_FLOATS
    for (i in 0 until count) {
        val lifetime = lifetimes[i]
        // A particle with no lifetime is drawn once, as emitted.
        val t = if (lifetime > 0f) ages[i] / lifetime else 0f
        val size = scale + scaleChange * t
        // The layout QuadBuffer.add writes.
        records[base] = xs[i]
        records[base + 1] = ys[i]
        records[base + 2] = rotations[i]
        records[base + 3] = 0f
        records[base + 4] = width * size
        records[base + 5] = height * size
        // The origin is the center, as QuadBuffer.add puts it.
        records[base + 6] = 0.5f
        records[base + 7] = 0.5f
        records[base + 8] = u0
        records[base + 9] = v0
        records[base + 10] = u1
        records[base + 11] = v1
        records[base + 12] = red + redChange * t
        records[base + 13] = green + greenChange * t
        records[base + 14] = blue + blueChange * t
        records[base + 15] = alpha + alphaChange * t
        base += SPRITE_RECORD_FLOATS
    }
}
