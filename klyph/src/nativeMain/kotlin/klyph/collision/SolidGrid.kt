package klyph.collision

import klyph.entity.ComponentStore
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.scene.SceneStack
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The [Solid]s of a world in a uniform grid of their own, the sweep that finds the first solid a
 * moving box enters, and the [KinematicBody] move built on it. [Collisions] owns one and forwards
 * its public sweeps and its body moves here.
 *
 * [refresh] gathers every solid's box into flat arrays, comparing each with what was there, and
 * rebuilds the grid only when something differs, so a level that does not change costs one pass
 * over its solids per refresh and no rebuild. Between refreshes [ensureCurrent] notices, by the
 * version counters of the [Solid] and [Collider] stores, that a solid or a collider was added,
 * replaced or removed, and refreshes before a sweep reads the grid.
 *
 * A sweep treats the moving box as a point against every solid grown by the box's half size (the
 * Minkowski sum of the two boxes), which gives the exact time of impact per axis. It visits the
 * grid's cells in the order the moving box reaches them, a thick-ray version of the traversal of
 * Amanatides and Woo ("A Fast Voxel Traversal Algorithm for Ray Tracing", 1987): every cell the
 * box's leading edges enter adds a column or a row of cells across the box. A solid in a cell the
 * box reaches at time t cannot be entered before t, so the walk stops once the next cell is
 * reached after the best hit so far. A stamp per solid tests a solid in several cells once.
 *
 * Not thread-safe. Allocates nothing once its arrays fit the largest level, besides the hits the
 * public sweeps return.
 */
internal class SolidGrid {
    /** The world and scenes of the last [refresh], which [ensureCurrent] refreshes against. */
    private var world: World? = null
    private var scenes: SceneStack? = null
    private var solidVersion = -1
    private var colliderVersion = -1

    /** The number of solids in the grid. */
    var solidCount: Int = 0
        private set

    // One entry per solid: its raw entity handle, its box (BOX floats: min x, min y, max x, max y),
    // its collider's layer bits and whether it is one-way.
    private var entities = LongArray(INITIAL_SOLIDS)
    private var boxes = FloatArray(INITIAL_SOLIDS * BOX)
    private var layerBits = IntArray(INITIAL_SOLIDS)
    private var isOneWay = BooleanArray(INITIAL_SOLIDS)

    /** The test stamp of each solid, so a solid in several cells is tested once per sweep. */
    private var stamps = IntArray(INITIAL_SOLIDS)
    private var stamp = 0

    // The grid. Cells are numbered in first-touched order; cellKeys holds each cell's packed (x, y)
    // coordinates and cellStarts its first position in cellSolids, cellStarts[cellCount] the end.
    private var cellSize = 0f
    private var inverseCell = 0f
    private var cellCount = 0
    private var cellKeys = LongArray(64)
    private var cellCursors = IntArray(64)
    private var cellStarts = IntArray(65)
    private var cellSolids = IntArray(INITIAL_SOLIDS)

    // Open-addressing hash table from cell key to cell number, at most half full; -1 marks a free slot.
    private var table = IntArray(128) { -1 }

    /** Solids covering more than [LARGE_CELLS] cells, kept out of the grid and tested by every sweep. */
    private var large = IntArray(16)
    private var largeCount = 0

    // One entry per (solid, cell) pair while the grid is built.
    private var entryCells = IntArray(INITIAL_SOLIDS)
    private var entrySolids = IntArray(INITIAL_SOLIDS)

    // The sweep: the moving box at its start, its motion and filters, and the best hit so far.
    private var sweepMinX = 0f
    private var sweepMinY = 0f
    private var sweepMaxX = 0f
    private var sweepMaxY = 0f
    private var sweepDx = 0f
    private var sweepDy = 0f
    private var sweepMask = 0
    private var sweepSkipsOverlaps = false
    private var sweepDropLine = Float.NEGATIVE_INFINITY
    private var sweepCollectsAll = false

