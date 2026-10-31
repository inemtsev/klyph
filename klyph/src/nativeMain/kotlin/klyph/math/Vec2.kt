package klyph.math

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
 * Returns the dot product of this vector and [other]: `x * other.x + y * other.y`.
 *
 * The result is zero for perpendicular vectors, and a vector dotted with itself gives its
 * squared [length].
 *
 * @sample samples.math.Vec2s.dot
 */
public fun Vec2.dot(other: Vec2): Float = x * other.x + y * other.y
