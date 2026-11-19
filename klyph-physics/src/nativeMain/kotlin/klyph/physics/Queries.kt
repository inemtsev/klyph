package klyph.physics

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * What a query looks at: [RIGID_BODIES], [SOFT_BODIES] and [SOLIDS], joined with [plus], or
 * [EVERYTHING]. Two are equal when they look at the same things.
 *
 * [toString] names them, for example `QueryTargets(RIGID_BODIES, SOLIDS)`.
 *
 * @sample samples.physics.QuerySamples.raycast
 */
public value class QueryTargets internal constructor(internal val bits: Int) {
    /** Returns the targets of this and of [other] together. */
    public operator fun plus(other: QueryTargets): QueryTargets = QueryTargets(bits or other.bits)

    /** Returns `true` if this looks at everything [other] does. */
    public operator fun contains(other: QueryTargets): Boolean = bits and other.bits == other.bits

    override fun toString(): String {
        val all = listOf("RIGID_BODIES" to RIGID, "SOFT_BODIES" to SOFT, "SOLIDS" to SOLID)
        val names = all.filter { bits and it.second != 0 }.map { it.first }
        return names.joinToString(prefix = "QueryTargets(", postfix = ")")
    }

    /** Holds the things a query can look at. */
    public companion object {
        /** The rigid bodies, by their shapes. Sensors are never found. */
        public val RIGID_BODIES: QueryTargets = QueryTargets(RIGID)

        /** The soft bodies, by their outlines. */
        public val SOFT_BODIES: QueryTargets = QueryTargets(SOFT)

        /** The solids: tiles and moving solids. */
        public val SOLIDS: QueryTargets = QueryTargets(SOLID)

        /** Everything: rigid bodies, soft bodies and solids. */
        public val EVERYTHING: QueryTargets = QueryTargets(RIGID or SOFT or SOLID)

        private const val RIGID = 1
        private const val SOFT = 2
        private const val SOLID = 4
    }
}

/** What a ray hit: see [RayHit.kind]. */
public enum class HitKind {
    /** Nothing: the ray reached its end. */
    NOTHING,

    /** A rigid body: see [RayHit.rigidBodyOrNull]. */
    RIGID_BODY,

    /** A soft body: see [RayHit.softBodyOrNull]. */
    SOFT_BODY,

    /** A solid: see [RayHit.solidOrNull]. */
    SOLID,
}

/**
 * Where a ray first hit something, filled in by [PhysicsWorld.raycast]. Make one and keep it, so a
 * raycast allocates nothing.
 *
 * [toString] gives what it hit and where, for example
 * `RayHit(RIGID_BODY, RigidBody(2), at=(120.0, 300.0), normal=(0.0, -1.0), fraction=0.4)`.
 *
 * @sample samples.physics.QuerySamples.raycast
 */
public class RayHit {
    /** What it hit, or [HitKind.NOTHING]. */
    public var kind: HitKind = HitKind.NOTHING
        internal set

    // The rigid or soft body hit, or -1; the index of the rigid body's shape hit, or -1.
    internal var body: Int = -1
    internal var shape: Int = -1

    /** The rigid body it hit, or `null` when it hit something else or nothing. */
    public val rigidBodyOrNull: RigidBody? get() = if (kind == HitKind.RIGID_BODY) RigidBody(body) else null

    /** The soft body it hit, or `null` when it hit something else or nothing. */
    public val softBodyOrNull: SoftBody? get() = if (kind == HitKind.SOFT_BODY) SoftBody(body) else null

    /** The shape of the rigid body it hit, or `null` when it hit something else or nothing. */
    public var shapeOrNull: Shape? = null
        internal set

    /** The solid it hit, or `null` when it hit something else or nothing. */
    public var solidOrNull: Solid? = null
        internal set

    /** Position across where it hit. */
    public var x: Float = 0f
        internal set

    /** Position down where it hit. */
    public var y: Float = 0f
        internal set

    /** Normal of the surface where it hit, across: a unit long with [normalY], pointing back towards the ray's start. */
    public var normalX: Float = 0f
        internal set

    /** Normal of the surface where it hit, down: see [normalX]. */
    public var normalY: Float = 0f
        internal set

    /** Distance along the ray where it hit: 0 at its start, 1 at its end. */
    public var fraction: Float = 1f
        internal set

