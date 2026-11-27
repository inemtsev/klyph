package klyph.physics

import kotlin.math.floor
import kotlin.math.sqrt

private const val DIAGONAL = 0.70710678f

/**
 * A surface whose normal points up by more than this (y grows down, so the normal's y is below it)
 * counts as a floor. A 45 degree slope's normal is at -0.707, so a body standing on one is grounded.
 */
private const val MIN_FLOOR_NORMAL_Y = -0.5f

/**
 * A set of up to 32 tags a game gives its solids, such as "bounce pad" or "ice", one bit each.
 * [Events.floorTagsOf] reports the tags of the floors a particle stood on.
 *
 * Build a set from single tags with [of] and [plus]: `SolidTags.of(0) + SolidTags.of(3)`. [bits]
 * holds tag `i` in bit `i`. [toString] lists the tag numbers in ascending order, as
 * `SolidTags(0, 3)`, and [NONE] prints as `SolidTags()`.
 *
 * @sample samples.physics.SolidSamples.solidTags
 */
public value class SolidTags(
    /** The tags as bits: tag `i` is in the set when bit `i` is set. */
    public val bits: Int,
) {
    /** Returns the set of tags that are in this set, in [other], or in both. */
    public operator fun plus(other: SolidTags): SolidTags = SolidTags(bits or other.bits)

    /** Returns `true` if this set and [other] have at least one tag in common. */
    public fun intersects(other: SolidTags): Boolean = bits and other.bits != 0

    override fun toString(): String {
        val builder = StringBuilder("SolidTags(")
        var first = true
        for (index in 0 until Int.SIZE_BITS) {
            if (bits and (1 shl index) == 0) continue
            if (!first) builder.append(", ")
            builder.append(index)
            first = false
        }
        return builder.append(')').toString()
    }

    /** Holds the empty set and builds the set of a single tag. */
    public companion object {
        /** No tag: what a solid carries unless it is given some. */
        public val NONE: SolidTags = SolidTags(0)

        /**
         * Returns the set holding only tag [index], which must be between 0 and 31.
         *
         * @throws IllegalArgumentException if [index] is outside 0..31.
         */
        public fun of(index: Int): SolidTags {
            require(index in 0 until Int.SIZE_BITS) { "Solid tag $index is not between 0 and 31." }
            return SolidTags(1 shl index)
        }
    }
}

/** How a [Solid] fills its rectangle: all of it, or the half under a diagonal surface. */
public enum class SolidShape {
    /** The whole rectangle. */
    BOX,

    /** The triangle under a surface that rises to the right, from the bottom-left corner to the top-right. */
    RISING,

    /** The triangle under a surface that falls to the right, from the top-left corner to the bottom-right. */
    FALLING,
}

/**
 * Something a particle cannot enter: a rectangle with the given edges (y grows down) filled as
 * [shape] says. A tile's solid knows its grid cell, so the grid can tell which faces are buried
 * against a neighbour; a [moving] one has none. Make a tile's solid with [SolidGrid.fill].
 *
 * [toString] gives its shape, edges and cell, for example
 * `Solid(BOX, left=16.0, top=32.0, right=32.0, bottom=48.0, column=1, row=2)`.
 */
