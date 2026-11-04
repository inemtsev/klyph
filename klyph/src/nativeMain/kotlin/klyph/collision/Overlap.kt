package klyph.collision

import klyph.entity.Entity

/**
 * Published once per frame for every pair of entities whose [Collider]s overlap and whose layers
 * match, after the last fixed update step of the frame. [a] is the entity with the smaller
 * [Entity.index], so a pair always arrives in the same order.
 *
 * The pair is reported when either collider's mask contains a layer of the other, so a handler
 * that only cares about one direction, such as bullets hitting enemies, checks the layers itself.
 * An entity may be destroyed by a handler that ran earlier, so check `World.isAlive` before
 * touching its components.
 *
 * @sample samples.collision.CollisionQueries.detect
 */
public class Overlap(
    /** The entity of the pair with the smaller index. */
    public val a: Entity,
    /** The entity of the pair with the larger index. */
    public val b: Entity,
) {
    override fun equals(other: Any?): Boolean = other is Overlap && other.a == a && other.b == b

    override fun hashCode(): Int = 31 * a.hashCode() + b.hashCode()

    override fun toString(): String = "Overlap(a=$a, b=$b)"
}

/**
 * Returns `true` if [entity] is one of the pair, [Overlap.a] or [Overlap.b], so a handler can ask
 * `player in overlap`.
 *
 * @sample samples.collision.Overlaps.otherThan
 */
public operator fun Overlap.contains(entity: Entity): Boolean = entity == a || entity == b

/**
 * Returns the entity of the pair that is not [entity]: [Overlap.b] when [entity] is [Overlap.a]
 * and [Overlap.a] when it is [Overlap.b], so a handler that found its bullet in the pair gets the
 * thing it hit without checking which slot is which.
 *
 * @throws IllegalArgumentException if [entity] is in neither slot.
 *
 * @sample samples.collision.Overlaps.otherThan
 */
public fun Overlap.otherThan(entity: Entity): Entity = when (entity) {
    a -> b
    b -> a
    else -> throw IllegalArgumentException("Entity $entity is in neither slot of $this.")
}