    /** Whether it hit anything. */
    public val isHit: Boolean get() = kind != HitKind.NOTHING

    internal var what: HitKind
        get() = kind
        set(value) {
            kind = value
        }

    internal var solid: Solid?
        get() = solidOrNull
        set(value) {
            solidOrNull = value
        }

    internal val hit: Boolean get() = isHit

    internal fun clear() {
        kind = HitKind.NOTHING
        body = -1
        shape = -1
        shapeOrNull = null
        solidOrNull = null
        fraction = 1f
    }

    override fun toString(): String {
        if (!isHit) return "RayHit(NOTHING)"
        val thing = rigidBodyOrNull ?: softBodyOrNull ?: solidOrNull
        return "RayHit($kind, $thing, at=($x, $y), normal=($normalX, $normalY), fraction=$fraction)"
    }
}

/**
 * What a point or box query found, filled in by [PhysicsWorld.queryPoint] and [PhysicsWorld.queryBox].
 * Make one and keep it: it only allocates when it has to grow, the first few times.
 *
 * [toString] gives how many of each it found, for example
 * `Overlaps(rigidBodies=2, softBodies=0, solids=1)`.
 *
 * @sample samples.physics.QuerySamples.queryBox
 */
public class Overlaps(initialCapacity: Int = 16) {
    private var rigid = IntArray(initialCapacity)
    private var soft = IntArray(initialCapacity)
    private var solidsFound = arrayOfNulls<Solid>(initialCapacity)

    /** Number of rigid bodies found. */
    public var rigidBodyCount: Int = 0
        private set

    /** Number of soft bodies found. */
    public var softBodyCount: Int = 0
        private set

    /** Number of solids found. */
    public var solidCount: Int = 0
        private set

    /** Number of things found in all. */
    public val size: Int get() = rigidBodyCount + softBodyCount + solidCount

    /**
     * Returns the rigid body found at [index].
     *
     * @throws IndexOutOfBoundsException if [index] is not in `0 until rigidBodyCount`.
     */
    public fun rigidBodyAt(index: Int): RigidBody {
        checkIndex(index, rigidBodyCount)
        return RigidBody(rigid[index])
    }

    /**
     * Returns the soft body found at [index].
     *
     * @throws IndexOutOfBoundsException if [index] is not in `0 until softBodyCount`.
     */
    public fun softBodyAt(index: Int): SoftBody {
        checkIndex(index, softBodyCount)
        return SoftBody(soft[index])
    }

    /**
     * Returns the solid found at [index].
     *
     * @throws IndexOutOfBoundsException if [index] is not in `0 until solidCount`.
     */
    public fun solidAt(index: Int): Solid {
        checkIndex(index, solidCount)
        return solidsFound[index]!!
    }

    /** Returns `true` if it found [body]. */
    public fun contains(body: RigidBody): Boolean = hasRigidBody(body.index)

    /** Returns `true` if it found [body]. */
    public fun contains(body: SoftBody): Boolean = hasSoftBody(body.index)

    internal fun rigidBody(i: Int): Int = rigid[i]

    internal fun softBody(i: Int): Int = soft[i]

    internal fun solid(i: Int): Solid = solidsFound[i]!!

    internal val count: Int get() = size

    internal fun hasRigidBody(b: Int): Boolean {
        for (i in 0 until rigidBodyCount) if (rigid[i] == b) return true
        return false
    }

    internal fun hasSoftBody(b: Int): Boolean {
        for (i in 0 until softBodyCount) if (soft[i] == b) return true
        return false
    }

    private fun checkIndex(index: Int, size: Int) {
        if (index !in 0 until size) throw IndexOutOfBoundsException("Index $index is out of bounds for size $size.")
    }

    override fun toString(): String = "Overlaps(rigidBodies=$rigidBodyCount, softBodies=$softBodyCount, solids=$solidCount)"

    internal fun clear() {
        rigidBodyCount = 0
        softBodyCount = 0
        for (i in 0 until solidCount) solidsFound[i] = null
        solidCount = 0
    }

    internal fun addRigidBody(b: Int) {
        if (rigidBodyCount == rigid.size) rigid = rigid.copyOf(max(4, rigid.size * 2))
        rigid[rigidBodyCount++] = b
    }

    internal fun addSoftBody(b: Int) {
        if (softBodyCount == soft.size) soft = soft.copyOf(max(4, soft.size * 2))
        soft[softBodyCount++] = b
    }