    /** The solid of the best hit of the last sweep, or -1 when it hit nothing. */
    var hitSolid: Int = -1
        private set
    var hitFraction: Float = 0f
        private set
    var hitNormalX: Float = 0f
        private set
    var hitNormalY: Float = 0f
        private set

    /** Every hit of a sweep that collects all, in the order the walk finds them. */
    private val collected = ArrayList<SweepHit>()

    /**
     * Gathers every [Solid] of [world], except those owned by a scene [scenes] holds frozen, and
     * rebuilds the grid with cells [cellSize] across if any solid or the cell size changed.
     *
     * @throws IllegalStateException if a solid has no [Collider] or a collider that is not a box.
     */
    fun refresh(world: World, scenes: SceneStack?, cellSize: Float) {
        this.world = world
        this.scenes = scenes
        val solids = world.storeOrNull(Solid)
        val colliders = world.storeOrNull(Collider)
        solidVersion = solids?.version ?: -1
        colliderVersion = colliders?.version ?: -1
        var changed = cellSize != this.cellSize
        var count = 0
        if (solids != null && solids.size > 0) {
            ensureSolidCapacity(solids.size)
            val locals = world.storeOrNull(Transform)
            val worlds = world.storeOrNull(WorldTransform)
            val frozen = if (scenes != null && scenes.hasFrozen) scenes else null
            for (k in 0 until solids.size) {
                val raw = solids.entities[k]
                val entity = Entity(raw)
                if (frozen != null && frozen.isFrozen(world.ownerAt(entity.index))) continue
                val collider = colliders?.getOrNull(entity)
                checkNotNull(collider) { "Entity $entity has a Solid but no Collider." }
                val shape = collider.shape
                check(shape is Shape.Box) { "Entity $entity has a Solid whose collider shape $shape is not a Box." }
                val x: Float
                val y: Float
                val scaleX: Float
                val scaleY: Float
                val worldTransform = worlds?.getOrNull(entity)
                if (worldTransform != null) {
                    x = worldTransform.x
                    y = worldTransform.y
                    scaleX = worldTransform.scaleX
                    scaleY = worldTransform.scaleY
                } else {
                    val transform = locals?.getOrNull(entity) ?: continue
                    x = transform.x
                    y = transform.y
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                }
                val sx = if (collider.scalesWithTransform) scaleX else 1f
                val sy = if (collider.scalesWithTransform) scaleY else 1f
                val centerX = x + collider.offsetX * sx
                val centerY = y + collider.offsetY * sy
                val halfWidth = shape.width * abs(sx) * 0.5f
                val halfHeight = shape.height * abs(sy) * 0.5f
                val minX = centerX - halfWidth
                val minY = centerY - halfHeight
                val maxX = centerX + halfWidth
                val maxY = centerY + halfHeight
                if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) continue
                val layers = collider.layers.bits
                val oneWay = solids.items[k].let { (it as Solid).passable == Passable.FROM_BELOW }
                val o = count * BOX
                if (!changed && (count >= solidCount || entities[count] != raw || boxes[o] != minX ||
                        boxes[o + 1] != minY || boxes[o + 2] != maxX || boxes[o + 3] != maxY ||
                        layerBits[count] != layers || isOneWay[count] != oneWay)
                ) {
                    changed = true
                }
                entities[count] = raw
                boxes[o] = minX
                boxes[o + 1] = minY
                boxes[o + 2] = maxX
                boxes[o + 3] = maxY
                layerBits[count] = layers
                isOneWay[count] = oneWay
                count++
            }
        }
        if (count != solidCount) changed = true
        solidCount = count
        if (changed) build(cellSize)
    }

    /** Refreshes against the world of the last [refresh] if a solid or a collider was added, replaced or removed since. */
    fun ensureCurrent(cellSize: Float) {
        val world = world ?: return
        val solids = world.storeOrNull(Solid)
        val colliders = world.storeOrNull(Collider)
        if ((solids?.version ?: -1) != solidVersion || (colliders?.version ?: -1) != colliderVersion || cellSize != this.cellSize) {
            refresh(world, scenes, cellSize)
        }
    }

    /** Sorts the solids into cells with a counting sort: count per cell, prefix sums, scatter. */
    private fun build(cellSize: Float) {
        this.cellSize = cellSize
        val inverse = 1f / cellSize
        inverseCell = inverse
        table.fill(-1)
        cellCount = 0
        largeCount = 0
        var entryCount = 0
        for (s in 0 until solidCount) {
            val o = s * BOX
            val x0 = cellOf(boxes[o], inverse)
            val y0 = cellOf(boxes[o + 1], inverse)
            val x1 = cellOf(boxes[o + 2], inverse)
            val y1 = cellOf(boxes[o + 3], inverse)
            val cells = (x1.toLong() - x0 + 1) * (y1.toLong() - y0 + 1)
            if (cells > LARGE_CELLS) {
                if (largeCount == large.size) large = large.copyOf(largeCount * 2)
                large[largeCount++] = s
                continue
            }
            ensureEntryCapacity(entryCount + cells.toInt())
            for (cy in y0..y1) {
                for (cx in x0..x1) {
                    val cell = cellOrAdd(cx, cy)
                    cellCursors[cell]++
                    entryCells[entryCount] = cell
                    entrySolids[entryCount] = s
                    entryCount++
                }
            }
        }
        if (cellStarts.size < cellCount + 1) cellStarts = IntArray(cellKeys.size + 1)
        var start = 0
        for (cell in 0 until cellCount) {
            val size = cellCursors[cell]
            cellStarts[cell] = start
            cellCursors[cell] = start
            start += size
        }
        cellStarts[cellCount] = start
        if (cellSolids.size < entryCount) cellSolids = IntArray(entrySolids.size)
        for (e in 0 until entryCount) cellSolids[cellCursors[entryCells[e]]++] = entrySolids[e]
    }

    /**
     * Sweeps the box from ([minX], [minY]) to ([maxX], [maxY]) by ([dx], [dy]) against the solids on
     * a layer in [mask] and records the earliest hit in [hitSolid], [hitFraction], [hitNormalX] and
     * [hitNormalY]. Returns whether there was one. Ties go to the solid with the smaller entity index.
     *
     * A solid the box overlaps at the start is a hit at fraction 0 with the normal (0, 0), unless
     * [skipsOverlaps] is set, as for a body, which then moves through it. One-way solids follow
     * [blocksOneWay], and those whose top is at or above [dropLine] are ignored.
     */
    fun sweep(
        minX: Float, minY: Float, maxX: Float, maxY: Float,
        dx: Float, dy: Float,
        mask: Int,
        skipsOverlaps: Boolean,
        dropLine: Float = Float.NEGATIVE_INFINITY,
    ): Boolean {
        sweepCollectsAll = false
        walk(minX, minY, maxX, maxY, dx, dy, mask, skipsOverlaps, dropLine)
        return hitSolid >= 0
    }

    /**
     * Adds a [SweepHit] to [destination] for every solid on a layer in [mask] that the box would
     * enter along the whole move if nothing stopped it, in order of fraction, ties by entity index.
     */
    fun <C : MutableCollection<in SweepHit>> sweepAllTo(
        destination: C,
        minX: Float, minY: Float, maxX: Float, maxY: Float,
        dx: Float, dy: Float,
        mask: Int,
    ): C {
        sweepCollectsAll = true
        try {
            walk(minX, minY, maxX, maxY, dx, dy, mask, skipsOverlaps = false, dropLine = Float.NEGATIVE_INFINITY)
            collected.sortWith(HIT_ORDER)
            destination.addAll(collected)
        } finally {
            collected.clear()
            sweepCollectsAll = false
        }
        return destination
    }

    /** Returns the hit of the last [sweep] as a [SweepHit], or `null` if it hit nothing. */
    fun lastHitOrNull(): SweepHit? =
        if (hitSolid < 0) null else SweepHit(Entity(entities[hitSolid]), hitFraction, hitNormalX, hitNormalY)

    /** Returns the raw entity handle of solid [solid]. */
    fun entityAt(solid: Int): Long = entities[solid]

    /** Returns the top edge, the smallest y, of solid [solid]. */
    fun topOf(solid: Int): Float = boxes[solid * BOX + 1]

    /** Returns the bottom edge, the largest y, of solid [solid]. */
    fun bottomOf(solid: Int): Float = boxes[solid * BOX + 3]

    /** Returns the left edge of solid [solid]. */
    fun leftOf(solid: Int): Float = boxes[solid * BOX]

    /** Returns the right edge of solid [solid]. */
    fun rightOf(solid: Int): Float = boxes[solid * BOX + 2]

    private fun walk(
        minX: Float, minY: Float, maxX: Float, maxY: Float,
        dx: Float, dy: Float,
        mask: Int,
        skipsOverlaps: Boolean,
        dropLine: Float,
    ) {
        sweepMinX = minX
        sweepMinY = minY
        sweepMaxX = maxX
        sweepMaxY = maxY
        sweepDx = dx
        sweepDy = dy
        sweepMask = mask
        sweepSkipsOverlaps = skipsOverlaps
        sweepDropLine = dropLine
        hitSolid = -1
        hitFraction = Float.POSITIVE_INFINITY
        hitNormalX = 0f
        hitNormalY = 0f
        if (solidCount == 0) return
        if (stamp == Int.MAX_VALUE) {
            stamps.fill(0)
            stamp = 0
        }
        stamp++
        for (k in 0 until largeCount) test(large[k])

        val inverse = inverseCell
        val size = cellSize
        // The cells of the box at its start, and of everything the move covers.
        var c0 = cellOf(minX, inverse)
        var c1 = cellOf(maxX, inverse)
        var r0 = cellOf(minY, inverse)
        var r1 = cellOf(maxY, inverse)
        val sweptColumns = cellOf(max(maxX, maxX + dx), inverse).toLong() - cellOf(min(minX, minX + dx), inverse) + 1
        val sweptRows = cellOf(max(maxY, maxY + dy), inverse).toLong() - cellOf(min(minY, minY + dy), inverse) + 1
        if (sweptColumns * sweptRows > cellCount) {
            // The move covers more cells than the grid has, so testing every solid is cheaper.
            for (s in 0 until solidCount) test(s)
            return
        }
        visit(c0, c1, r0, r1)
        // Amanatides and Woo with a thick ray: the time at which each leading edge reaches the
        // next cell boundary, recomputed from the cell number so no error builds up.
        var nextX = when {
            dx > 0f -> ((c1 + 1) * size - maxX) / dx
            dx < 0f -> (c0 * size - minX) / dx
            else -> Float.POSITIVE_INFINITY
        }
        var nextY = when {
            dy > 0f -> ((r1 + 1) * size - maxY) / dy
            dy < 0f -> (r0 * size - minY) / dy
            else -> Float.POSITIVE_INFINITY
        }
        while (true) {
            val t = min(nextX, nextY)
            // A solid first reached at time t is entered at t or later: stop past the end or the best hit.
            if (t > 1f || (!sweepCollectsAll && t > hitFraction)) return
            if (nextX <= nextY) {
                // The leading x edge enters a new column, across the rows the box covers at t. The
                // leading row counter is kept in the range, so rounding cannot drop a row already entered.
                val column: Int
                if (dx > 0f) {
                    column = ++c1
                    nextX = ((c1 + 1) * size - maxX) / dx
                } else {
                    column = --c0
                    nextX = (c0 * size - minX) / dx
                }
                val top = cellOf(minY + dy * t, inverse)
                val bottom = cellOf(maxY + dy * t, inverse)
                visit(column, column, if (dy < 0f) min(r0, top) else top, if (dy > 0f) max(r1, bottom) else bottom)
            } else {
                val row: Int
                if (dy > 0f) {
                    row = ++r1
                    nextY = ((r1 + 1) * size - maxY) / dy
                } else {
                    row = --r0
                    nextY = (r0 * size - minY) / dy
                }
                val left = cellOf(minX + dx * t, inverse)
                val right = cellOf(maxX + dx * t, inverse)
                visit(if (dx < 0f) min(c0, left) else left, if (dx > 0f) max(c1, right) else right, row, row)
            }
        }
    }

    /** Tests every solid in the cells from column [x0] to [x1] and row [y0] to [y1]. */
    private fun visit(x0: Int, x1: Int, y0: Int, y1: Int) {
        for (cy in y0..y1) {
            for (cx in x0..x1) {
                val cell = cellOrNegative(cx, cy)
                if (cell < 0) continue
                for (p in cellStarts[cell] until cellStarts[cell + 1]) test(cellSolids[p])
            }
        }
    }

    /**
     * Tests solid [s] against the sweep, once per sweep, and records it as the best hit, or adds it
     * to the collected hits when the sweep collects all.
     */
    private fun test(s: Int) {
        if (stamps[s] == stamp) return
        stamps[s] = stamp
        if (layerBits[s] and sweepMask == 0) return
        val o = s * BOX
        val solidMinX = boxes[o]
        val solidMinY = boxes[o + 1]
        val solidMaxX = boxes[o + 2]
        val solidMaxY = boxes[o + 3]
        val dx = sweepDx
        val dy = sweepDy
        if (isOneWay[s] && !(blocksOneWay(sweepMaxY, dy, solidMinY) && solidMinY > sweepDropLine)) return
        // Entry and exit times per axis of the box against the solid, the same as of the box's
        // center against the solid grown by the box's half size.
        val enterX: Float
        val exitX: Float
        if (dx > 0f) {
            enterX = (solidMinX - sweepMaxX) / dx
            exitX = (solidMaxX - sweepMinX) / dx
        } else if (dx < 0f) {
            enterX = (solidMaxX - sweepMinX) / dx
            exitX = (solidMinX - sweepMaxX) / dx
        } else {
            if (!(sweepMinX < solidMaxX && solidMinX < sweepMaxX)) return
            enterX = Float.NEGATIVE_INFINITY
            exitX = Float.POSITIVE_INFINITY
        }
        val enterY: Float
        val exitY: Float
        if (dy > 0f) {
            enterY = (solidMinY - sweepMaxY) / dy
            exitY = (solidMaxY - sweepMinY) / dy
        } else if (dy < 0f) {
            enterY = (solidMaxY - sweepMinY) / dy
            exitY = (solidMinY - sweepMaxY) / dy
        } else {
            if (!(sweepMinY < solidMaxY && solidMinY < sweepMaxY)) return
            enterY = Float.NEGATIVE_INFINITY
            exitY = Float.POSITIVE_INFINITY
        }
        val enter = max(enterX, enterY)
        val exit = min(exitX, exitY)
        // Touching without sharing area, moving away, or reaching it only after the move ends.
        if (exit <= enter || exit <= 0f || enter > 1f) return
        val fraction: Float
        val normalX: Float
        val normalY: Float
        if (enter < 0f) {
            // Already overlapping at the start.
            if (sweepSkipsOverlaps) return
            fraction = 0f
            normalX = 0f
            normalY = 0f
        } else {
            fraction = enter
            // A corner reached on both axes at once counts as the floor or ceiling.
            if (enterY >= enterX) {
                normalX = 0f
                normalY = if (dy > 0f) -1f else 1f
            } else {
                normalX = if (dx > 0f) -1f else 1f
                normalY = 0f
            }
        }
        if (sweepCollectsAll) {
            collected.add(SweepHit(Entity(entities[s]), fraction, normalX, normalY))
            return
        }
        val best = hitSolid
        if (best >= 0 && (fraction > hitFraction ||
                (fraction == hitFraction && Entity(entities[s]).index >= Entity(entities[best]).index))
        ) {
            return
        }
        hitSolid = s
        hitFraction = fraction
        hitNormalX = normalX
        hitNormalY = normalY
    }

    /** Returns the number of the cell at ([cx], [cy]), adding the cell if it is new. */
    private fun cellOrAdd(cx: Int, cy: Int): Int {
        val key = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFF_FFFFL)
        val mask = table.size - 1
        var i = hash(key) and mask
        while (table[i] >= 0) {
            val cell = table[i]
            if (cellKeys[cell] == key) return cell
            i = (i + 1) and mask
        }
        if (cellCount == cellKeys.size) {
            cellKeys = cellKeys.copyOf(cellCount * 2)
            cellCursors = cellCursors.copyOf(cellCount * 2)
        }
        val cell = cellCount++
        cellKeys[cell] = key
        cellCursors[cell] = 0
        table[i] = cell
        if (cellCount * 2 > table.size) rehash()
        return cell
    }

    /** Returns the number of the cell at ([cx], [cy]), or -1 if no solid touches it. */
    private fun cellOrNegative(cx: Int, cy: Int): Int {
        val key = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFF_FFFFL)
        val mask = table.size - 1
        var i = hash(key) and mask
        while (true) {
            val cell = table[i]
            if (cell < 0) return -1
            if (cellKeys[cell] == key) return cell
            i = (i + 1) and mask
        }
    }

    private fun rehash() {
        val size = table.size * 2
        val grown = IntArray(size) { -1 }
        val mask = size - 1
        for (cell in 0 until cellCount) {
            var i = hash(cellKeys[cell]) and mask
            while (grown[i] >= 0) i = (i + 1) and mask
            grown[i] = cell
        }
        table = grown
    }

    private fun ensureEntryCapacity(size: Int) {
        if (entryCells.size >= size) return
        var grown = entryCells.size * 2
        while (grown < size) grown *= 2
        entryCells = entryCells.copyOf(grown)
        entrySolids = entrySolids.copyOf(grown)
    }

    /** Makes room for [size] solids, keeping the ones there, which [refresh] compares against. */
    private fun ensureSolidCapacity(size: Int) {
        if (entities.size >= size) return
        var grown = entities.size * 2
        while (grown < size) grown *= 2
        entities = entities.copyOf(grown)
        boxes = boxes.copyOf(grown * BOX)
        layerBits = layerBits.copyOf(grown)
        isOneWay = isOneWay.copyOf(grown)
        stamps = stamps.copyOf(grown)
    }

    override fun toString(): String = "SolidGrid(solids=$solidCount, cells=$cellCount)"

    companion object {
        private const val INITIAL_SOLIDS: Int = 64

        /** Hits by fraction, ties by entity index. */
        private val HIT_ORDER: Comparator<SweepHit> = compareBy<SweepHit> { it.fraction }.thenBy { it.entity.index }

        /** Floats per solid in [boxes]. */
        private const val BOX: Int = 4

        /** The most cells a solid may cover and still be placed in the grid; solids rarely change, so it is generous. */
        private const val LARGE_CELLS: Long = 4096

        private fun hash(key: Long): Int {
            val mixed = key * -7046029254386353131L
            return (mixed ushr 32).toInt() xor mixed.toInt()
        }
    }
}

/**
 * Returns whether a one-way solid whose top is at [solidTop] stops a box moving down by [dy] whose
 * bottom edge was at [bottom] before the move: only when it moves down and the bottom was at or
 * above the top. Every one-way decision, in sweeps and body moves, goes through it.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun blocksOneWay(bottom: Float, dy: Float, solidTop: Float): Boolean = dy > 0f && bottom <= solidTop

/** Returns the grid cell coordinate of [value], given the inverse of the cell size. */
@Suppress("NOTHING_TO_INLINE")
private inline fun cellOf(value: Float, inverseCell: Float): Int = floor(value * inverseCell).toInt()
