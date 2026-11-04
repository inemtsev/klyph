package klyph.math

/**
 * An axis-aligned rectangle whose top-left corner is at ([x], [y]) and that is [width] wide and
 * [height] tall, in world or screen units with y pointing down.
 *
 * The rectangle covers x from [x] up to but not including `x + width`, and the same for y, so two
 * rectangles that only touch along an edge do not overlap. A rectangle of zero width or height
 * contains no point.
 *
 * Two rectangles are equal when all four values are equal in the sense of [Float.equals].
 * [toString] prints `Rect(x=0.0, y=0.0, width=800.0, height=600.0)`.
 *
 * @throws IllegalArgumentException if [width] or [height] is negative or not finite, or [x] or
 *   [y] is not finite.
 *
 * @sample samples.math.Rects.contains
 */
public class Rect(
    /** Left edge. */
    public val x: Float,
    /** Top edge. */
    public val y: Float,
    /** Width, at least zero. */
    public val width: Float,
    /** Height, at least zero. */
    public val height: Float,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Rectangle corner ($x, $y) is not finite." }
        require(width >= 0f && width.isFinite()) { "Rectangle width $width is not a finite number of at least zero." }
        require(height >= 0f && height.isFinite()) { "Rectangle height $height is not a finite number of at least zero." }
    }

    override fun equals(other: Any?): Boolean =
        other is Rect && x.equals(other.x) && y.equals(other.y) && width.equals(other.width) && height.equals(other.height)

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        return result
    }

    override fun toString(): String = "Rect(x=$x, y=$y, width=$width, height=$height)"
}

/**
 * The point in the middle of the rectangle.
 *
 * @sample samples.math.Rects.contains
 */
public val Rect.center: Vec2
    get() = Vec2(x + width * 0.5f, y + height * 0.5f)

/**
 * Returns `true` if the point ([x], [y]) lies inside the rectangle: at or right of its left edge,
 * at or below its top edge, and short of its right and bottom edges.
 *
 * @sample samples.math.Rects.contains
 */
public fun Rect.contains(x: Float, y: Float): Boolean =
    x >= this.x && x < this.x + width && y >= this.y && y < this.y + height

/**
 * Returns `true` if [point] lies inside the rectangle, as [contains] with two coordinates does, so
 * `point in rect` reads naturally.
 *
 * @sample samples.math.Rects.contains
 */
public operator fun Rect.contains(point: Vec2): Boolean = contains(point.x, point.y)

/**
 * Returns `true` if this rectangle and [other] share an area larger than zero. Rectangles that
 * only touch along an edge or at a corner do not overlap.
 *
 * @sample samples.math.Rects.overlaps
 */
public fun Rect.overlaps(other: Rect): Boolean =
    x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height
