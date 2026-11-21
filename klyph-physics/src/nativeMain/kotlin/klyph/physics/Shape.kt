package klyph.physics

import klyph.math.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** What a [Shape] is. Every one is a convex core, a point, a segment or a polygon, grown by a radius. */
public enum class ShapeKind {
    /** A point grown by its radius. */
    CIRCLE,

    /** A segment grown by its radius: a pill, a rounded rod. */
    CAPSULE,

    /** A convex polygon, with sharp corners. */
    POLYGON,
}

/**
 * What a rigid body is made of: a convex shape, in the body's own units, around the body's origin.
 * Make one with [circle], [box], [polygon] or [capsule]; a body made of several is a compound body,
 * such as a hammer of two boxes.
 *
 * Inside, every shape is a convex **core**, one point for a circle, two for a capsule, three or more
 * for a polygon, grown by a [radius]: a circle is a point grown by its radius, a capsule a segment, a
 * polygon has a radius of 0. One way of describing all of them means one set of collision code.
 *
 * A polygon's points go clockwise on screen (y grows down), and each edge `i`, from point `i` to the
 * next, has a unit normal pointing out. A shape never changes once made, so any number of bodies can
 * share one.
 *
 * [toString] gives its kind and size, for example `Shape(POLYGON, vertices=4, radius=0.0)`.
 *
 * @sample samples.physics.RigidBodySamples.shapes
 */