public class Solid internal constructor(
    /** Which part of its box is solid: all of it, or a slope. */
    public val shape: SolidShape,
    /** Its box: the left edge. */
    public val left: Float,
    /** Its box: the top edge. A moving solid's moves. */
    public var top: Float,
    /** Its box: the right edge. */
    public val right: Float,
    /** Its box: the bottom edge. A moving solid's moves. */
    public var bottom: Float,
    /** Tags the game gives this solid, such as "bounce pad", reported by [Events.floorTagsOf] when a particle stands on it. */
    public val tags: SolidTags = SolidTags.NONE,
    /** The tile's column in its grid, or -1 for a moving solid. */
    public val column: Int = -1,
    /** The tile's row in its grid, or -1 for a moving solid. */
    public val row: Int = -1,
    /** How it grips and bounces what touches it. */
    public var material: Material = Material.DEFAULT,
) {
    /** `true` if this solid moves, such as a crusher, rather than filling a grid cell. It has no neighbours to bury its faces against. */
    public val isMoving: Boolean get() = column < 0

    /**
     * Returns `true` if the point ([x], [y]) is inside this solid by more than [margin] units, measured
     * straight out of the nearest face (the slope's own surface, for a slope). A point on the surface is
     * outside. This is the one place that decides what "inside" means: collision and
     * [SolidGrid.isSolidAt] both ask it.
     *
     * @sample samples.physics.SolidSamples.contains
     */
    public fun contains(x: Float, y: Float, margin: Float = 0f): Boolean = when (shape) {
        SolidShape.BOX -> x > left + margin && x < right - margin && y > top + margin && y < bottom - margin
        SolidShape.RISING -> isWithinEdges(x, y) && ((x - left) - (bottom - y)) * DIAGONAL > margin
        SolidShape.FALLING -> isWithinEdges(x, y) && ((right - left) - (x - left) - (bottom - y)) * DIAGONAL > margin
    }

    private fun isWithinEdges(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom

    /**
     * Whether a particle of radius [r] centred at ([x], [y]) reaches into this solid: its centre is inside
     * the solid grown by [r]. Corners grow square rather than round, which is close enough at the size
     * particles are.
     */
    internal fun overlaps(x: Float, y: Float, r: Float): Boolean {
        if (r == 0f) return contains(x, y)
        if (x <= left - r || x >= right + r || y <= top - r || y >= bottom + r) return false
        return when (shape) {
            SolidShape.BOX -> true
            SolidShape.RISING -> ((x - left) - (bottom - y)) * DIAGONAL > -r
            SolidShape.FALLING -> ((right - left) - (x - left) - (bottom - y)) * DIAGONAL > -r
        }
    }

    override fun toString(): String = "Solid($shape, left=$left, top=$top, right=$right, bottom=$bottom, column=$column, row=$row)"

    /** Builds solids that move. */
    public companion object {
        /**
         * Returns a new solid that is not tied to a grid cell, filling the given edges, for
         * [SolidGrid.add]. Its [top] and [bottom] may change as it moves. Pass the edges by name.
         *
         * @throws IllegalArgumentException if [right] is not more than [left] or [bottom] not more than [top].
         *
         * @sample samples.physics.SolidSamples.moving
         */
        public fun moving(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            tags: SolidTags = SolidTags.NONE,
            material: Material = Material.DEFAULT,
        ): Solid {
            require(right > left && bottom > top) { "Solid edges left=$left, top=$top, right=$right, bottom=$bottom make no box." }
            return Solid(SolidShape.BOX, left, top, right, bottom, tags, material = material)
        }
    }
}

/**
 * A level's solids: at most one per cell of a grid of [columns] by [rows] cells, each [tileSize]
 * wide, plus [moving] ones. Set it as [PhysicsWorld.solids] and every particle is pushed out of it:
 * a point particle can only be inside the one tile it is in, so only that tile and the moving solids
 * are checked; a round one checks the tiles its radius reaches.
 *
 * [toString] gives its size, for example `SolidGrid(40x30, tileSize=16.0, moving=1)`.
 *
 * @throws IllegalArgumentException if [columns] or [rows] is not more than 0, or [tileSize] is not more than 0.
 *
 * @sample samples.physics.SolidSamples.solidGrid
 */
public class SolidGrid(
    /** Number of cells across. */
    public val columns: Int,
    /** Number of cells down. */
    public val rows: Int,
    /** Width and height of one cell, in world units. */
    public val tileSize: Float,
) {
    init {
        require(columns > 0 && rows > 0) { "Solid grid size ${columns}x$rows is not more than 0 in both directions." }
        require(tileSize > 0f) { "Tile size $tileSize is not more than 0." }
    }

    private val cells = arrayOfNulls<Solid>(columns * rows)
    private val invTile = 1f / tileSize
    private val maxNearRadius = tileSize * 0.999f
    private val near = BooleanArray(columns * rows)
    private var nearVersion = -1

    internal val dynamic: ArrayList<Solid> = ArrayList()

    /** Solids that are not tied to a cell, such as a crusher, in the order they were added. Checked every time. */
    public val moving: List<Solid> get() = dynamic

    /**
     * Adds [solid], made with [Solid.moving], to the solids that move. Adding one already there does nothing.
     *
     * @throws IllegalArgumentException if [solid] belongs to a grid cell.
     */
    public fun add(solid: Solid) {
        require(solid.isMoving) { "Solid $solid belongs to a grid cell; make a moving one with Solid.moving." }
        if (solid !in dynamic) dynamic.add(solid)
    }

    /** Removes [solid] from the solids that move. Removing one that is not there does nothing. */
    public fun remove(solid: Solid) {
        dynamic.remove(solid)
    }

    /** Counts every change to the cells, so what's built from them, such as the edges rigid bodies meet, knows to build again. */
    internal var version = 0
        private set

    /** Returns the solid in cell ([column], [row]), or `null` for air and for cells outside the grid. */
    public fun atOrNull(column: Int, row: Int): Solid? =
        if (column in 0 until columns && row in 0 until rows) cells[row * columns + column] else null

    internal fun at(col: Int, row: Int): Solid? = atOrNull(col, row)

    /**
     * Fills cell ([column], [row]) with a solid of [shape] carrying [tags] and [material], and returns
     * it. A solid already in the cell is replaced.
     *
     * @throws IllegalArgumentException if the cell is outside the grid.
     *
     * @sample samples.physics.SolidSamples.solidGrid
     */
    public fun fill(
        column: Int,
        row: Int,
        shape: SolidShape = SolidShape.BOX,
        tags: SolidTags = SolidTags.NONE,
        material: Material = Material.DEFAULT,
    ): Solid {
        val t = tileSize
        return put(Solid(shape, column * t, row * t, (column + 1) * t, (row + 1) * t, tags, column, row, material))
    }

    /**
     * Fills only the top half of cell ([column], [row]), such as a stub hanging from a ceiling, and
     * returns the solid.
     *
     * @throws IllegalArgumentException if the cell is outside the grid.
     */
    public fun fillTopHalf(column: Int, row: Int, tags: SolidTags = SolidTags.NONE, material: Material = Material.DEFAULT): Solid {
        val t = tileSize
        return put(Solid(SolidShape.BOX, column * t, row * t, (column + 1) * t, row * t + t / 2f, tags, column, row, material))
    }

    private fun put(solid: Solid): Solid {
        require(solid.column in 0 until columns && solid.row in 0 until rows) {
            "Cell (${solid.column}, ${solid.row}) is outside the ${columns}x$rows grid."
        }
        cells[solid.row * columns + solid.column] = solid
        version++
        return solid
    }

    /** Empties cell ([column], [row]). Emptying a cell that is air or outside the grid does nothing. */
    public fun removeAt(column: Int, row: Int) {
        if (column !in 0 until columns || row !in 0 until rows || cells[row * columns + column] == null) return
        cells[row * columns + column] = null
        version++
    }

    override fun toString(): String = "SolidGrid(${columns}x$rows, tileSize=$tileSize, moving=${dynamic.size})"

    /**
     * Returns `true` if the point ([x], [y]) is inside any solid by more than [margin] units; see
     * [Solid.contains].
     *
     * @sample samples.physics.SolidSamples.solidGrid
     */
    public fun isSolidAt(x: Float, y: Float, margin: Float = 0f): Boolean {
        val solid = at(floor(x / tileSize).toInt(), floor(y / tileSize).toInt())
        if (solid != null && solid.contains(x, y, margin)) return true
        for (d in dynamic) if (d.contains(x, y, margin)) return true
        return false
    }

    /**
     * Pushes every particle of [body] out of the solids, along the way of least penetration. The
     * speed going into the surface is removed and the rest kept, so particles slide along walls and
     * floors. Faces buried against a neighbouring solid are never exits, so a floor made of many
     * tiles has no seams.
     */
    internal fun collide(particles: Particles, events: Events, restingMove: Float = 0f) {
        if (nearVersion != version) refreshNear()
        val x = particles.x
        val y = particles.y
        val passing = particles.anyPassSolids
        val hasDynamic = dynamic.isNotEmpty()
        for (p in 0 until particles.end) {
            if (particles.invMass[p] == 0f || passing && particles.flags[p] and Particles.PASSES_SOLIDS != 0) continue
            // a soft body's particles look a little further, as near a solid they touch it, for events
            val r = particles.radius[p] + events.touchReach(particles.owner[p])
            if (!isFarFromGrid(x[p], y[p], r)) collideGrid(particles, p, r, events, restingMove)
            if (hasDynamic) {
                for (k in 0 until dynamic.size) push(particles, p, dynamic[k], events, restingMove)
            }
        }
    }

    /** Whether a particle at ([px], [py]) reaching [r] out cannot overlap any filled grid cell. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun isFarFromGrid(px: Float, py: Float, r: Float): Boolean {
        if (r >= maxNearRadius) return false
        val gx = px * invTile
        val gy = py * invTile
        val inX = gx >= 0f && gx < columns
        val inY = gy >= 0f && gy < rows
        return inX && inY && !near[gy.toInt() * columns + gx.toInt()]
    }

    private fun collideGrid(particles: Particles, p: Int, r: Float, events: Events, restingMove: Float) {
        val tile = tileSize
        val px = particles.x[p]
        val py = particles.y[p]
        if (r == 0f) {
            val solid = at(floor(px / tile).toInt(), floor(py / tile).toInt())
            if (solid != null) push(particles, p, solid, events, restingMove)
            return
        }
        // A round particle can reach into the tiles around the one its centre is in.
        val col0 = floor((px - r) / tile).toInt()
        val col1 = floor((px + r) / tile).toInt()
        val row0 = floor((py - r) / tile).toInt()
        val row1 = floor((py + r) / tile).toInt()
        for (row in row0..row1) for (col in col0..col1) {
            val solid = at(col, row) ?: continue
            push(particles, p, solid, events, restingMove)
        }
    }

    /** Marks every tile that holds a solid or sits beside one, so open-air particles skip tile lookups. */
    private fun refreshNear() {
        near.fill(false)
        for (row in 0 until rows) for (col in 0 until columns) {
            if (cells[row * columns + col] != null) markNear(col, row)
        }
        nearVersion = version
    }

    private fun markNear(col: Int, row: Int) {
        val r0 = if (row > 0) row - 1 else 0
        val r1 = if (row + 1 < rows) row + 1 else rows - 1
        val c0 = if (col > 0) col - 1 else 0
        val c1 = if (col + 1 < columns) col + 1 else columns - 1
        for (nr in r0..r1) for (nc in c0..c1) near[nr * columns + nc] = true
    }

    /**
     * Pushes particle [p] out of [solid] if it reaches into it. Every way out is worked out for the
     * particle's centre, then its radius is added, so it ends up a radius clear of the surface.
     */
    private fun push(particles: Particles, p: Int, solid: Solid, events: Events, restingMove: Float) {
        val px = particles.x[p]
        val py = particles.y[p]
        val r = particles.radius[p]
        val owner = particles.owner[p]
        if (events.records(owner) && solid.overlaps(px, py, r + Events.TOUCH_SLOP)) {
            events.touch(TouchKind.SOFT_BODY, owner, TouchKind.SOLIDS, -1)
        }
        if (!solid.overlaps(px, py, r)) return
        val exit = exitScratch
        when (solid.shape) {
            SolidShape.BOX -> exitFromBox(solid, px, py, exit)
            SolidShape.RISING -> exitFromRising(solid, px, py, exit)
            SolidShape.FALLING -> exitFromFalling(solid, px, py, exit)
        }
        if (exit.depth == Float.MAX_VALUE) return
        val m = solid.material
        particles.pushOut(
            p, exit.nx, exit.ny, exit.depth + r,
            friction = Material.mixFriction(particles.friction[p], m.friction),
            staticFriction = Material.mixFriction(particles.staticFriction[p], m.staticFriction),
            restitution = Material.mixRestitution(particles.restitution[p], m.restitution),
            restingMove = restingMove,
        )
        if (exit.ny < MIN_FLOOR_NORMAL_Y) events.recordFloor(p, solid.tags.bits)
    }

    /** Writes to [exit] the cheapest way out of the box for a point inside it. */
    private fun exitFromBox(solid: Solid, px: Float, py: Float, exit: Exit) {
        nearestExit(solid, px, py, respectBuried = true)
        // Where a step meets a wall, both faces at the corner are buried, yet just past the corner is air.
        // A point just inside leaves through that corner, not through the far side of the wall.
        if (!solid.isMoving) {
            tryCorner(solid, -1, -1, px - solid.left, py - solid.top, exit)
            tryCorner(solid, 1, -1, solid.right - px, py - solid.top, exit)
            tryCorner(solid, -1, 1, px - solid.left, solid.bottom - py, exit)
            tryCorner(solid, 1, 1, solid.right - px, solid.bottom - py, exit)
            trySlopeAbove(solid, px, py, exit)
        }
        // Buried on every side, such as under the foot of a wall: step toward the nearest face
        // anyway, just past it, and the tile there pushes the particle the rest of the way out.
        if (exit.depth == Float.MAX_VALUE) {
            nearestExit(solid, px, py, respectBuried = false)
            exit.depth += 0.01f
        }
        // A tile with nowhere to go at all, such as the only cell of a one-cell grid, keeps the
        // impossible depth, and the particle is left be.
    }

    /** Writes to [exit] the way out, along the surface's normal, for a point under a surface that rises to the right. */
    private fun exitFromRising(solid: Solid, px: Float, py: Float, exit: Exit) {
        val u = px - solid.left
        val v = solid.bottom - py
        exit.depth = (u - v) * DIAGONAL
        exit.nx = -DIAGONAL
        exit.ny = -DIAGONAL
        // A slope in mid-air can be hit from below or from its tall side, and the way back out is
        // shorter there than up through the surface: on top of it is the wrong side.
        tryFace(solid, Side.BOTTOM, solid.bottom - py, 0f, 1f, exit)
        tryFace(solid, Side.RIGHT, solid.right - px, 1f, 0f, exit)
    }

    /**
     * Takes the corner of a tile's [solid] toward ([dx], [dy]), each -1 or 1, as the way out if the cell
     * diagonally across it is air inside the grid and the corner, [toX] and [toY] away along each axis,
     * is closer than [exit]'s. A corner is never closer than an open face beside it, so it only wins
     * where both of those faces are buried.
     */
    private fun tryCorner(solid: Solid, dx: Int, dy: Int, toX: Float, toY: Float, exit: Exit) {
        val col = solid.column + dx
        val row = solid.row + dy
        // Only from inside: a round particle reaching in from outside the corner has a face to leave by.
        if (toX <= 0f || toY <= 0f) return
        if (col !in 0 until columns || row !in 0 until rows || at(col, row) != null) return
        val depth = sqrt(toX * toX + toY * toY)
        if (depth >= exit.depth || depth < 1e-6f) return
        exit.depth = depth
        exit.nx = dx * toX / depth
        exit.ny = dy * toY / depth
    }

    /**
     * Under a ramp, the box below a slope tile meets the slope tile beside it at a seam, the box's top
     * corner. A particle that dips just under the surface there is in the box, with every face buried,
     * and the corner would push it out sideways, downhill, up to a tile's width from the real surface.
     * Where the slope above carries on through the seam into the tile beside the box, the way out is
     * along the slope's normal, to its line, as if the particle were still in the slope tile.
     */
    private fun trySlopeAbove(solid: Solid, px: Float, py: Float, exit: Exit) {
        val above = at(solid.column, solid.row - 1) ?: return
        val depth: Float
        val nx: Float
        when (above.shape) {
            SolidShape.BOX -> return
            SolidShape.RISING -> {
                if (at(solid.column - 1, solid.row)?.shape != SolidShape.RISING) return
                depth = ((px - above.left) - (above.bottom - py)) * DIAGONAL
                nx = -DIAGONAL
            }
            SolidShape.FALLING -> {
                if (at(solid.column + 1, solid.row)?.shape != SolidShape.FALLING) return
                depth = ((above.right - px) - (above.bottom - py)) * DIAGONAL
                nx = DIAGONAL
            }
        }
        if (depth >= exit.depth) return
        exit.depth = depth
        exit.nx = nx
        exit.ny = -DIAGONAL
    }

    /** Takes the [side] face as the way out, along ([nx], [ny]), if it is open and closer than [exit]'s. */
    private fun tryFace(solid: Solid, side: Side, depth: Float, nx: Float, ny: Float, exit: Exit) {
        if (depth < exit.depth && isExit(solid, side, respectBuried = true)) {
            exit.depth = depth
            exit.nx = nx
            exit.ny = ny
        }
    }

    /** Writes to [exit] the way out, along the surface's normal, for a point under a surface that falls to the right. */
    private fun exitFromFalling(solid: Solid, px: Float, py: Float, exit: Exit) {
        val u = px - solid.left
        val v = solid.bottom - py
        val side = solid.right - solid.left
        exit.depth = (side - u - v) * DIAGONAL
        exit.nx = DIAGONAL
        exit.ny = -DIAGONAL
        tryFace(solid, Side.BOTTOM, solid.bottom - py, 0f, 1f, exit)
        tryFace(solid, Side.LEFT, px - solid.left, -1f, 0f, exit)
    }

    /** The way out of a box that costs the least movement, reused between calls so nothing allocates. */
    private class Exit {
        var depth = Float.MAX_VALUE
        var nx = 0f
        var ny = 0f
    }

    private val exitScratch = Exit()

    /** Writes to the scratch [Exit] the face of a box that is cheapest to leave through, skipping buried faces when asked. */
    private fun nearestExit(solid: Solid, px: Float, py: Float, respectBuried: Boolean) {
        val exit = exitScratch
        exit.depth = Float.MAX_VALUE
        exit.nx = 0f
        exit.ny = 0f
        val toLeft = px - solid.left
        if (toLeft < exit.depth && isExit(solid, Side.LEFT, respectBuried)) { exit.depth = toLeft; exit.nx = -1f; exit.ny = 0f }
        val toRight = solid.right - px
        if (toRight < exit.depth && isExit(solid, Side.RIGHT, respectBuried)) { exit.depth = toRight; exit.nx = 1f; exit.ny = 0f }
        val toTop = py - solid.top
        if (toTop < exit.depth && isExit(solid, Side.TOP, respectBuried)) { exit.depth = toTop; exit.nx = 0f; exit.ny = -1f }
        val toBottom = solid.bottom - py
        if (toBottom < exit.depth && isExit(solid, Side.BOTTOM, respectBuried)) { exit.depth = toBottom; exit.nx = 0f; exit.ny = 1f }
    }

    /**
     * Whether a particle may leave [solid] through its [side]: never through the edge of the grid,
     * where there is nothing to land on and nothing to push it back, and, when [respectBuried], not
     * through a face buried against a neighbour.
     */
    private fun isExit(solid: Solid, side: Side, respectBuried: Boolean): Boolean =
        !isOnGridEdge(solid, side) && !(respectBuried && isBuried(solid, side))

    /** Whether the [side] face of a tile's [solid] lies on the edge of the grid. A moving solid's never does. */
    private fun isOnGridEdge(solid: Solid, side: Side): Boolean {
        if (solid.isMoving) return false
        return when (side) {
            Side.LEFT -> solid.column == 0
            Side.RIGHT -> solid.column == columns - 1
            Side.TOP -> solid.row == 0
            Side.BOTTOM -> solid.row == rows - 1
        }
    }

    private enum class Side { LEFT, RIGHT, TOP, BOTTOM }

    /** Whether the [side] face of a tile's [solid] lies against a neighbour that fills the whole face. */
    private fun isBuried(solid: Solid, side: Side): Boolean {
        if (solid.isMoving) return false
        val neighbour = when (side) {
            Side.LEFT -> at(solid.column - 1, solid.row)
            Side.RIGHT -> at(solid.column + 1, solid.row)
            Side.TOP -> at(solid.column, solid.row - 1)
            Side.BOTTOM -> at(solid.column, solid.row + 1)
        } ?: return false
        return when (side) {
            Side.LEFT -> neighbour.shape != SolidShape.FALLING && spansHeight(neighbour, solid) && neighbour.right == solid.left
            Side.RIGHT -> neighbour.shape != SolidShape.RISING && spansHeight(neighbour, solid) && neighbour.left == solid.right
            Side.TOP -> neighbour.bottom == solid.top && neighbour.left <= solid.left && neighbour.right >= solid.right
            Side.BOTTOM -> neighbour.shape == SolidShape.BOX && neighbour.top == solid.bottom &&
                neighbour.left <= solid.left && neighbour.right >= solid.right
        }
    }

    private fun spansHeight(neighbour: Solid, solid: Solid): Boolean = neighbour.top <= solid.top && neighbour.bottom >= solid.bottom
}