    internal fun addSolid(s: Solid) {
        if (solidCount == solidsFound.size) solidsFound = solidsFound.copyOf(max(4, solidsFound.size * 2))
        solidsFound[solidCount++] = s
    }
}

/**
 * Asks the world what is where: the first thing along a ray, everything at a point, everything in a
 * box. Rigid bodies by their shapes, soft bodies by their outlines (see [Outline]), and the solids by
 * their open faces. None of it allocates: the caller owns the [RayHit] or [Overlaps] it's written to.
 */
internal class Queries(private val world: PhysicsWorld) {
    // a rigid shape's core points, in world units, for a box query
    private val coreX = FloatArray(Shape.MAX_VERTICES)
    private val coreY = FloatArray(Shape.MAX_VERTICES)

    // the best hit so far, in the frame being tested
    private var bestT = 0f
    private var bestNx = 0f
    private var bestNy = 0f

    fun raycast(fromX: Float, fromY: Float, toX: Float, toY: Float, hit: RayHit, targets: QueryTargets): Boolean {
        hit.clear()
        val dx = toX - fromX
        val dy = toY - fromY
        if (QueryTargets.RIGID_BODIES in targets) rayRigid(fromX, fromY, dx, dy, hit)
        if (QueryTargets.SOFT_BODIES in targets) raySoft(fromX, fromY, dx, dy, hit)
        if (QueryTargets.SOLIDS in targets) raySolids(fromX, fromY, dx, dy, hit)
        if (hit.hit) {
            hit.x = fromX + dx * hit.fraction
            hit.y = fromY + dy * hit.fraction
        }
        return hit.hit
    }

    private fun rayRigid(ox: Float, oy: Float, dx: Float, dy: Float, hit: RayHit) {
        val bs = world.rigidBodies
        for (b in 0 until bs.end) {
            if (!bs.alive[b] || bs.sensor[b]) continue
            val shapes = bs.shapes[b] ?: continue
            // a circle round the body that the ray, as far as it still goes, misses
            if (!segmentNearCircle(ox, oy, dx * hit.fraction, dy * hit.fraction, bs.x[b], bs.y[b], bs.reach[b])) continue
            val c = bs.cos[b]
            val s = bs.sin[b]
            val lx = c * (ox - bs.x[b]) + s * (oy - bs.y[b])
            val ly = -s * (ox - bs.x[b]) + c * (oy - bs.y[b])
            val ldx = c * dx + s * dy
            val ldy = -s * dx + c * dy
            for ((i, shape) in shapes.withIndex()) {
                bestT = hit.fraction
                if (!rayShape(shape, lx, ly, ldx, ldy)) continue
                hit.what = HitKind.RIGID_BODY
                hit.body = b
                hit.shape = i
                hit.shapeOrNull = shape
                hit.solid = null
                hit.fraction = bestT
                hit.normalX = c * bestNx - s * bestNy
                hit.normalY = s * bestNx + c * bestNy
            }
        }
    }

