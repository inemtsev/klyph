package klyph.entity

import kotlin.math.PI
import kotlin.math.floor

/**
 * Marks an entity whose sprite is drawn between its last two fixed steps instead of at its
 * current [Transform], so motion integrated in `Phase.FIXED_UPDATE` looks smooth at any frame
 * rate.
 *
 * A marker carries no data, so the component is this object itself, and adding it allocates
 * nothing: `world.add(entity, Interpolated, Interpolated)`. Remove it with
 * `world.remove(entity, Interpolated)`.
 *
 * At the start of every fixed step the engine copies the [Transform] of each marked entity. When
 * the frame is drawn, the sprite's position and scale are blended linearly from that copy toward
 * the current [Transform] by `Time.fixedStepAlpha`, and its rotation along the shorter way round
 * the circle. The picture therefore trails the simulation by up to one fixed step. Move marked
 * entities only in `Phase.FIXED_UPDATE`: a change made in another phase is drawn partly blended
 * until the next step, and a jump such as a teleport is drawn as fast motion across one step.
 *
 * An entity is blended only from its second fixed step since it gained the marker on: until then
 * there is no earlier step to blend from, so it is drawn at its current [Transform], and an entity
 * spawned at one place and moved to its start in its first step does not sweep across the screen.
 * The same goes for every entity on the first frame before any step has run.
 *
 * Only entities without a parent are blended. An entity in a hierarchy is drawn at its current
 * [WorldTransform] even if it is marked, because world transforms are computed once per frame
 * from the current transforms.
 *
 * @sample samples.entity.Interpolations.interpolated
 */
public object Interpolated : ComponentType<Interpolated>("Interpolated")

/**
 * The [Transform] of an [Interpolated] entity at the start of the last fixed step. [isFirstStep]
 * is `true` while that step is the first the entity has been through with the marker, when the
 * copy is only where the entity was spawned and drawing blends from it would sweep the entity
 * from there; the renderer then draws the current [Transform].
 */
internal class PreviousTransform(
    var x: Float,
    var y: Float,
    var rotation: Float,
    var scaleX: Float,
    var scaleY: Float,
    var isFirstStep: Boolean = false,
) {
    /** Sets every field to the matching one of [transform] and clears [isFirstStep]. */
    fun copyFrom(transform: Transform) {
        x = transform.x
        y = transform.y
        rotation = transform.rotation
        scaleX = transform.scaleX
        scaleY = transform.scaleY
        isFirstStep = false
    }

    override fun toString(): String =
        "PreviousTransform(x=$x, y=$y, rotation=$rotation, scale=${scaleX}x$scaleY, firstStep=$isFirstStep)"

    /** Component key for [PreviousTransform]. */
    companion object : ComponentType<PreviousTransform>("PreviousTransform")
}

/**
 * Copies the [Transform] of every entity that has [Interpolated] and a [Transform] into its
 * [PreviousTransform], creating the copy the first time. Drops the copies of entities that lost
 * either component since the last call, so they are no longer blended.
 *
 * The loop calls it at the start of each fixed step, before the `FIXED_UPDATE` systems run.
 */
internal fun World.recordPreviousTransforms() {
    val marked = storeOrNull(Interpolated)
    if (marked == null || marked.size == 0) {
        storeOrNull(PreviousTransform)?.let { if (it.size > 0) clearPreviousTransforms(it) }
        return
    }
    val previous = store(PreviousTransform)
    var recorded = 0
    forEach(Interpolated, Transform) { entity, _, transform ->
        // A live entity's slot in the sparse array is its own: destroying an entity removes all of
        // its components, so the generation check of getOrNull is not needed here.
        val slot = previous.sparse[entity.index]
        if (slot >= 0) {
            previous.items.itemAt<PreviousTransform>(slot).copyFrom(transform)
        } else {
            previous.set(entity, PreviousTransform(transform.x, transform.y, transform.rotation, transform.scaleX, transform.scaleY, isFirstStep = true))
        }
        recorded++
    }
    // Every recorded entity has a copy, so more copies than records means some are stale.
    if (previous.size > recorded) dropStalePreviousTransforms(previous, marked)
}

/**
 * Reads a store item without a type check. A non-reified cast is erased, so this skips the check
 * that `as PreviousTransform` would emit for every entity.
 */
@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
private inline fun <T> Array<Any?>.itemAt(slot: Int): T = this[slot] as T

/** Removes every copy in [previous]. */
private fun clearPreviousTransforms(previous: ComponentStore<PreviousTransform>) {
    while (previous.size > 0) previous.remove(Entity(previous.entities[previous.size - 1]))
}

/** Removes the copies in [previous] whose entity no longer has [Interpolated] or a [Transform]. */
private fun World.dropStalePreviousTransforms(previous: ComponentStore<PreviousTransform>, marked: ComponentStore<Interpolated>) {
    val transforms = storeOrNull(Transform)
    var slot = previous.size - 1
    // Walking backwards keeps the slots still to visit in place when remove moves the last one down.
    while (slot >= 0) {
        val entity = Entity(previous.entities[slot])
        if (marked.getOrNull(entity) == null || transforms?.getOrNull(entity) == null) previous.remove(entity)
        slot--
    }
}

/**
 * Returns the rotation [alpha] of the way from [from] to [to], in radians, turning the shorter
 * way round the circle. [alpha] 0 gives [from]; 1 gives an angle equal to [to] up to whole turns.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun blendRotation(from: Float, to: Float, alpha: Float): Float {
    var difference = to - from
    // Rotations between two steps rarely differ by more than half a turn, so the common case
    // skips the division.
    if (difference > PI_FLOAT || difference < -PI_FLOAT) {
        difference -= TWO_PI * floor((difference + PI_FLOAT) / TWO_PI)
    }
    return from + alpha * difference
}

private const val PI_FLOAT: Float = PI.toFloat()
private const val TWO_PI: Float = (2.0 * PI).toFloat()
