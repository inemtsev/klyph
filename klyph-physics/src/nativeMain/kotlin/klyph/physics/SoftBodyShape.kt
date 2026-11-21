package klyph.physics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The rest shape of a soft body: a ring of points in order, and a core inside it [coreMass] times
 * heavier than a ring point. The coordinates are the shape's own, with y growing down;
 * [PhysicsWorld.softBody] places it. Make one with [semiDisc], [circle] or [box].
 *
 * [toString] gives its size, for example `SoftBodyShape(ring=22, coreMass=34.0)`.
 *
 * @sample samples.physics.SoftBodySamples.semiDisc
 */
public class SoftBodyShape internal constructor(
    internal val ringX: FloatArray,
    internal val ringY: FloatArray,
    internal val coreX: Float,
    internal val coreY: Float,
    /** How many times heavier the core is than one ring point. */
    public val coreMass: Float,
) {
    init {
        require(ringX.size >= 3 && ringX.size == ringY.size) { "Soft body ring of ${ringX.size} points has fewer than 3." }
        require(coreMass > 0f) { "Core mass $coreMass is not more than 0." }
    }

    /** Number of points in the ring. */
    public val ringCount: Int get() = ringX.size

    override fun toString(): String = "SoftBodyShape(ring=$ringCount, coreMass=$coreMass)"

    /** Builds the shapes soft bodies come in. */
    public companion object {
        /**
         * Returns a slime: a half-disc of [radius], [arcSegments] segments over the dome from the left
         * end to the right, then [baseSegments] along the flat base back, with the dome stretched
         * [heightScale] times taller and the core where the half-disc's centre of mass is. Pass the
         * arguments by name.
         *
         * @throws IllegalArgumentException if [radius] is not more than 0, there are fewer than 2 arc or 1
         * base segments, or [coreMass] is not more than 0.
         *
         * @sample samples.physics.SoftBodySamples.semiDisc
         */
        public fun semiDisc(radius: Float, arcSegments: Int, baseSegments: Int, coreMass: Float, heightScale: Float = 1f): SoftBodyShape {
            require(radius > 0f && arcSegments >= 2 && baseSegments >= 1) {
                "Semi-disc radius $radius, $arcSegments arc segments and $baseSegments base segments make no shape."
            }
            val ring = arcSegments + baseSegments
            val xs = FloatArray(ring)
            val ys = FloatArray(ring)
            for (i in 0..arcSegments) {
                val angle = PI.toFloat() + PI.toFloat() * i / arcSegments
                xs[i] = cos(angle) * radius
                ys[i] = sin(angle) * radius * heightScale
            }
            for (j in 1 until baseSegments) {
                xs[arcSegments + j] = radius - 2f * radius * j / baseSegments
                ys[arcSegments + j] = 0f
            }
            return SoftBodyShape(xs, ys, 0f, -4f * radius / (3f * PI.toFloat()) * heightScale, coreMass)
        }

        /**
         * Returns a ball: [segments] points around a circle of [radius], with the core in the middle.
         *
         * @throws IllegalArgumentException if [radius] is not more than 0, there are fewer than 3
         * segments, or [coreMass] is not more than 0.
         */
        public fun circle(radius: Float, segments: Int, coreMass: Float): SoftBodyShape {
            require(radius > 0f && segments >= 3) { "Circle radius $radius and $segments segments make no shape." }
            val xs = FloatArray(segments) { cos(2f * PI.toFloat() * it / segments) * radius }
            val ys = FloatArray(segments) { sin(2f * PI.toFloat() * it / segments) * radius }
            return SoftBodyShape(xs, ys, 0f, 0f, coreMass)
        }

        /**
         * Returns a box [width] by [height], its ring [across] segments along the top and bottom and
         * [down] along each side, with the core in the middle. Pass the counts by name.
         *
         * @throws IllegalArgumentException if a side is not more than 0, there are fewer than 1 segment a
         * side, or [coreMass] is not more than 0.
         */
        public fun box(width: Float, height: Float, across: Int, down: Int, coreMass: Float): SoftBodyShape {
            require(width > 0f && height > 0f && across >= 1 && down >= 1) {
                "Box $width by $height with $across by $down segments makes no shape."
            }
            val ring = 2 * (across + down)
            val xs = FloatArray(ring)
            val ys = FloatArray(ring)
            var k = 0
            val left = -width / 2f
            val top = -height / 2f
            for (i in 0 until across) {
                xs[k] = left + width * i / across
                ys[k] = top
                k++
            }
            for (i in 0 until down) {
                xs[k] = -left
                ys[k] = top + height * i / down
                k++
            }
            for (i in 0 until across) {
                xs[k] = -left - width * i / across
                ys[k] = -top
                k++
            }
            for (i in 0 until down) {
                xs[k] = left
                ys[k] = -top - height * i / down
                k++
            }
            return SoftBodyShape(xs, ys, 0f, 0f, coreMass)
        }
    }
}

/**
 * How soft a body is. The compliances say how far each kind of constraint gives per unit of force
 * (0 is rigid): the ring's edges, the spokes from the ring to the core, and the area. Soft spokes let a
 * body stretch and squash, a soft area lets it sag and be squeezed, and stiff edges keep the skin from
 * tearing. How fast it moves, how damped and how often it is solved is the world's [PhysicsConfig].
 * Pass the values by name.
 *
 * Two tunings are equal when every value is. [toString] gives every value, as
 * `SoftBodyTuning(edgeCompliance=2.0E-5, spokeCompliance=5.0E-4, areaCompliance=4.0E-4)`.
 *
 * @throws IllegalArgumentException if a compliance is negative or not finite.
 *
 * @sample samples.physics.SoftBodySamples.softBodyTuning
 */
public class SoftBodyTuning(
    /** How far the ring's edges give per unit of force: small, so the skin does not tear. */
    public val edgeCompliance: Float = 0.00002f,
    /** How far the spokes from the ring to the core give: larger lets the body stretch and squash. */
    public val spokeCompliance: Float = 0.0005f,
    /** How far the area gives: larger lets the body sag and be squeezed. */
    public val areaCompliance: Float = 0.0004f,
) {
    init {
        for (c in floatArrayOf(edgeCompliance, spokeCompliance, areaCompliance)) {
            require(c >= 0f && c.isFinite()) { "Compliance $c is negative or not finite." }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is SoftBodyTuning && edgeCompliance.equals(other.edgeCompliance) && spokeCompliance.equals(other.spokeCompliance) &&
            areaCompliance.equals(other.areaCompliance)

    override fun hashCode(): Int = 31 * (31 * edgeCompliance.hashCode() + spokeCompliance.hashCode()) + areaCompliance.hashCode()

    override fun toString(): String =
        "SoftBodyTuning(edgeCompliance=$edgeCompliance, spokeCompliance=$spokeCompliance, areaCompliance=$areaCompliance)"
}
