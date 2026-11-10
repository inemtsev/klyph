package klyph.collision

import klyph.entity.ComponentType

/**
 * Makes an entity's box [Collider] a wall, floor or ledge that every [KinematicBody] stops at and
 * that [Collisions.sweepBox] finds, such as a building, a platform or the edge of the level.
 *
 * The solid's area is its collider's box, placed as [Collider] describes: at the entity's
 * `WorldTransform` or `Transform`, offset and scaled unless [Collider.scalesWithTransform] is
 * `false`, with rotation ignored. The collider must be a [Shape.Box]: a solid without a [Collider]
 * or with a [Shape.Circle] makes the next body move, sweep or `Collisions.detect` throw
 * [IllegalStateException] naming the entity. A solid without a position is ignored, as a collider is.
 * Its collider's layers say which bodies and sweeps it stops: those whose mask contains one of them.
 *
 * Solids do not move. [Collisions] keeps them in a grid of its own, which it checks against the
 * world at every body move and every detection, once per fixed step and once per frame, at a cost
 * that grows with the number of solids, and rebuilds when a solid was added, removed, moved,
 * resized or changed its [passable] or layers. A solid moved by a system therefore stops bodies
 * where it now is from the next fixed step on, but nothing carries a body along with it, and a
 * body the solid moved into passes through it.
 *
 * A solid is also an ordinary collider: it takes part in overlap detection and `Overlap` events
 * like any other, so a game may still use layers and masks to hear about touches. A solid of a
 * scene frozen under a pushed scene stops nothing, as its collider is left out of detection.
 *
 * [toString] gives `Solid(passable=NEVER)`.
 *
 * @sample samples.collision.Solids.solid
 */
public class Solid(
    /** Which way bodies may pass through the solid; [Passable.NEVER] makes it solid from every side. */
    public var passable: Passable = Passable.NEVER,
) {
    /** Returns `Solid(passable=NEVER)`. The format may change. */
    override fun toString(): String = "Solid(passable=$passable)"

    /** Component key for [Solid]. */
    public companion object : ComponentType<Solid>("Solid")
}

/**
 * The ways a [KinematicBody] or a sweep may pass through a [Solid].
 *
 * @sample samples.collision.Solids.oneWay
 */
public enum class Passable {
    /** Never: the solid stops bodies from every side. */
    NEVER,

    /**
     * From below: a one-way ledge. It stops a body or a sweep only when it moves down and its
     * bottom edge was at or above the solid's top before the move, so a body jumps up through the
     * ledge, lands on it from above and walks through it from the sides. [KinematicBody.dropThrough]
     * lets a body standing on it fall through.
     */
    FROM_BELOW,
}