    /**
     * Whether the ray from ([ox], [oy]) along ([dx], [dy]), in the shape's frame, enters [shape] before
     * [bestT]; if so, [bestT] and the normal are where. A ray that starts inside the shape doesn't hit it.
     */
    private fun rayShape(shape: Shape, ox: Float, oy: Float, dx: Float, dy: Float): Boolean {
        if (shape.distance(ox, oy) <= 0f) return false
        val vx = shape.vx
        val vy = shape.vy
        val nx = shape.nx
        val ny = shape.ny
        val n = vx.size
        val r = shape.radius
        if (r == 0f) {
            // a sharp polygon: clip the ray against every face; it's in between the last face it
            // enters and the first it leaves
            var enter = -1f
            var leave = Float.MAX_VALUE
            var face = -1
            for (i in nx.indices) {
                val toward = dx * nx[i] + dy * ny[i]
                val gap = (ox - vx[i]) * nx[i] + (oy - vy[i]) * ny[i]
                if (toward == 0f) {
                    if (gap > 0f) return false
                    continue
                }
                val t = -gap / toward
                if (toward < 0f) {
                    if (t > enter) {
                        enter = t
                        face = i
                    }
                } else {
                    leave = min(leave, t)
                }
            }
            if (face < 0 || enter < 0f || enter > leave || enter > bestT) return false
            bestT = enter
            bestNx = nx[face]
            bestNy = ny[face]
            return true
        }
        // a rounded one: its faces moved out by its radius, and a circle at each corner
        var found = false
        if (n >= 2) {
            for (i in nx.indices) {
                val toward = dx * nx[i] + dy * ny[i]
                if (toward >= 0f) continue
                val gap = (ox - vx[i]) * nx[i] + (oy - vy[i]) * ny[i] - r
                if (gap < 0f) continue
                val t = gap / -toward
                if (t > bestT) continue
                val j = if (i + 1 == n) 0 else i + 1
                // along the face, from its start: the tangent is the normal turned back a quarter
                val tx = -ny[i]
                val ty = nx[i]
                val along = (ox + dx * t - vx[i]) * tx + (oy + dy * t - vy[i]) * ty
                val length = (vx[j] - vx[i]) * tx + (vy[j] - vy[i]) * ty
                if (along < 0f || along > length) continue
                bestT = t
                bestNx = nx[i]
                bestNy = ny[i]
                found = true
            }
        }
        for (i in 0 until n) {
            val t = rayCircle(ox, oy, dx, dy, vx[i], vy[i], r)
            if (t < 0f || t > bestT) continue
            bestT = t
            val hx = ox + dx * t - vx[i]
            val hy = oy + dy * t - vy[i]
            val length = sqrt(hx * hx + hy * hy)
            bestNx = hx / length
            bestNy = hy / length
            found = true
        }
        return found
    }

