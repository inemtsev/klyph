package klyph.collision

import klyph.entity.Entity

/**
 * The first touch of a box swept by [Collisions.sweepBox] or [Collisions.sweepBoxTo] with a [Solid]:
 * which solid, how far along the move and which face.
 *
 * [fraction] is how far along the move the box gets before it touches the solid, from 0 at the
 * start to 1 at the end, so the box stops at `x + dx * fraction`, `y + dy * fraction`. The normal,
 * [normalX] and [normalY], points out of the face of the solid that was hit and is one of (-1, 0),
 * (1, 0), (0, -1) and (0, 1): (0, -1) for a box landing on a solid's top, because y points down.
 * A box that already overlaps the solid where the sweep starts hits it at fraction 0 with the normal
 * (0, 0).
 *
 * Two hits are equal when all four properties are. [toString] gives
 * `SweepHit(entity=Entity(1:0), fraction=0.25, normalX=0.0, normalY=-1.0)`.
 *
 * @sample samples.collision.Sweeps.sweepBox
 */
public class SweepHit internal constructor(
    /** The solid that was hit. */
    public val entity: Entity,
    /** How far along the move the box touches the solid, from 0 to 1. */
    public val fraction: Float,
    /** The horizontal part of the normal of the face that was hit: -1, 0 or 1. */
    public val normalX: Float,
    /** The vertical part of the normal of the face that was hit: -1, 0 or 1; -1 is a floor. */
    public val normalY: Float,
) {
    override fun equals(other: Any?): Boolean =
        other is SweepHit && other.entity == entity && other.fraction == fraction &&
            other.normalX == normalX && other.normalY == normalY

    override fun hashCode(): Int {
        var result = entity.hashCode()
        result = 31 * result + fraction.hashCode()
        result = 31 * result + normalX.hashCode()
        return 31 * result + normalY.hashCode()
    }

    override fun toString(): String = "SweepHit(entity=$entity, fraction=$fraction, normalX=$normalX, normalY=$normalY)"
}