public class Shape private constructor(
    /** Whether it is a circle, a capsule or a polygon. */
    public val kind: ShapeKind,
    internal val vx: FloatArray,
    internal val vy: FloatArray,
    /** How far the shape reaches out from its core: a circle's or capsule's radius, 0 for a polygon. */
    public val radius: Float,
) {
    /** Number of points in the core: 1 for a circle, 2 for a capsule, 3 or more for a polygon. */
    public val vertexCount: Int get() = vx.size

    // The unit normal of each edge, pointing out: a capsule's two edges are its segment, both ways.
    internal val nx = FloatArray(if (vx.size > 1) vx.size else 0)
    internal val ny = FloatArray(nx.size)

    /** Area the shape covers, in units squared. */
    public val area: Float

    /** Middle of the shape by area, across: where a body made of it alone balances. */
    public val centroidX: Float

    /** Middle of the shape by area, down: see [centroidX]. */
    public val centroidY: Float

    /** Moment of inertia about ([centroidX], [centroidY]) for a density of 1: how hard the shape is to turn. */
    public val inertia: Float

    /**
     * A circle around the whole shape, in the body's frame: the middle of its core's points and how far
     * the furthest of them reaches, its radius too. What's further from the middle can't touch it.
     */
    /**
     * How thin the shape is at its thinnest, near enough: twice its radius, plus for a polygon twice
     * the distance from its middle to its nearest face. A bullet is swept in steps no longer than half
     * this, so it can't step over anything.
     */
    internal val thickness: Float get() = run {
        var inner = 0f
        if (vx.size >= 3) {
            val cx = vx.average().toFloat()
            val cy = vy.average().toFloat()
            inner = Float.MAX_VALUE
            for (i in vx.indices) inner = minOf(inner, (vx[i] - cx) * nx[i] + (vy[i] - cy) * ny[i])
        }
        2f * (inner + radius)
    }

    internal val boundX: Float = vx.average().toFloat()
    internal val boundY: Float = vy.average().toFloat()
    internal val boundRadius: Float = run {
        var far = 0f
        for (i in vx.indices) far = max(far, sqrt((vx[i] - boundX) * (vx[i] - boundX) + (vy[i] - boundY) * (vy[i] - boundY)))
        far + radius
    }

    init {
        for (i in nx.indices) {
            val j = if (i + 1 == vx.size) 0 else i + 1
            val ex = vx[j] - vx[i]
            val ey = vy[j] - vy[i]
            val length = sqrt(ex * ex + ey * ey)
            nx[i] = ey / length
            ny[i] = -ex / length
        }
        when (kind) {
            ShapeKind.CIRCLE -> {
                area = PI_F * radius * radius
                centroidX = vx[0]
                centroidY = vy[0]
                inertia = area * radius * radius / 2f
            }
            ShapeKind.CAPSULE -> {
                // A box between the two caps' centres, and a disc split in half over its two ends.
                val ex = vx[1] - vx[0]
                val ey = vy[1] - vy[0]
                val length = sqrt(ex * ex + ey * ey)
                val rr = radius * radius
                val disc = PI_F * rr
                val middle = 2f * radius * length
                area = disc + middle
                centroidX = (vx[0] + vx[1]) / 2f
                centroidY = (vy[0] + vy[1]) / 2f
                // Each half disc's own centroid is 4r / 3π from its flat side; the parallel axis
                // theorem moves it there from the half's centre, then out to its end of the box.
                val lc = 4f * radius / (3f * PI_F)
                val h = length / 2f
                inertia = disc * (rr / 2f + h * h + 2f * h * lc) + middle * (4f * rr + length * length) / 12f
            }
            ShapeKind.POLYGON -> {
                // A fan of triangles from the first point: each adds its area, its centroid weighted
                // by that, and its inertia about the first point.
                var a = 0f
                var cx = 0f
                var cy = 0f
                var i0 = 0f
                val ox = vx[0]
                val oy = vy[0]
                for (k in 1 until vx.size - 1) {
                    val e1x = vx[k] - ox
                    val e1y = vy[k] - oy
                    val e2x = vx[k + 1] - ox
                    val e2y = vy[k + 1] - oy
                    val cross = e1x * e2y - e1y * e2x
                    val triangle = cross / 2f
                    a += triangle
                    cx += triangle * (e1x + e2x) / 3f
                    cy += triangle * (e1y + e2y) / 3f
                    val intX = e1x * e1x + e2x * e1x + e2x * e2x
                    val intY = e1y * e1y + e2y * e1y + e2y * e2y
                    i0 += cross * (intX + intY) / 12f
                }
                area = a
                cx /= a
                cy /= a
                centroidX = ox + cx
                centroidY = oy + cy
                // from the first point to the centroid
                inertia = i0 - a * (cx * cx + cy * cy)
            }
        }
    }

    /**
     * Returns the position across of core point [index], in the body's units.
     *
     * @throws IndexOutOfBoundsException if [index] is not in `0 until vertexCount`.
     */
    public fun vertexXAt(index: Int): Float = vx[checkVertex(index)]

    /**
     * Returns the position down of core point [index], in the body's units.
     *
     * @throws IndexOutOfBoundsException if [index] is not in `0 until vertexCount`.
     */
    public fun vertexYAt(index: Int): Float = vy[checkVertex(index)]

    /**
     * Returns the unit normal across of edge [index], from point [index] to the next, pointing out.
     * A circle has no edges; a capsule has two, its segment both ways.
     *
     * @throws IndexOutOfBoundsException if the shape has no edge [index].
     */
    public fun normalXAt(index: Int): Float = nx[checkEdge(index)]

    /**
     * Returns the unit normal down of edge [index]: see [normalXAt].
     *
     * @throws IndexOutOfBoundsException if the shape has no edge [index].
     */
    public fun normalYAt(index: Int): Float = ny[checkEdge(index)]

    private fun checkVertex(index: Int): Int {
        if (index !in vx.indices) throw IndexOutOfBoundsException("Index $index is out of bounds for size ${vx.size}.")
        return index
    }

    private fun checkEdge(index: Int): Int {
        if (index !in nx.indices) throw IndexOutOfBoundsException("Index $index is out of bounds for size ${nx.size}.")
        return index
    }

    override fun toString(): String = "Shape($kind, vertices=$vertexCount, radius=$radius)"

    /**
     * Returns how far the point ([x], [y]), in the body's units, is outside the shape: negative
     * inside, by how deep, at least where the core is near; 0 on its edge.
     */
    public fun distance(x: Float, y: Float): Float {
        val n = vx.size
        if (n >= 3) {
            // Inside the core: as deep as the nearest edge.
            var deepest = -Float.MAX_VALUE
            for (i in 0 until n) deepest = max(deepest, (x - vx[i]) * nx[i] + (y - vy[i]) * ny[i])
            if (deepest <= 0f) return deepest - radius
        }
        var nearest = Float.MAX_VALUE
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            nearest = min(nearest, segmentDistanceSquared(x, y, vx[i], vy[i], vx[j], vy[j]))
            if (n <= 2) break
        }
        return sqrt(nearest) - radius
    }

    /** Returns `true` if the point ([x], [y]), in the body's units, is inside the shape or on its edge. */
    public fun contains(x: Float, y: Float): Boolean = distance(x, y) <= 0f

    /** This shape, moved by ([dx], [dy]). */
    internal fun moved(dx: Float, dy: Float): Shape =
        Shape(kind, FloatArray(vx.size) { vx[it] + dx }, FloatArray(vy.size) { vy[it] + dy }, radius)

    /** Makes shapes. */
    public companion object {
        /** Most points a polygon may have. */
        public const val MAX_VERTICES: Int = 16

        private const val PI_F = PI.toFloat()

        /**
         * Returns a circle of [radius] centred on ([x], [y]).
         *
         * @throws IllegalArgumentException if [radius] is not finite and more than 0.
         */
        public fun circle(radius: Float, x: Float = 0f, y: Float = 0f): Shape {
            require(radius > 0f && radius.isFinite()) { "Circle radius $radius is not finite and more than 0." }
            return Shape(ShapeKind.CIRCLE, floatArrayOf(x), floatArrayOf(y), radius)
        }

        /**
         * Returns a box [width] by [height] centred on ([x], [y]), turned by [angle] radians.
         *
         * @throws IllegalArgumentException if a side is not finite and more than 0.
         */
        public fun box(width: Float, height: Float, x: Float = 0f, y: Float = 0f, angle: Float = 0f): Shape {
            require(width > 0f && height > 0f && width.isFinite() && height.isFinite()) {
                "Box $width by $height has a side that is not finite and more than 0."
            }
            val hx = width / 2f
            val hy = height / 2f
            val c = cos(angle)
            val s = sin(angle)
            val cornerX = floatArrayOf(-hx, hx, hx, -hx)
            val cornerY = floatArrayOf(-hy, -hy, hy, hy)
            return Shape(
                ShapeKind.POLYGON,
                FloatArray(4) { x + c * cornerX[it] - s * cornerY[it] },
                FloatArray(4) { y + s * cornerX[it] + c * cornerY[it] },
                0f,
            )
        }

        /**
         * Returns a pill [length] long between its two caps' centres, and [radius] round, centred on
         * ([x], [y]) and lying along x, turned by [angle] radians. End to end it is `length + 2 · radius`.
         *
         * @throws IllegalArgumentException if [length] or [radius] is not finite and more than 0.
         */
        public fun capsule(length: Float, radius: Float, x: Float = 0f, y: Float = 0f, angle: Float = 0f): Shape {
            require(length > 0f && length.isFinite()) { "Capsule length $length is not finite and more than 0; for none, make a circle." }
            require(radius > 0f && radius.isFinite()) { "Capsule radius $radius is not finite and more than 0; for none, make a box." }
            val hx = cos(angle) * length / 2f
            val hy = sin(angle) * length / 2f
            return Shape(ShapeKind.CAPSULE, floatArrayOf(x - hx, x + hx), floatArrayOf(y - hy, y + hy), radius)
        }

        /**
         * Returns a convex polygon through [points], which may go round either way:
         * `polygon(listOf(Vec2(0f, 0f), Vec2(20f, 0f), Vec2(10f, 15f)))` is a triangle. A dent, a
         * crossing, or points in a line are refused: a concave shape is made of several convex ones,
         * as a compound body.
         *
         * @throws IllegalArgumentException if there are fewer than 3 or more than [MAX_VERTICES]
         * points, a point is not finite, two points are the same, three are in a line, or the
         * polygon is not convex or crosses itself.
         *
         * @sample samples.physics.RigidBodySamples.shapes
         */
        public fun polygon(points: List<Vec2>): Shape {
            val flat = FloatArray(2 * points.size)
            for ((i, p) in points.withIndex()) {
                flat[2 * i] = p.x
                flat[2 * i + 1] = p.y
            }
            return polygon(*flat)
        }

        internal fun polygon(vararg points: Float): Shape {
            require(points.size % 2 == 0) { "Polygon of ${points.size} coordinates has an x without a y." }
            val n = points.size / 2
            require(n in 3..MAX_VERTICES) { "Polygon of $n points is outside 3..$MAX_VERTICES." }
            for (v in points) require(v.isFinite()) { "Polygon point $v is not finite." }
            val xs = FloatArray(n) { points[2 * it] }
            val ys = FloatArray(n) { points[2 * it + 1] }
            var twiceArea = 0f
            for (i in 0 until n) {
                val j = (i + 1) % n
                twiceArea += xs[i] * ys[j] - xs[j] * ys[i]
            }
            // clockwise on screen is a positive area with y down; turn the other way round
            if (twiceArea < 0f) {
                xs.reverse()
                ys.reverse()
            }
            // Convex: every corner turns the same way, and all of them together turn once, not twice.
            var turning = 0f
            for (i in 0 until n) {
                val j = (i + 1) % n
                val k = (i + 2) % n
                val ax = xs[j] - xs[i]
                val ay = ys[j] - ys[i]
                val bx = xs[k] - xs[j]
                val by = ys[k] - ys[j]
                val cross = ax * by - ay * bx
                val scale = sqrt((ax * ax + ay * ay) * (bx * bx + by * by))
                require(scale > 0f) { "Polygon points $j and ${if (j == 0) n - 1 else j - 1} are both (${xs[j]}, ${ys[j]})." }
                require(cross > 1e-5f * scale) {
                    val corner = "(${xs[j]}, ${ys[j]})"
                    if (abs(cross) <= 1e-5f * scale) {
                        "Polygon corner $corner is in a line with its neighbours; leave that point out."
                    } else {
                        "Polygon has a dent at $corner; make a concave shape from convex pieces, as a compound body."
                    }
                }
                turning += atan2(cross, ax * bx + ay * by)
            }
            require(turning < 3f * PI_F) { "Polygon crosses itself; list its points in order round the outside." }
            return Shape(ShapeKind.POLYGON, xs, ys, 0f)
        }

        /** How far ([px], [py]) is from the segment from ([ax], [ay]) to ([bx], [by]), squared. */
        internal fun segmentDistanceSquared(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val ex = bx - ax
            val ey = by - ay
            val lengthSquared = ex * ex + ey * ey
            val t = if (lengthSquared == 0f) 0f else (((px - ax) * ex + (py - ay) * ey) / lengthSquared).coerceIn(0f, 1f)
            val dx = px - (ax + ex * t)
            val dy = py - (ay + ey * t)
            return dx * dx + dy * dy
        }
    }
}

/**
 * What a body made of several shapes weighs: the mass of [shapes] at [density], where it balances,
 * and how hard it is to turn about there. A body's shapes are moved so that point is its origin.
 */
internal class MassProperties(shapes: List<Shape>, density: Float) {
    val mass: Float
    val centerX: Float
    val centerY: Float
    val inertia: Float

    init {
        var area = 0f
        var cx = 0f
        var cy = 0f
        for (s in shapes) {
            area += s.area
            cx += s.area * s.centroidX
            cy += s.area * s.centroidY
        }
        cx /= area
        cy /= area
        var i = 0f
        for (s in shapes) {
            val dx = s.centroidX - cx
            val dy = s.centroidY - cy
            i += s.inertia + s.area * (dx * dx + dy * dy)
        }
        mass = area * density
        centerX = cx
        centerY = cy
        inertia = i * density
    }
}
