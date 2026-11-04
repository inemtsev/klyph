package klyph.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An immutable two-dimensional vector of [x] and [y] in world or screen units.
 *
 * Every operation returns a new instance. Two vectors are equal when both components are equal
 * in the sense of [Float.equals]: `NaN` equals `NaN`, and `0.0` differs from `-0.0`, which keeps
 * [equals] consistent with [hashCode]. [toString] prints `Vec2(x, y)`, for example
 * `Vec2(1.0, 2.0)`.
 *
 * @sample samples.math.Vec2s.operators
 */
public class Vec2(
    /** Horizontal component. */
    public val x: Float,
    /** Vertical component. */
    public val y: Float,
) {
    /** Returns the component-wise sum of this vector and [other]. */
    public operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    /** Returns the component-wise difference of this vector and [other]. */
    public operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    /** Returns this vector with both components multiplied by [scale]. */
    public operator fun times(scale: Float): Vec2 = Vec2(x * scale, y * scale)

    /** Returns this vector with both components negated. */
    public operator fun unaryMinus(): Vec2 = Vec2(-x, -y)

    override fun equals(other: Any?): Boolean =
        other is Vec2 && x.equals(other.x) && y.equals(other.y)

    override fun hashCode(): Int = 31 * x.hashCode() + y.hashCode()

    override fun toString(): String = "Vec2($x, $y)"

    /** Holds shared [Vec2] constants. */
    public companion object {
        /** The vector with both components zero. */
        public val ZERO: Vec2 = Vec2(0f, 0f)

        /**
         * Creates the vector of [length] that points [radians] from +x, turning clockwise on
         * screen because y points down: 0 points right and a quarter turn, `PI / 2`, points down.
         * [angle] is its inverse for a vector that is not zero.
         *
         * @sample samples.math.Vec2s.fromAngle
         */
        public fun fromAngle(radians: Float, length: Float = 1f): Vec2 = Vec2(cos(radians) * length, sin(radians) * length)
    }
}

/**
 * Euclidean length of the vector.
 *
 * @sample samples.math.Vec2s.length
 */
public val Vec2.length: Float
    get() = sqrt(x * x + y * y)

/**
 * Square of the vector's [length], `x * x + y * y`. Cheaper than [length] because it needs no
 * square root, so compare it with a squared distance when only the order matters.
 *
 * @sample samples.math.Vec2s.length
 */
public val Vec2.lengthSquared: Float
    get() = x * x + y * y

/**
 * Direction of the vector in radians from +x, between `-PI` and `PI`, turning clockwise on screen
 * because y points down: `Vec2(0f, 1f)` points down the screen at `PI / 2`. The zero vector has an
 * angle of 0. [Vec2.fromAngle] is its inverse.
 *
 * @sample samples.math.Vec2s.fromAngle
 */
public val Vec2.angle: Float
    get() = atan2(y, x)

/**
 * Returns the vector of length 1 that points the same way as this one, or [Vec2.ZERO] if this is
 * the zero vector, which points nowhere.
 *
 * @sample samples.math.Vec2s.normalized
 */
public fun Vec2.normalized(): Vec2 {
    val length = length
    return if (length == 0f) Vec2.ZERO else Vec2(x / length, y / length)
}

/**
 * Returns the distance between this point and [other].
 *
 * @sample samples.math.Vec2s.distanceTo
 */
public fun Vec2.distanceTo(other: Vec2): Float {
    val dx = other.x - x
    val dy = other.y - y
    return sqrt(dx * dx + dy * dy)
}

/**
 * Returns the point [t] of the way from this vector to [to]: this vector at 0, [to] at 1, and the
 * point halfway between them at 0.5. A [t] outside 0 to 1 goes on past either end along the same
 * line.
 *
 * @sample samples.math.Vec2s.lerp
 */
public fun Vec2.lerp(to: Vec2, t: Float): Vec2 = Vec2(x + (to.x - x) * t, y + (to.y - y) * t)

/**
 * Returns the dot product of this vector and [other]: `x * other.x + y * other.y`.
 *
 * The result is zero for perpendicular vectors, and a vector dotted with itself gives its
 * squared [length].
 *
 * @sample samples.math.Vec2s.dot
 */
public fun Vec2.dot(other: Vec2): Float = x * other.x + y * other.y
