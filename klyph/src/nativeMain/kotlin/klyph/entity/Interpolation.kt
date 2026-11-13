package klyph.entity

import klyph.math.Vec2
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
 * the circle. The picture therefore trails the simulation by up to one fixed step, so a camera
 * that follows the entity reads [drawnPositionOf], the place the picture is at, not the
 * [Transform]. Move marked entities only in `Phase.FIXED_UPDATE`: a change made in another phase
 * is drawn partly blended until the next step. A jump such as a teleport or a respawn is drawn as
 * fast motion across one step unless [snapInterpolation] follows it.
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
 * Returns the position, in world units, at which the renderer draws [entity] this frame, so that a
 * camera or an effect can follow what the player sees.
 *
 * For an [Interpolated] entity without a parent, from its second fixed step since it gained the
 * marker on, this is [alpha] of the way from its position at the start of the last step to its
 * current [Transform], where its sprite and text are drawn. For an entity with a parent it is the
 * position of its [WorldTransform] as of the last [propagateTransforms]. The engine runs that
 * after the fixed steps and again after `LATE_UPDATE`, so call it first if a system has moved the
 * parent since. For every other entity, and for a marked one before its second step, it is the
 * position of its [Transform].
 *
 * Pass `Time.fixedStepAlpha` as [alpha], which must be from 0 to 1, or call
 * `App.drawnPositionOf`, which does. The [entity] must be alive and have a [Transform]. Each call
 * allocates a [Vec2], which is fine for a camera system that calls it once a frame. The shift a
 * `ParallaxLayer` adds to its sprite is not included.
 *
 * @throws IllegalArgumentException if [alpha] is not between 0 and 1.
 * @throws NoSuchElementException if [entity] is not alive or has no [Transform].
 *
 * @sample samples.entity.Interpolations.drawnPositionOf
 */
public fun World.drawnPositionOf(entity: Entity, alpha: Float): Vec2 {
    require(alpha in 0f..1f) { "Alpha $alpha is not between 0 and 1." }
    val local = get(entity, Transform)
    val hierarchy = getOrNull(entity, WorldTransform)
    val previous = if (hierarchy == null) getOrNull(entity, PreviousTransform) else null
    return drawnTransform(local, hierarchy, previous, alpha) { x, y, _, _, _ -> Vec2(x, y) }
}

/**
 * Makes the next frame draw [entity] exactly at its current [Transform], with no blend from where
 * it was a step ago.
 *
 * An [Interpolated] entity is drawn between its last two fixed steps, so a jump such as a teleport
 * or a respawn is drawn as fast motion across the screen over the next step. Move the [Transform]
 * first and call this after. It copies the entity's current position, rotation and scale over the
 * copy the engine saved at the start of the step, so called before the move it would copy the old
 * place and the entity would still sweep. The entity blends again from the next fixed step on.
 *
 * Does nothing when there is no saved copy to change: for an entity that is not [Interpolated] or
 * not alive, that has no [Transform], or that gained the marker after the last fixed step began.
 * The last is drawn at its [Transform] anyway.
 *
 * Snapping also ends the entity's first step: from the next step on it is blended as usual, from
 * the snapped place.
 *
 * @sample samples.entity.Interpolations.snapInterpolation
 */
public fun World.snapInterpolation(entity: Entity) {
    val previous = getOrNull(entity, PreviousTransform) ?: return
    val local = getOrNull(entity, Transform) ?: return
    previous.copyFrom(local)
}

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
 * Calls [use] with the position, rotation and scale an entity is drawn at and returns its result:
 * those of [hierarchy] if it is not `null`, else [alpha] of the way from [previous] to [local] if
 * [previous] is not `null` and is past its first step, else those of [local].
 *
 * The sprite pass, the text pass and [drawnPositionOf] all draw through this one function, so the
 * place a camera reads is the place the picture is. It is inline and allocates nothing.
 */
internal inline fun <R> drawnTransform(
    local: Transform,
    hierarchy: WorldTransform?,
    previous: PreviousTransform?,
    alpha: Float,
    use: (x: Float, y: Float, rotation: Float, scaleX: Float, scaleY: Float) -> R,
): R {
    val x: Float
    val y: Float
    val rotation: Float
    val scaleX: Float
    val scaleY: Float
    if (hierarchy != null) {
        x = hierarchy.x
        y = hierarchy.y
        rotation = hierarchy.rotation
        scaleX = hierarchy.scaleX
        scaleY = hierarchy.scaleY
    } else if (previous != null && !previous.isFirstStep) {
        x = previous.x + alpha * (local.x - previous.x)
        y = previous.y + alpha * (local.y - previous.y)
        rotation = blendRotation(previous.rotation, local.rotation, alpha)
        scaleX = previous.scaleX + alpha * (local.scaleX - previous.scaleX)
        scaleY = previous.scaleY + alpha * (local.scaleY - previous.scaleY)
    } else {
        x = local.x
        y = local.y
        rotation = local.rotation
        scaleX = local.scaleX
        scaleY = local.scaleY
    }
    return use(x, y, rotation, scaleX, scaleY)
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
