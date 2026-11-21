package klyph.physics

import kotlin.math.sqrt

/**
 * A closed outline of points, in order, such as a soft body's skin: see [SoftBodies.ringOf]. It reads
 * the points where they are now, so the same one serves every frame.
 */
public interface Ring {
    /** Number of points in the outline. The one after the last is the first. */
    public val size: Int

    /** Returns the position across of the point at [index], in `0 until size`. */
    public fun xAt(index: Int): Float

    /** Returns the position down of the point at [index], in `0 until size`. */
    public fun yAt(index: Int): Float
}

/**
 * Where a soft body's outline is, as shapes: what lies inside it, the stretches of a row that lie
 * inside it, and the nearest point on it. This is the one place that knows how the outline's edges
 * join up, so whatever fills the body on screen and whatever gets pushed out of it agree about what
 * "inside" means, even when the outline has folded over itself.
 *
 * Inside is decided by winding number, not even-odd: a point the outline winds around at all counts,
 * so a folded part of the body is solid rather than hollow.
 */
internal object Outline {
    private fun next(ring: Ring, i: Int): Int = if (i + 1 == ring.size) 0 else i + 1

    /** Whether the point ([px], [py]) is inside the outline of [body]. */
    fun contains(body: Ring, px: Float, py: Float): Boolean {
        var winding = 0
        for (i in 0 until body.size) {
            val j = next(body, i)
            val y0 = body.yAt(i)
            val y1 = body.yAt(j)
            if ((y0 <= py) != (y1 <= py)) {
                val crossingX = body.xAt(i) + (py - y0) / (y1 - y0) * (body.xAt(j) - body.xAt(i))
                if (crossingX > px) winding += if (y1 > y0) 1 else -1
            }
        }
        return winding != 0
    }

    /**
     * Finds the point on the outline of [body] nearest to ([px], [py]), writes its x and y to
     * `out[0]` and `out[1]`, and returns its squared distance from the point.
     */
    fun nearestPoint(body: Ring, px: Float, py: Float, out: FloatArray): Float {
        var best = Float.MAX_VALUE
        for (e in 0 until body.size) {
            val f = next(body, e)
            val ex = body.xAt(f) - body.xAt(e)
            val ey = body.yAt(f) - body.yAt(e)
            val lengthSquared = ex * ex + ey * ey
            val t = if (lengthSquared < 1e-9f) 0f else (((px - body.xAt(e)) * ex + (py - body.yAt(e)) * ey) / lengthSquared).coerceIn(0f, 1f)
            val cx = body.xAt(e) + ex * t
            val cy = body.yAt(e) + ey * t
            val d = (px - cx) * (px - cx) + (py - cy) * (py - cy)
            if (d < best) {
                best = d
                out[0] = cx
                out[1] = cy
            }
        }
        return best
    }

    /**
     * Writes into [left] and [right] the stretches of height [y] that are inside the outline, left to
     * right, and returns how many. [left] and [right] must hold at least as many entries as the body
     * has outline particles.
     */
    fun spans(body: Ring, y: Float, left: FloatArray, right: FloatArray): Int {
        var crossings = 0
        for (i in 0 until body.size) {
            val j = next(body, i)
            val y0 = body.yAt(i)
            val y1 = body.yAt(j)
            if ((y0 <= y) != (y1 <= y)) {
                // `left` holds each crossing's x and `right` its direction until they are paired up below.
                left[crossings] = body.xAt(i) + (y - y0) / (y1 - y0) * (body.xAt(j) - body.xAt(i))
                right[crossings] = if (y1 > y0) 1f else -1f
                crossings++
            }
        }
        for (i in 1 until crossings) {
            val x = left[i]
            val direction = right[i]
            var k = i - 1
            while (k >= 0 && left[k] > x) {
                left[k + 1] = left[k]
                right[k + 1] = right[k]
                k--
            }
            left[k + 1] = x
            right[k + 1] = direction
        }
        // Walk the crossings left to right keeping the winding number, writing each stretch where it is
        // not zero over the crossings already read: a stretch never ends before its own start, so this is safe in place.
        var winding = 0
        var start = 0f
        var count = 0
        for (i in 0 until crossings) {
            val x = left[i]
            val wasInside = winding != 0
            winding += right[i].toInt()
            val isInside = winding != 0
            if (!wasInside && isInside) {
                start = x
            } else if (wasInside && !isInside) {
                left[count] = start
                right[count] = x
                count++
            }
        }
        return count
    }
}