    private fun raySoft(ox: Float, oy: Float, dx: Float, dy: Float, hit: RayHit) {
        val sb = world.softBodies
        for (b in 0 until sb.end) {
            if (!sb.isAlive(b)) continue
            val ring = sb.ring(b)
            val n = ring.size
            if (n < 3) continue
            if (!segmentNearBox(ox, oy, dx * hit.fraction, dy * hit.fraction, sb.minX(b), sb.minY(b), sb.maxX(b), sb.maxY(b))) continue
            if (Outline.contains(ring, ox, oy)) continue
            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                val ax = ring.xAt(i)
                val ay = ring.yAt(i)
                val t = raySegment(ox, oy, dx, dy, ax, ay, ring.xAt(j), ring.yAt(j))
                if (t < 0f || t > hit.fraction) continue
                hit.what = HitKind.SOFT_BODY
                hit.body = b
                hit.shape = -1
                hit.shapeOrNull = null
                hit.solid = null
                hit.fraction = t
                faceBack(ring.yAt(j) - ay, ax - ring.xAt(j), dx, dy, hit)
            }
        }
    }

    private fun raySolids(ox: Float, oy: Float, dx: Float, dy: Float, hit: RayHit) {
        val grid = world.solids ?: return
        val contacts = world.rigidContacts
        contacts.updateEdges(grid)
        val ed = contacts.edges
        val ex = ox + dx * hit.fraction
        val ey = oy + dy * hit.fraction
        ed.near(min(ox, ex), min(oy, ey), max(ox, ex), max(oy, ey))
        var face = -1
        for (k in 0 until ed.foundCount) {
            val e = ed.found[k]
            // only a face the ray comes at from outside
            if (dx * ed.nx[e] + dy * ed.ny[e] >= 0f) continue
            val t = raySegment(ox, oy, dx, dy, ed.x1[e], ed.y1[e], ed.x2[e], ed.y2[e])
            if (t < 0f || t > hit.fraction) continue
            hit.fraction = t
            face = e
        }
        if (face >= 0) {
            hit.what = HitKind.SOLID
            hit.body = -1
            hit.shape = -1
            hit.shapeOrNull = null
            hit.normalX = ed.nx[face]
            hit.normalY = ed.ny[face]
            hit.solid = solidBehind(grid, ox + dx * hit.fraction, oy + dy * hit.fraction, ed.nx[face], ed.ny[face])
        }
        for (s in grid.dynamic) {
            val t = rayBox(ox, oy, dx, dy, s.left, s.top, s.right, s.bottom, hit.fraction)
            if (t < 0f) continue
            hit.what = HitKind.SOLID
            hit.body = -1
            hit.shape = -1
            hit.shapeOrNull = null
            hit.solid = s
            hit.fraction = t
            hit.normalX = bestNx
            hit.normalY = bestNy
        }
    }

    /** The tile's solid just behind the point ([x], [y]) on a face with normal ([nx], [ny]). */
    private fun solidBehind(grid: SolidGrid, x: Float, y: Float, nx: Float, ny: Float): Solid? {
        val tile = grid.tileSize
        val inX = x - nx * tile * 0.01f
        val inY = y - ny * tile * 0.01f
        // on a tile's corner, the point may sit just past it along the face: try a little either way
        for (k in 0..2) {
            val along = tile * 0.01f * if (k == 0) 0f else if (k == 1) 1f else -1f
            val sx = inX - ny * along
            val sy = inY + nx * along
            val s = grid.at(floor(sx / tile).toInt(), floor(sy / tile).toInt())
            if (s != null) return s
        }
        return null
    }

    /**
     * Where along the ray from ([ox], [oy]) along ([dx], [dy]) it enters the box, no later than
     * [before], or -1; the face's normal is left in [bestNx], [bestNy]. A ray that starts inside misses.
     */
    private fun rayBox(ox: Float, oy: Float, dx: Float, dy: Float, left: Float, top: Float, right: Float, bottom: Float, before: Float): Float {
        if (ox > left && ox < right && oy > top && oy < bottom) return -1f
        var enter = 0f
        var leave = before
        var nx = 0f
        var ny = 0f
        if (dx == 0f) {
            if (ox < left || ox > right) return -1f
        } else {
            val t1 = (left - ox) / dx
            val t2 = (right - ox) / dx
            val near = min(t1, t2)
            if (near > enter) {
                enter = near
                nx = if (dx > 0f) -1f else 1f
                ny = 0f
            }
            leave = min(leave, max(t1, t2))
        }
        if (dy == 0f) {
            if (oy < top || oy > bottom) return -1f
        } else {
            val t1 = (top - oy) / dy
            val t2 = (bottom - oy) / dy
            val near = min(t1, t2)
            if (near > enter) {
                enter = near
                nx = 0f
                ny = if (dy > 0f) -1f else 1f
            }
            leave = min(leave, max(t1, t2))
        }
        if (enter > leave || nx == 0f && ny == 0f) return -1f
        bestNx = nx
        bestNy = ny
        return enter
    }

    /** Sets [hit]'s normal to ([nx], [ny]) made a unit long and turned to face the ray ([dx], [dy]). */
    private fun faceBack(nx: Float, ny: Float, dx: Float, dy: Float, hit: RayHit) {
        val length = sqrt(nx * nx + ny * ny)
        val sign = if (nx * dx + ny * dy > 0f) -1f else 1f
        hit.normalX = sign * nx / length
        hit.normalY = sign * ny / length
    }

    fun queryPoint(x: Float, y: Float, found: Overlaps, targets: QueryTargets): Int {
        found.clear()
        if (QueryTargets.RIGID_BODIES in targets) {
            val bs = world.rigidBodies
            for (b in 0 until bs.end) {
                if (!bs.alive[b] || bs.shapes[b] == null) continue
                val ddx = x - bs.x[b]
                val ddy = y - bs.y[b]
                val reach = bs.reach[b]
                if (ddx * ddx + ddy * ddy > reach * reach) continue
                if (bs.contains(b, x, y)) found.addRigidBody(b)
            }
        }
        if (QueryTargets.SOFT_BODIES in targets) {
            val sb = world.softBodies
            for (b in 0 until sb.end) {
                if (!sb.isAlive(b) || sb.ringCount(b) < 3) continue
                if (x < sb.minX(b) || x > sb.maxX(b) || y < sb.minY(b) || y > sb.maxY(b)) continue
                if (Outline.contains(sb.ring(b), x, y)) found.addSoftBody(b)
            }
        }
        if (QueryTargets.SOLIDS in targets) {
            val grid = world.solids
            if (grid != null) {
                val tile = grid.tileSize
                val s = grid.at(floor(x / tile).toInt(), floor(y / tile).toInt())
                if (s != null && s.contains(x, y)) found.addSolid(s)
                for (d in grid.dynamic) if (d.contains(x, y)) found.addSolid(d)
            }
        }
        return found.count
    }

    fun queryBox(left: Float, top: Float, right: Float, bottom: Float, found: Overlaps, targets: QueryTargets): Int {
        found.clear()
        if (left > right || top > bottom) return 0
        if (QueryTargets.RIGID_BODIES in targets) {
            val bs = world.rigidBodies
            for (b in 0 until bs.end) {
                if (!bs.alive[b]) continue
                val shapes = bs.shapes[b] ?: continue
                val cx = bs.x[b]
                val cy = bs.y[b]
                val reach = bs.reach[b]
                if (cx + reach < left || cx - reach > right || cy + reach < top || cy - reach > bottom) continue
                val c = bs.cos[b]
                val s = bs.sin[b]
                for (shape in shapes) {
                    val n = shape.vertexCount
                    for (i in 0 until n) {
                        coreX[i] = cx + c * shape.vx[i] - s * shape.vy[i]
                        coreY[i] = cy + s * shape.vx[i] + c * shape.vy[i]
                    }
                    if (coreOverlapsBox(n, shape.radius, left, top, right, bottom)) {
                        found.addRigidBody(b)
                        break
                    }
                }
            }
        }
        if (QueryTargets.SOFT_BODIES in targets) {
            val sb = world.softBodies
            for (b in 0 until sb.end) {
                if (!sb.isAlive(b) || sb.ringCount(b) < 3) continue
                if (sb.maxX(b) < left || sb.minX(b) > right || sb.maxY(b) < top || sb.minY(b) > bottom) continue
                if (ringOverlapsBox(sb.ring(b), left, top, right, bottom)) found.addSoftBody(b)
            }
        }
        if (QueryTargets.SOLIDS in targets) {
            val grid = world.solids
            if (grid != null) {
                val tile = grid.tileSize
                val col0 = max(0, floor(left / tile).toInt())
                val col1 = min(grid.columns - 1, floor(right / tile).toInt())
                val row0 = max(0, floor(top / tile).toInt())
                val row1 = min(grid.rows - 1, floor(bottom / tile).toInt())
                for (row in row0..row1) for (col in col0..col1) {
                    val s = grid.at(col, row) ?: continue
                    if (solidOverlapsBox(s, left, top, right, bottom)) found.addSolid(s)
                }
                for (d in grid.dynamic) if (solidOverlapsBox(d, left, top, right, bottom)) found.addSolid(d)
            }
        }
        return found.count
    }

    /**
     * Whether the convex core in [coreX], [coreY], [n] points, grown by [r], overlaps the box: the core
     * and the box overlap, by separating axes, or else they're less than [r] apart.
     */
    private fun coreOverlapsBox(n: Int, r: Float, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        var separated = false
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until n) {
            minX = min(minX, coreX[i])
            maxX = max(maxX, coreX[i])
            minY = min(minY, coreY[i])
            maxY = max(maxY, coreY[i])
        }
        if (maxX < left || minX > right || maxY < top || minY > bottom) separated = true
        if (!separated && n >= 2) {
            // the core's own faces: a segment has one, both ways round
            val faces = if (n == 2) 1 else n
            for (i in 0 until faces) {
                val j = if (i + 1 == n) 0 else i + 1
                val nx = coreY[j] - coreY[i]
                val ny = coreX[i] - coreX[j]
                var coreMin = Float.MAX_VALUE
                var coreMax = -Float.MAX_VALUE
                for (k in 0 until n) {
                    val p = coreX[k] * nx + coreY[k] * ny
                    coreMin = min(coreMin, p)
                    coreMax = max(coreMax, p)
                }
                val boxMin = min(left * nx, right * nx) + min(top * ny, bottom * ny)
                val boxMax = max(left * nx, right * nx) + max(top * ny, bottom * ny)
                if (coreMax < boxMin || coreMin > boxMax) {
                    separated = true
                    break
                }
            }
        }
        if (!separated) return true
        if (r == 0f) return false
        // apart: they're nearest at a point of one and a face of the other
        var nearest = Float.MAX_VALUE
        for (i in 0 until n) {
            val ddx = coreX[i] - coreX[i].coerceIn(left, right)
            val ddy = coreY[i] - coreY[i].coerceIn(top, bottom)
            nearest = min(nearest, ddx * ddx + ddy * ddy)
        }
        if (n >= 2) {
            val faces = if (n == 2) 1 else n
            for (i in 0 until faces) {
                val j = if (i + 1 == n) 0 else i + 1
                for (corner in 0 until 4) {
                    val px = if (corner == 1 || corner == 2) right else left
                    val py = if (corner >= 2) bottom else top
                    nearest = min(nearest, segmentDistanceSquared(px, py, coreX[i], coreY[i], coreX[j], coreY[j]))
                }
            }
        }
        return nearest < r * r
    }

    private fun ringOverlapsBox(ring: Ring, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        val n = ring.size
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            if (segmentNearBox(ring.xAt(i), ring.yAt(i), ring.xAt(j) - ring.xAt(i), ring.yAt(j) - ring.yAt(i), left, top, right, bottom)) return true
        }
        // no edge in it: the box is all inside the body, or all outside
        return Outline.contains(ring, (left + right) / 2f, (top + bottom) / 2f)
    }

    private fun solidOverlapsBox(s: Solid, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        if (s.right < left || s.left > right || s.bottom < top || s.top > bottom) return false
        return when (s.shape) {
            SolidShape.BOX -> true
            // the slope's surface is the third axis: the box's lowest corner along its normal must be above it
            SolidShape.RISING -> right - s.left >= s.bottom - bottom
            SolidShape.FALLING -> s.right - left >= s.bottom - bottom
        }
    }

    companion object {
        /** Whether the segment from ([ox], [oy]) along ([dx], [dy]) passes within [r] of ([cx], [cy]). */
        fun segmentNearCircle(ox: Float, oy: Float, dx: Float, dy: Float, cx: Float, cy: Float, r: Float): Boolean =
            segmentDistanceSquared(cx, cy, ox, oy, ox + dx, oy + dy) <= r * r

        /** Whether the segment from ([ox], [oy]) along ([dx], [dy]) touches the box, ends included. */
        fun segmentNearBox(ox: Float, oy: Float, dx: Float, dy: Float, left: Float, top: Float, right: Float, bottom: Float): Boolean {
            var enter = 0f
            var leave = 1f
            if (abs(dx) < 1e-12f) {
                if (ox < left || ox > right) return false
            } else {
                val t1 = (left - ox) / dx
                val t2 = (right - ox) / dx
                enter = max(enter, min(t1, t2))
                leave = min(leave, max(t1, t2))
            }
            if (abs(dy) < 1e-12f) {
                if (oy < top || oy > bottom) return false
            } else {
                val t1 = (top - oy) / dy
                val t2 = (bottom - oy) / dy
                enter = max(enter, min(t1, t2))
                leave = min(leave, max(t1, t2))
            }
            return enter <= leave
        }

        /**
         * How far along the ray from ([ox], [oy]) along ([dx], [dy]) it enters the circle at ([cx],
         * [cy]) of radius [r], or -1 if it doesn't, or starts inside it.
         */
        fun rayCircle(ox: Float, oy: Float, dx: Float, dy: Float, cx: Float, cy: Float, r: Float): Float {
            val mx = ox - cx
            val my = oy - cy
            val c = mx * mx + my * my - r * r
            if (c <= 0f) return -1f
            val a = dx * dx + dy * dy
            val b = mx * dx + my * dy
            if (b >= 0f || a == 0f) return -1f
            val disc = b * b - a * c
            if (disc < 0f) return -1f
            return (-b - sqrt(disc)) / a
        }

        /**
         * How far along the ray from ([ox], [oy]) along ([dx], [dy]) it crosses the segment from ([ax],
         * [ay]) to ([bx], [by]), or -1 if it doesn't, or runs along it.
         */
        fun raySegment(ox: Float, oy: Float, dx: Float, dy: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val ex = bx - ax
            val ey = by - ay
            val denominator = dx * ey - dy * ex
            if (denominator == 0f) return -1f
            val wx = ax - ox
            val wy = ay - oy
            val t = (wx * ey - wy * ex) / denominator
            val s = (wx * dy - wy * dx) / denominator
            if (t < 0f || s < 0f || s > 1f) return -1f
            return t
        }

        private fun segmentDistanceSquared(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val ex = bx - ax
            val ey = by - ay
            val lengthSquared = ex * ex + ey * ey
            val t = if (lengthSquared == 0f) 0f else (((px - ax) * ex + (py - ay) * ey) / lengthSquared).coerceIn(0f, 1f)
            val cx = ax + ex * t - px
            val cy = ay + ey * t - py
            return cx * cx + cy * cy
        }
    }
}
