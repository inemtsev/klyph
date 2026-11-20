package klyph.collision

import klyph.entity.ComponentType
import klyph.entity.Velocity

/**
 * Makes an entity's box [Collider] a wall, floor or ledge that every [KinematicBody] stops at and
 * that [Collisions.sweepBox] finds, such as a building, a platform or the edge of the level.
 *
 * The solid's area is its collider's box, placed as [Collider] describes: at the entity's
 * `Transform`, offset and scaled unless [Collider.scalesWithTransform] is `false`, with rotation
 * ignored. The entity must be a root entity with a [Shape.Box] collider: a solid without a
 * [Collider], with a [Shape.Circle] or with a parent makes the next body move, sweep or
 * `Collisions.detect` throw [IllegalStateException] naming the entity. A solid without a position
 * is ignored, as a collider is.
 * Its collider's layers say which bodies and sweeps it stops: those whose mask contains one of them.
 *
 * A solid moves like any entity: give it a [Velocity], or set its `Transform` from a system, and it
 * is a moving platform, a lift or a crushing wall. Its move is never shortened. In every fixed
 * step, before the bodies move, a body standing on a solid that moved since the last step is
 * carried with it on both axes, and a body the solid moved into is pushed along the solid's move
 * until it is clear, or crushed against another solid, as [KinematicBody] describes; a
 * [Passable.FROM_BELOW] solid carries but never pushes. A move made between two steps acts once,
 * in the next step, so a solid moved by a system in `UPDATE` carries its riders then.
 *
 * [Collisions] keeps the solids in a grid of its own, which it checks against the world at every
 * body move and every detection, once per fixed step and once per frame, at a cost that grows with
 * the number of solids. A solid with a [Velocity], or one that moved in the last second or so, is
 * kept in a second hash grid, rebuilt over each moving solid's whole move, so a level where a few
 * platforms move rebuilds only that one; the first grid is rebuilt only when a solid is added or
 * removed, starts or stops moving, or changes its [passable] or layers while it stands still.
 *
 * A solid is also an ordinary collider: it takes part in overlap detection and `Overlap` events
 * like any other, so a game may still use layers and masks to hear about touches. A solid of a
 * scene frozen under a pushed scene stops nothing, as its collider is left out of detection, and
 * does not move, as its [Velocity] stands still.
 *
 * [toString] gives `Solid(passable=NEVER)`.
 *
 * @sample samples.collision.Solids.solid
 * @sample samples.collision.Bodies.wasCarried
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
