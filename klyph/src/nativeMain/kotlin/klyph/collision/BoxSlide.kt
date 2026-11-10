package klyph.collision

import klyph.entity.Entity

/**
 * Where a box moved by [Collisions.slideBox] or [Collisions.slideCollider] came to rest and what it
 * touched on the way: the same move and the same contacts a [KinematicBody] gets in a fixed step,
 * without a body and without moving anything.
 *
 * A slide writes into the instance it is given and returns it, so a planner that follows a jump
 * step by step reuses one and allocates nothing. Every property describes the last slide written
 * into it; a new instance holds a slide of zero length at (0, 0) that touched nothing.
 *
 * [toString] gives
 * `BoxSlide(x=15.0, y=79.99, movedX=15.0, movedY=79.99, isOnFloor=true, isOnWall=false, wallNormalX=0.0, isOnCeiling=false)`.
 *
 * @sample samples.collision.Slides.slideBox
 */
public class BoxSlide {
    /** The x the slide ended at: the box's center for [Collisions.slideBox], the entity's position for [Collisions.slideCollider]. */
    public var x: Float = 0f
        internal set

    /** The y the slide ended at: the box's center for [Collisions.slideBox], the entity's position for [Collisions.slideCollider]. */
    public var y: Float = 0f
        internal set

    /** How far the box moved across, which is less than asked when it ran into a wall. */
    public var movedX: Float = 0f
        internal set

    /** How far the box moved up or down, which is less than asked when it landed or bumped a ceiling. */
    public var movedY: Float = 0f
        internal set

    /** Whether the slide ended on a floor: the box came down onto one, or the floor snap found one. */
    public var isOnFloor: Boolean = false
        internal set

    /** Whether the box ran into a wall moving across. */
    public var isOnWall: Boolean = false
        internal set

    /** Whether the box ran into a ceiling moving up. */
    public var isOnCeiling: Boolean = false
        internal set

    /** The direction pointing away from the wall the box ran into: -1 for a wall on its right, 1 for one on its left, 0 with no wall. */
    public var wallNormalX: Float = 0f
        internal set

    /** The raw handle of the solid under the box, or [KinematicBody.NO_FLOOR]. */
    internal var floorRaw: Long = KinematicBody.NO_FLOOR

    /** Returns the [Solid] entity the box ended on, or `null` while [isOnFloor] is `false`. */
    public val floorOrNull: Entity?
        get() = if (floorRaw == KinematicBody.NO_FLOOR) null else Entity(floorRaw)

    /** Returns `BoxSlide(x=15.0, y=79.99, movedX=15.0, …)`. The format may change. */
    override fun toString(): String =
        "BoxSlide(x=$x, y=$y, movedX=$movedX, movedY=$movedY, isOnFloor=$isOnFloor, isOnWall=$isOnWall, " +
            "wallNormalX=$wallNormalX, isOnCeiling=$isOnCeiling)"
}
