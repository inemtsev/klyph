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
 * A solid moves when its box changes between two refreshes. [refresh] finds each solid's box of
 * the last refresh by its entity, wherever the store's order put it, and adds the difference of
 * the top-left corners to the solid's shift, unless the box changed size, which [moveBodies]
 * carries and pushes bodies by and then clears with [clearShifts], so a move made between two
 * fixed steps counts once whatever refreshed in between. A solid with a `Velocity`, or one whose
 * box changed in the last [STILL_REFRESHES] refreshes, is moving: it stays out of the still cells
 * and goes into a second hash grid, rebuilt over each moving solid's whole move, so a platform
 * that moves every step rebuilds only that. The still cells are rebuilt when a solid starts or
 * stops moving, as when one is added or removed.
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

    // One entry per solid: its raw entity handle, its box and size (BOX floats: min x, min y, max x,
    // max y, then the half width and half height computed from its collider, so a resize is told
    // from a move exactly), its collider's layer bits, whether it is one-way, whether it is moving,
    // how far its top-left corner moved since the shifts were last cleared, and for how many
    // refreshes it has kept still.
    // refresh swaps them with the previous* arrays, which hold the last refresh's entries.
    private var entities = LongArray(INITIAL_SOLIDS)
    private var boxes = FloatArray(INITIAL_SOLIDS * BOX)
    private var layerBits = IntArray(INITIAL_SOLIDS)
    private var isOneWay = BooleanArray(INITIAL_SOLIDS)
    private var isMoving = BooleanArray(INITIAL_SOLIDS)
    private var shiftX = FloatArray(INITIAL_SOLIDS)
    private var shiftY = FloatArray(INITIAL_SOLIDS)
    private var stillRefreshes = IntArray(INITIAL_SOLIDS)

    private var previousEntities = LongArray(INITIAL_SOLIDS)
    private var previousBoxes = FloatArray(INITIAL_SOLIDS * BOX)
    private var previousLayerBits = IntArray(INITIAL_SOLIDS)
    private var previousIsOneWay = BooleanArray(INITIAL_SOLIDS)
    private var previousIsMoving = BooleanArray(INITIAL_SOLIDS)
    private var previousShiftX = FloatArray(INITIAL_SOLIDS)
    private var previousShiftY = FloatArray(INITIAL_SOLIDS)
    private var previousStillRefreshes = IntArray(INITIAL_SOLIDS)

    /** The solid of each entity index as of the last refresh; valid only where [entities] holds that entity there. */
    private var solidOfIndex = IntArray(INITIAL_SOLIDS) { -1 }

    /** The solids whose shift is not zero, which [moveBodies] carries and pushes bodies by. */
    private var shifted = IntArray(16)

    /** The number of entries in [shifted]. */
    var shiftedCount: Int = 0
        private set

    /** The test stamp of each solid, so a solid in several cells is tested once per sweep. */
    private var stamps = IntArray(INITIAL_SOLIDS)
    private var stamp = 0

    // The grid: the cells of the solids that stand still, rebuilt only when they change, and the
    // cells of the moving ones, rebuilt at every refresh and covering each one's whole move.
    private var cellSize = 0f
    private var inverseCell = 0f
    private val stillCells = SolidCells()
    private val movingCells = SolidCells()

    /** Still solids covering more than LARGE_CELLS cells, kept out of the cells and tested by every sweep. */
    private var large = IntArray(16)
    private var largeCount = 0

    /** The moving solids, gathered by [refresh]. */
    private var moving = IntArray(16)
    private var movingCount = 0

    /** Moving solids covering more than LARGE_CELLS cells, kept out of the cells and tested by every sweep. */
    private var movingLarge = IntArray(16)
    private var movingLargeCount = 0

    /** Whether [movingCells] no longer match the moving solids' boxes and shifts, so the next refresh rebuilds them. */
    private var isMovingStale = true

    // The shifted solids near a body, found by shiftedNear, and the stamps that find each one once.
    private var near = IntArray(16)
    private var nearStamps = IntArray(INITIAL_SOLIDS)
    private var nearStamp = 0

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
    private var sweepIgnores = -1

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

    /** The slide every body move writes into, so moving bodies allocates nothing. */
    val bodySlide: BoxSlide = BoxSlide()

    /** The box of the body being moved, so carrying and pushing it allocates nothing. */
    val bodyBox: BodyBox = BodyBox()

    /** The world of the last [refresh], or `null` before the first. */
    val worldOrNull: World?
        get() = world

    /**
     * Gathers every [Solid] of [world], except those owned by a scene [scenes] holds frozen, adds
     * how far each moved since the last refresh to its shift, and rebuilds the grid with cells
     * [cellSize] across if a solid was added or removed, started or stopped moving, changed while
     * still, the cell size changed or [world] is not the world of the last refresh, whose solids
     * then count as new, with no shift. After it throws, the grid's state is undefined until a
     * refresh succeeds: the game fixes the entity rather than calling again.
     *
     * @throws IllegalStateException if a solid has no [Collider], a collider that is not a box, or a parent.
     */
    fun refresh(world: World, scenes: SceneStack?, cellSize: Float) {
        // Handles of another world can equal this one's, so its solids are not compared with them.
        val isSameWorld = this.world === world
        val previousCount = if (isSameWorld) solidCount else 0
        val previousMovingCount = movingCount
        this.world = world
        this.scenes = scenes
        val solids = world.storeOrNull(Solid)
        val colliders = world.storeOrNull(Collider)
        solidVersion = solids?.version ?: -1
        colliderVersion = colliders?.version ?: -1
        swapWithPrevious()
        var changed = cellSize != this.cellSize || !isSameWorld
        var movingStale = isMovingStale
        var count = 0
        shiftedCount = 0
        movingCount = 0
        if (solids != null && solids.size > 0) {
            ensureSolidCapacity(solids.size)
            val locals = world.storeOrNull(Transform)
            val worlds = world.storeOrNull(WorldTransform)
            val velocities = world.storeOrNull(Velocity)
            val frozen = if (scenes != null && scenes.hasFrozen) scenes else null
            for (k in 0 until solids.size) {
                val raw = solids.entities[k]
                val entity = Entity(raw)
                if (frozen != null && frozen.isFrozen(world.ownerAt(entity.index))) continue
                val collider = colliders?.getOrNull(entity)
                checkNotNull(collider) { "Entity $entity has a Solid but no Collider; add a Collider with a Shape.Box." }
                val shape = collider.shape
                check(shape is Shape.Box) { "Entity $entity has a Solid whose collider shape $shape is not a Box." }
                check(world.parentOrNull(entity) == null) {
                    "Entity $entity has a Solid and a parent, but a solid must be a root entity; " +
                        "remove the parent or move the Solid and its Collider to the root."
                }
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
                solidOfIndex = solidOfIndex.withRoomFor(entity.index)

                // The same solid in the last refresh, found by its entity wherever it was.
                val last = previousSolidOf(raw, count, previousCount)
                var moved = false
                var shiftedX = 0f
                var shiftedY = 0f
                var still = 0
                var wasMoving = false
                if (last >= 0) {
                    val p = last * BOX
                    moved = previousBoxes[p] != minX || previousBoxes[p + 1] != minY ||
                        previousBoxes[p + 2] != maxX || previousBoxes[p + 3] != maxY
                    // A box that changed size grew or shrank in place: it moved for the grid, but
                    // shifts nothing, so it carries no rider by half its growth.
                    val keepsSize = previousBoxes[p + 4] == halfWidth && previousBoxes[p + 5] == halfHeight
                    shiftedX = previousShiftX[last] + if (keepsSize) minX - previousBoxes[p] else 0f
                    shiftedY = previousShiftY[last] + if (keepsSize) minY - previousBoxes[p + 1] else 0f
                    still = if (moved) 0 else min(previousStillRefreshes[last] + 1, STILL_REFRESHES + 1)
                    wasMoving = previousIsMoving[last]
                }
                val nowMoving = velocities?.getOrNull(entity) != null || (last >= 0 && still <= STILL_REFRESHES)
                // The cells stay as they are while every solid keeps its place, and its look while still.
                val keepsPlace = last == count && wasMoving == nowMoving
                if (!keepsPlace || !(nowMoving || looksTheSame(last, moved, layers, oneWay))) changed = true
                if (nowMoving && moved) movingStale = true
                val o = count * BOX
                entities[count] = raw
                boxes[o] = minX
                boxes[o + 1] = minY
                boxes[o + 2] = maxX
                boxes[o + 3] = maxY
                boxes[o + 4] = halfWidth
                boxes[o + 5] = halfHeight
                layerBits[count] = layers
                isOneWay[count] = oneWay
                isMoving[count] = nowMoving
                shiftX[count] = shiftedX
                shiftY[count] = shiftedY
                stillRefreshes[count] = if (last >= 0) still else STILL_REFRESHES + 1
                solidOfIndex[entity.index] = count
                if (shiftedX != 0f || shiftedY != 0f) {
                    if (shiftedCount == shifted.size) shifted = shifted.copyOf(shiftedCount * 2)
                    shifted[shiftedCount++] = count
                }
                if (nowMoving) {
                    if (movingCount == moving.size) moving = moving.copyOf(movingCount * 2)
                    moving[movingCount++] = count
                }
                count++
            }
        }
        if (count != previousCount) changed = true
        solidCount = count
        if (changed) build(cellSize)
        // The moving cells stand while the moving solids keep their places, boxes and shifts.
        if (changed || movingStale || movingCount != previousMovingCount) buildMoving()
        isMovingStale = false
    }

    /**
     * Returns where the solid of entity [raw] was in the last refresh, or -1 if it was in none: at
     * [expected] when the store kept its order, which is the common case, or wherever
     * [solidOfIndex] says, checked against the previous entities so a stale entry finds nothing.
     */
    private fun previousSolidOf(raw: Long, expected: Int, previousCount: Int): Int {
        if (expected < previousCount && previousEntities[expected] == raw) return expected
        val index = Entity(raw).index
        val last = solidOfIndex[index]
        return if (last in 0 until previousCount && previousEntities[last] == raw) last else -1
    }

    /** Returns whether the solid at [last] in the last refresh kept its box, unless it [moved], its [layers] and its [oneWay]. */
    private fun looksTheSame(last: Int, moved: Boolean, layers: Int, oneWay: Boolean): Boolean =
        !moved && previousLayerBits[last] == layers && previousIsOneWay[last] == oneWay

    /** Makes the arrays of the last refresh the previous ones, so this refresh writes over the older pair. */
    private fun swapWithPrevious() {
        entities = previousEntities.also { previousEntities = entities }
        boxes = previousBoxes.also { previousBoxes = boxes }
        layerBits = previousLayerBits.also { previousLayerBits = layerBits }
        isOneWay = previousIsOneWay.also { previousIsOneWay = isOneWay }
        isMoving = previousIsMoving.also { previousIsMoving = isMoving }
        shiftX = previousShiftX.also { previousShiftX = shiftX }
        shiftY = previousShiftY.also { previousShiftY = shiftY }
        stillRefreshes = previousStillRefreshes.also { previousStillRefreshes = stillRefreshes }
    }

    /** Returns the solid of entity [raw] as of the last refresh, or -1 if it is not one. */
    fun solidOf(raw: Long): Int {
        val index = if (raw == KinematicBody.NO_FLOOR) -1 else Entity(raw).index
        val solid = if (index in solidOfIndex.indices) solidOfIndex[index] else -1
        return if (solid in 0 until solidCount && entities[solid] == raw) solid else -1
    }

    /** Returns how far solid [solid] moved across since the shifts were last cleared. */
    fun shiftXOf(solid: Int): Float = shiftX[solid]

    /** Returns how far solid [solid] moved up or down since the shifts were last cleared. */
    fun shiftYOf(solid: Int): Float = shiftY[solid]

    /** Returns the layer bits of solid [solid]. */
    fun layersOf(solid: Int): Int = layerBits[solid]

    /** Returns whether solid [solid] is a one-way ledge. */
    fun isOneWayAt(solid: Int): Boolean = isOneWay[solid]

    /** Sets every shift back to zero once [moveBodies] has carried and pushed the bodies by them. */
    fun clearShifts() {
        if (shiftedCount > 0) isMovingStale = true
        for (k in 0 until shiftedCount) {
            val solid = shifted[k]
            shiftX[solid] = 0f
            shiftY[solid] = 0f
        }
        shiftedCount = 0
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

    /** Sorts the still solids into [stillCells], or [large] when they cover too many cells. */
    private fun build(cellSize: Float) {
        this.cellSize = cellSize
        val inverse = 1f / cellSize
        inverseCell = inverse
        stillCells.clear()
        largeCount = 0
        for (s in 0 until solidCount) {
            if (isMoving[s]) continue
            val o = s * BOX
            if (!stillCells.addBox(s, boxes[o], boxes[o + 1], boxes[o + 2], boxes[o + 3], inverse)) {
                if (largeCount == large.size) large = large.copyOf(largeCount * 2)
                large[largeCount++] = s
            }
        }
        stillCells.finish()
    }

    /**
     * Sorts the moving solids into [movingCells] by the box each one covered while it moved since
     * the shifts were last cleared, its old box and its new box together, so a body it moved into
     * shares a cell with it; one that covers too many cells goes to [movingLarge].
     */
    private fun buildMoving() {
        movingCells.clear()
        movingLargeCount = 0
        if (movingCount == 0) return
        for (k in 0 until movingCount) {
            val s = moving[k]
            val o = s * BOX
            val dx = shiftX[s]
            val dy = shiftY[s]
            val minX = min(boxes[o], boxes[o] - dx)
            val minY = min(boxes[o + 1], boxes[o + 1] - dy)
            val maxX = max(boxes[o + 2], boxes[o + 2] - dx)
            val maxY = max(boxes[o + 3], boxes[o + 3] - dy)
            if (!movingCells.addBox(s, minX, minY, maxX, maxY, inverseCell)) {
                if (movingLargeCount == movingLarge.size) movingLarge = movingLarge.copyOf(movingLargeCount * 2)
                movingLarge[movingLargeCount++] = s
            }
        }
        movingCells.finish()
    }

    /**
     * Gathers the solids that moved since the shifts were last cleared and whose move covered a
     * cell the box from ([minX], [minY]) to ([maxX], [maxY]) touches, each once, and returns how
     * many; [nearAt] returns them. Allocates nothing once its array fits.
     */
    fun shiftedNear(minX: Float, minY: Float, maxX: Float, maxY: Float): Int {
        if (nearStamp == Int.MAX_VALUE) {
            nearStamps.fill(0)
            nearStamp = 0
        }
        nearStamp++
        var count = 0
        if (shiftedCount > 0) {
            for (k in 0 until movingLargeCount) count = addNear(movingLarge[k], count)
            if (movingCells.cellCount > 0) count = addNearInCells(minX, minY, maxX, maxY, count)
        }
        return count
    }

    /** Adds the shifted solids of the moving cells the box touches to [near], after the first [count], and returns the new count. */
    private fun addNearInCells(minX: Float, minY: Float, maxX: Float, maxY: Float, count: Int): Int {
        var added = count
        val inverse = inverseCell
        for (cy in cellOf(minY, inverse)..cellOf(maxY, inverse)) {
            for (cx in cellOf(minX, inverse)..cellOf(maxX, inverse)) {
                val cell = movingCells.cellOrNegative(cx, cy)
                if (cell < 0) continue
                for (p in movingCells.startOf(cell) until movingCells.endOf(cell)) added = addNear(movingCells.solidAt(p), added)
            }
        }
        return added
    }

    private fun addNear(s: Int, count: Int): Int {
        if (nearStamps[s] == nearStamp || (shiftX[s] == 0f && shiftY[s] == 0f)) return count
        nearStamps[s] = nearStamp
        if (count == near.size) near = near.copyOf(count * 2)
        near[count] = s
        return count + 1
    }

    /** Returns the solid at position [k] of the last [shiftedNear]. */
    fun nearAt(k: Int): Int = near[k]

    /**
     * Sweeps the box from ([minX], [minY]) to ([maxX], [maxY]) by ([dx], [dy]) against the solids on
     * a layer in [mask] and records the earliest hit in [hitSolid], [hitFraction], [hitNormalX] and
     * [hitNormalY]. Returns whether there was one. Ties go to the solid with the smaller entity index.
     *
     * A solid the box overlaps at the start is a hit at fraction 0 with the normal (0, 0), unless
     * [skipsOverlaps] is set, as for a body, which then moves through it. One-way solids follow
     * [blocksOneWay], and those whose top is at or above [dropLine], a y less than or equal to it,
     * are ignored, as is solid [ignores], such as the one a body is carried or pushed by.
     */
    fun sweep(
        minX: Float, minY: Float, maxX: Float, maxY: Float,
        dx: Float, dy: Float,
        mask: Int,
        skipsOverlaps: Boolean,
        dropLine: Float = Float.NEGATIVE_INFINITY,
        ignores: Int = -1,
    ): Boolean {
        sweepCollectsAll = false
        sweepIgnores = ignores
        try {
            walk(minX, minY, maxX, maxY, dx, dy, mask, skipsOverlaps, dropLine)
        } finally {
            sweepIgnores = -1
        }
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
        for (k in 0 until movingLargeCount) test(movingLarge[k])

        val inverse = inverseCell
        val size = cellSize
        // The cells of the box at its start, and of everything the move covers.
        var c0 = cellOf(minX, inverse)
        var c1 = cellOf(maxX, inverse)
        var r0 = cellOf(minY, inverse)
        var r1 = cellOf(maxY, inverse)
        val sweptColumns = cellOf(max(maxX, maxX + dx), inverse).toLong() - cellOf(min(minX, minX + dx), inverse) + 1
        val sweptRows = cellOf(max(maxY, maxY + dy), inverse).toLong() - cellOf(min(minY, minY + dy), inverse) + 1
        if (sweptColumns * sweptRows > stillCells.cellCount + movingCells.cellCount) {
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

    /** Tests every solid in the cells from column [x0] to [x1] and row [y0] to [y1], still and moving. */
    private fun visit(x0: Int, x1: Int, y0: Int, y1: Int) {
        val hasMoving = movingCells.cellCount > 0
        for (cy in y0..y1) {
            for (cx in x0..x1) {
                val cell = stillCells.cellOrNegative(cx, cy)
                if (cell >= 0) for (p in stillCells.startOf(cell) until stillCells.endOf(cell)) test(stillCells.solidAt(p))
                if (!hasMoving) continue
                val movingCell = movingCells.cellOrNegative(cx, cy)
                if (movingCell < 0) continue
                for (p in movingCells.startOf(movingCell) until movingCells.endOf(movingCell)) test(movingCells.solidAt(p))
            }
        }
    }

    /**
     * Tests solid [s] against the sweep, once per sweep, and records it as the best hit, or adds it
     * to the collected hits when the sweep collects all.
     */
    private fun test(s: Int) {
        if (stamps[s] == stamp || s == sweepIgnores) return
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

    /**
     * Makes room for [size] solids in the arrays [refresh] writes. Their old entries are two
     * refreshes old and unused, so they are not kept; the previous arrays, which [refresh] compares
     * against, are left as they are.
     */
    private fun ensureSolidCapacity(size: Int) {
        if (entities.size >= size) return
        var grown = entities.size * 2
        while (grown < size) grown *= 2
        entities = LongArray(grown)
        boxes = FloatArray(grown * BOX)
        layerBits = IntArray(grown)
        isOneWay = BooleanArray(grown)
        isMoving = BooleanArray(grown)
        shiftX = FloatArray(grown)
        shiftY = FloatArray(grown)
        stillRefreshes = IntArray(grown)
        if (stamps.size < grown) stamps = stamps.copyOf(grown)
        if (nearStamps.size < grown) nearStamps = nearStamps.copyOf(grown)
    }

    override fun toString(): String =
        "SolidGrid(solids=$solidCount, moving=$movingCount, cells=${stillCells.cellCount}, movingCells=${movingCells.cellCount})"

    companion object {
        private const val INITIAL_SOLIDS: Int = 64

        /** Hits by fraction, ties by entity index. */
        private val HIT_ORDER: Comparator<SweepHit> = compareBy<SweepHit> { it.fraction }.thenBy { it.entity.index }

        /** Floats per solid in [boxes]: the box's four edges and its two half sizes. */
        private const val BOX: Int = 6

        /**
         * How many refreshes a solid without a `Velocity` stays moving after its box last changed:
         * a second or two of fixed steps and frames, so a solid a system moves every step stays out of
         * the cells, and one moved once goes back into them after one more rebuild.
         */
        private const val STILL_REFRESHES: Int = 120
    }
}

/**
 * Returns whether a one-way solid whose top is at [solidTop] stops a box moving down by [dy] whose
 * bottom edge was at [bottom] before the move: only when it moves down and the bottom was at or
 * above the top. Every one-way decision, in sweeps and body moves, goes through it.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun blocksOneWay(bottom: Float, dy: Float, solidTop: Float): Boolean = dy > 0f && bottom <= solidTop

/**
 * Returns [this] if it has an entry for entity index [index], or a copy grown to have one, its new
 * entries marking no solid.
 */
private fun IntArray.withRoomFor(index: Int): IntArray {
    if (index < size) return this
    var grown = size * 2
    while (grown <= index) grown *= 2
    return copyOf(grown).also { it.fill(-1, size, grown) }
}

/**
 * Adds [solid] with the box from ([minX], [minY]) to ([maxX], [maxY]) to the cells it touches, given
 * the inverse of the cell size, and returns `true`, or returns `false` if it covers more than
 * [LARGE_CELLS] cells.
 */
private fun SolidCells.addBox(solid: Int, minX: Float, minY: Float, maxX: Float, maxY: Float, inverseCell: Float): Boolean {
    val x0 = cellOf(minX, inverseCell)
    val y0 = cellOf(minY, inverseCell)
    val x1 = cellOf(maxX, inverseCell)
    val y1 = cellOf(maxY, inverseCell)
    if ((x1.toLong() - x0 + 1) * (y1.toLong() - y0 + 1) > LARGE_CELLS) return false
    add(solid, x0, y0, x1, y1)
    return true
}

/** The most cells a solid may cover and still be placed in the cells; solids rarely change, so it is generous. */
private const val LARGE_CELLS: Long = 4096

/** Returns the grid cell coordinate of [value], given the inverse of the cell size. */
@Suppress("NOTHING_TO_INLINE")
private inline fun cellOf(value: Float, inverseCell: Float): Int = floor(value * inverseCell).toInt()
