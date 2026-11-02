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
