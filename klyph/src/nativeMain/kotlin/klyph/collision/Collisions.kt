package klyph.collision

import klyph.entity.ComponentStore
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.event.Events
import klyph.scene.SceneStack
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.time.Duration

/**
 * Finds overlapping [Collider]s: [detect] publishes an [Overlap] event for every overlapping pair,
 * and [overlappingTo], [overlapping] and [firstOverlappingOrNull] find the colliders a shape overlaps.
 * For ordinary colliders that is all: overlaps are reported, never resolved. Entities that also
 * have a [Solid] stop every [KinematicBody], which [moveBodies] moves; [sweepBox], [sweepBoxTo]
 * and [sweepCollider] find the solids in the way of a moving box, [slideBox] and [slideCollider]
 * make a body's move without a body, and [floorBelowOrNull] finds the floor under a point.
 *
 * `App.collisions` is the engine's instance: the engine calls [detect] on it once per frame,
 * after the last fixed update step and a transform propagation, so parented colliders are where
 * their parents put them. Create another instance only to use detection without an app, such as
 * in a test or a tool, and call [detect] on it yourself.
 *
 * [detect] takes a snapshot of every collider's world position and size and sorts them into a
 * uniform grid of square cells [cellSize] world units across. A collider is placed in every cell
 * its bounding box touches, and only colliders that share a cell are tested against each other,
 * so the cost grows with the number of colliders and of close pairs, not with the square of the
 * collider count. Cells are found through a hash table keyed by their coordinates, so the world
 * has no bounds and empty space costs nothing. A cell size near the size of a typical collider
 * works best: much smaller places each collider in many cells, much larger tests many distant
 * pairs. A collider that would touch more than 256 cells is kept out of the grid and tested
 * against every other collider instead, so one huge collider cannot stall a frame.
 *
 * The snapshot is rebuilt from scratch on every [detect], with no state carried between frames.
 * Its arrays grow by doubling and are kept, so once they fit the largest frame, detection
 * allocates nothing besides the [Overlap] events it publishes.
 *
 * Queries use the snapshot of the last [detect], not the world as it is now: a collider added or
 * moved since then is found where it was, or not at all, and an entity destroyed since then can
 * still be returned. For `App.collisions`, queries from the update, late update and render phases
 * see this frame's positions after the fixed steps; queries from the input and fixed update
 * phases see the previous frame's. Before the first [detect] every query finds nothing.
 *
 * Solids live in a grid of their own, with the same [cellSize], which the sweeps read as it is
 * now, not as of the last [detect]; see [Solid] for when it is rebuilt.
 *
 * [toString] gives `Collisions(cellSize=64.0, colliders=120, cells=85, pairs=3, solids=40)`, the
 * numbers of the last [detect] and the solids as of the last refresh.
 *
 * Not thread-safe.
 *
 * @throws IllegalArgumentException if [cellSize] is not a positive finite number.
 *
 * @sample samples.collision.CollisionQueries.detect
 */
public class Collisions(cellSize: Float = DEFAULT_CELL_SIZE) {
    /**
     * The width and height of a grid cell in world units, which must be a positive finite number.
     * A change takes effect at the next [detect]; queries until then use the cell size of the last
     * one.
     *
     * @throws IllegalArgumentException if set to a value that is not positive or not finite.
     */
    public var cellSize: Float = checkCellSize(cellSize)
        set(value) {
            field = checkCellSize(value)
        }

    /** Returns the number of overlapping pairs the last [detect] found, which is the number of [Overlap] events it published. */
    public var pairCount: Int = 0
        private set

    /**
     * Returns the number of colliders the last [detect] took part in the snapshot: every entity
     * with a [Collider] and a `Transform` or `WorldTransform`.
     */
    public var colliderCount: Int = 0
        private set

    // The snapshot, one entry per collider in gathering order. bounds holds STRIDE floats per
    // collider: min x, min y, max x, max y of the bounding box, then the center and the half
    // width and half height, which are both the radius for a circle.
    private var bounds = FloatArray(INITIAL_COLLIDERS * STRIDE)
    private var isCircle = BooleanArray(INITIAL_COLLIDERS)
    private var layerBits = IntArray(INITIAL_COLLIDERS)
    private var maskBits = IntArray(INITIAL_COLLIDERS)
    private var entities = LongArray(INITIAL_COLLIDERS)

    /** Whether each collider touches more than [LARGE_CELLS] cells and so is kept out of the grid. */
    private var isLarge = BooleanArray(INITIAL_COLLIDERS)
    private var large = IntArray(16)
    private var largeCount = 0

    // The grid. Cells are numbered in first-touched order; cellKeys holds each cell's packed
    // (x, y) coordinates, cellStarts its first position in sorted, and cellStarts[cellCount] the
    // end of the last cell.
    private var gridInverseCell = 1f / cellSize
    private var cellCount = 0
    private var cellKeys = LongArray(64)
    private var cellCursors = IntArray(64)
    private var cellStarts = IntArray(65)

    /** Collider indices grouped by cell, the result of the counting sort. */
    private var sorted = IntArray(INITIAL_COLLIDERS)

    // Copies of each sorted entry's bounding box (BOX_STRIDE floats: min x, min y, max x, max y),
    // layers and mask, so the pair loop reads one contiguous run per cell instead of jumping to
    // random colliders, which costs a cache miss per test once the colliders outgrow the cache.
    private var sortedBoxes = FloatArray(INITIAL_COLLIDERS * BOX_STRIDE)
    private var sortedLayers = IntArray(INITIAL_COLLIDERS)
    private var sortedMasks = IntArray(INITIAL_COLLIDERS)

    // One entry per (collider, cell) pair, in gathering order: the cell and the collider.
    private var entryCells = IntArray(INITIAL_COLLIDERS)
    private var entryColliders = IntArray(INITIAL_COLLIDERS)
    private var entryCount = 0

    // Open-addressing hash table from cell key to cell number, a power of two in size and at most
    // half full. A slot is in use only while its stamp equals tableStamp, so a new detection
    // empties the table without clearing it.
    private var table = IntArray(128)
    private var tableStamps = IntArray(128)
    private var tableStamp = 0

    // Query scratch: the query's stamp per collider, so a collider in several cells is tested once,
    // and the matches as (entity index shl 32) or collider index, so sorting orders them by entity.
    private var queryStamps = IntArray(INITIAL_COLLIDERS)
    private var queryStamp = 0
    private var matchKeys = LongArray(16)
    private var queryMinX = 0f
    private var queryMinY = 0f
    private var queryMaxX = 0f
    private var queryMaxY = 0f
    private var queryCenterX = 0f
    private var queryCenterY = 0f
    private var queryRadius = 0f
    private var queryIsCircle = false
    private var queryMask = 0

    /** The solids, their grid and the sweep. */
    private val solids = SolidGrid()

    /**
     * Returns the number of [Solid]s in the solids grid as of its last refresh, which happens at
     * every [detect], every [moveBodies] and every sweep after a solid or collider was added or removed.
     */
    public val solidCount: Int
        get() = solids.solidCount

    /**
     * Takes a snapshot of every collider in [world], sorts it into the grid, and publishes an
     * [Overlap] into [events] for every pair of colliders that overlap, once per pair. A pair is
     * reported when either collider's mask contains a layer of the other.
     *
     * Two shapes overlap when they share interior area: shapes that only touch along an edge or
     * at a point do not. Boxes are compared as axis-aligned rectangles, circles by the distance
     * between their centers, and a circle and a box by the box point closest to the circle's center.
     *
     * Each collider sits where the class description of [Collider] says, offset and scaled unless
     * [Collider.scalesWithTransform] is `false`, with rotation ignored. An entity with a [Collider]
     * but neither a `Transform` nor a `WorldTransform` is skipped, as is one whose position or size
     * is not finite.
     *
     * The engine calls this on `App.collisions` once per frame; call it yourself only on an
     * instance you created. The engine's call also leaves out the colliders of scenes frozen below
     * the top of `App.scenes`, so they are in no pair and no query finds them.
     *
     * It also brings the solids grid up to date with [world], as [moveBodies] does.
     *
     * @throws IllegalStateException if an entity has a [Solid] but no [Collider], or a collider
     *   that is not a [Shape.Box].
     *
     * @sample samples.collision.CollisionQueries.detect
     */
    public fun detect(world: World, events: Events) {
        detect(world, events, scenes = null)
    }

    /**
     * Detects overlaps as `detect(world, events)` does, leaving out every collider owned by a scene
     * that [scenes] holds frozen below its top. The engine's once-per-frame call.
     */
    internal fun detect(world: World, events: Events, scenes: SceneStack?) {
        solids.refresh(world, scenes, cellSize)
        gather(world, scenes)
        buildGrid()
        findPairs(events)
    }

    /**
     * Makes the sweeps answer for [world] before the first [detect] or [moveBodies], so a level
     * spawned during setup is found by a sweep in the first fixed step. The engine calls it when
     * it creates the app.
     */
    internal fun attach(world: World, scenes: SceneStack?) {
        solids.refresh(world, scenes, cellSize)
    }

    /**
     * Moves every [KinematicBody] in [world] by its speed times [delta] and stops it at the
     * [Solid]s in the way, setting its contact state, as the class description of [KinematicBody]
     * explains. It first brings the solids grid up to date with [world].
     *
     * The engine calls this on `App.collisions` in every fixed step, after the `FIXED_UPDATE`
     * systems and the `Velocity` step, with the fixed step length, and leaves out the bodies and
     * solids of scenes frozen below the top of `App.scenes`. Call it yourself only on an instance
     * you created, such as in a test or a tool. Allocates nothing once the grid has grown.
     *
     * @throws IllegalArgumentException if [delta] is negative or infinite.
     * @throws IllegalStateException if a body or a solid is set up wrongly, as [KinematicBody] and
     *   [Solid] describe.
     *
     * @sample samples.collision.Bodies.kinematicBody
     */
    public fun moveBodies(world: World, delta: Duration) {
        require(!delta.isNegative() && delta.isFinite()) { "Delta $delta is negative or infinite." }
        moveBodies(world, (delta.inWholeNanoseconds / 1e9).toFloat(), scenes = null)
    }

    /** Moves the bodies as `moveBodies(world, delta)` does, leaving out what [scenes] holds frozen. The engine's call. */
    internal fun moveBodies(world: World, deltaSeconds: Float, scenes: SceneStack?) {
        solids.refresh(world, scenes, cellSize)
        solids.moveBodies(world, deltaSeconds, scenes)
    }

    /**
     * Sweeps a box [width] by [height] centered on ([x], [y]) along the straight move by ([dx],
     * [dy]) and returns where it first touches a [Solid] on a layer in [mask], or `null` if the
     * whole move is clear. Pass the arguments by name, as in
     * `sweepBox(x = x, y = y, width = 24f, height = 40f, dx = 0f, dy = 300f)`.
     *
     * The returned [SweepHit] holds the solid, how far along the move the box touches it, from 0
     * to 1, and the normal of the face it touches. Of two solids touched at the same moment, the one
     * with the smaller [Entity.index] is returned. A box that only touches a solid along an edge
     * and moves along it, or away from it, does not hit it. A solid the box already overlaps at the
     * start is a hit at fraction 0 with the normal (0, 0), so a sweep with [dx] and [dy] both 0
     * returns the first solid the box overlaps where it is. A [Passable.FROM_BELOW] solid is hit
     * only by a box moving down whose bottom edge starts at or above the solid's top, as it stops
     * bodies.
     *
     * Unlike the overlap queries, a sweep reads the solids as they are now: the grid is brought up
     * to date at every [detect] and [moveBodies], and before a sweep when a solid or a collider was
     * added, replaced or removed since. A solid moved since the last refresh is found where it was
     * until the next one. The sweep visits only the grid cells along the move, in the order the box
     * reaches them, and stops at the first hit; a move longer than the grid is wide tests every
     * solid instead. It allocates nothing besides the hit it returns.
     *
     * @throws IllegalArgumentException if [x], [y], [dx] or [dy] is not finite, or [width] or
     *   [height] is not a positive finite number.
     * @throws IllegalStateException if a solid is set up wrongly, as [Solid] describes.
     *
     * @sample samples.collision.Sweeps.sweepBox
     */
    public fun sweepBox(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        dx: Float,
        dy: Float,
        mask: CollisionLayers = CollisionLayers.ALL,
    ): SweepHit? {
        checkSweep(x, y, width, height, dx, dy)
        solids.ensureCurrent(cellSize)
        val halfWidth = width * 0.5f
        val halfHeight = height * 0.5f
        solids.sweep(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight, dx, dy, mask.bits, skipsOverlaps = false)
        return solids.lastHitOrNull()
    }

    /**
     * Adds to [destination] a [SweepHit] for every [Solid] on a layer in [mask] that a box [width]
     * by [height] centered on ([x], [y]) touches along the straight move by ([dx], [dy]), as if
     * nothing stopped it, in the order the box reaches them, and returns [destination]. Hits at the
     * same moment are ordered by [Entity.index]. Pass the arguments by name.
     *
     * Each hit is the one [sweepBox] would return if that solid were the only one, and the solids
     * are read as [sweepBox] reads them. It allocates the hits and what [destination] needs to grow.
     *
     * @throws IllegalArgumentException if [x], [y], [dx] or [dy] is not finite, or [width] or
     *   [height] is not a positive finite number.
     * @throws IllegalStateException if a solid is set up wrongly, as [Solid] describes.
     *
     * @sample samples.collision.Sweeps.sweepBoxTo
     */
    public fun <C : MutableCollection<in SweepHit>> sweepBoxTo(
        destination: C,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        dx: Float,
        dy: Float,
        mask: CollisionLayers = CollisionLayers.ALL,
    ): C {
        checkSweep(x, y, width, height, dx, dy)
        solids.ensureCurrent(cellSize)
        val halfWidth = width * 0.5f
        val halfHeight = height * 0.5f
        return solids.sweepAllTo(destination, x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight, dx, dy, mask.bits)
    }

    /**
     * Sweeps [entity]'s own box [Collider] from where it is along the straight move by ([dx], [dy])
     * and returns where it first touches a [Solid] on a layer in the collider's mask, or `null` if
     * the whole move is clear. It is [sweepBox] with the box placed as for a [KinematicBody]: at the
     * entity's `Transform`, offset and scaled unless [Collider.scalesWithTransform] is `false`, so a
     * game does not repeat its hero's collider size and offset to look ahead of him.
     *
     * The entity is looked up in the world the solids were last brought up to date with: `App.world`
     * for `App.collisions`, otherwise the world of the last [detect] or [moveBodies]. The solids are
     * read as [sweepBox] reads them, and a solid the box already overlaps is a hit at fraction 0.
     *
     * @throws IllegalArgumentException if [dx] or [dy] is not finite, or [entity] is not alive, has
     *   no `Transform`, no [Collider] or one that is not a [Shape.Box], has a parent, or is a [Solid].
     * @throws IllegalStateException if this instance has not met a world yet, or a solid is set up
     *   wrongly, as [Solid] describes.
     *
     * @sample samples.collision.Sweeps.sweepCollider
     */
    public fun sweepCollider(entity: Entity, dx: Float, dy: Float): SweepHit? {
        require(dx.isFinite() && dy.isFinite()) { "Sweep move ($dx, $dy) is not finite." }
        val transform = placeCollider(entity)
        solids.ensureCurrent(cellSize)
        val centerX = transform.x + placedOffsetX
        val centerY = transform.y + placedOffsetY
        solids.sweep(
            centerX - placedHalfWidth, centerY - placedHalfHeight, centerX + placedHalfWidth, centerY + placedHalfHeight,
            dx, dy, placedMask, skipsOverlaps = false,
        )
        return solids.lastHitOrNull()
    }

    /**
     * Moves a box [width] by [height] centered on ([x], [y]) by ([dx], [dy]) exactly as a
     * [KinematicBody] moves in a fixed step, without moving anything, and writes where it comes to
     * rest and what it touched into [result], which it returns. Pass the arguments by name and pass
     * a [result] kept between calls, as in
     * `slideBox(x = x, y = y, width = 24f, height = 40f, dx = speedX * dt, dy = speedY * dt, result = slide)`;
     * [dx] and [dy] are the move of one step in world units, not speeds.
     *
     * The box moves across, then up or down, and stops a [CONTACT_GAP] short of the first [Solid] on
     * a layer in [mask] each way. It passes one-way solids as a body does and moves out of a solid
     * it starts inside. A body standing on a floor also keeps to a floor up to
     * [FLOOR_SNAP_DISTANCE] below it; pass that distance as [floorSnap] when the box stood on a
     * floor before the move and is not moving up, and the default 0 otherwise. A box moving up never
     * snaps. [KinematicBody.dropThrough] has no counterpart here. The body's move and this one are
     * the same code, so a jump followed step by step with the speeds the game's system would give
     * ends where the body will, and the game zeroes its planned speeds where the body would: `dx`
     * when [BoxSlide.isOnWall], `dy` when [BoxSlide.isOnFloor] or [BoxSlide.isOnCeiling].
     *
     * The solids are read as [sweepBox] reads them. It allocates nothing when given a [result].
     *
     * @throws IllegalArgumentException if [x], [y], [dx] or [dy] is not finite, [width] or [height]
     *   is not a positive finite number, or [floorSnap] is negative or not finite.
     * @throws IllegalStateException if a solid is set up wrongly, as [Solid] describes.
     *
     * @sample samples.collision.Slides.slideBox
     */
    public fun slideBox(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        dx: Float,
        dy: Float,
        mask: CollisionLayers = CollisionLayers.ALL,
        floorSnap: Float = 0f,
        result: BoxSlide = BoxSlide(),
    ): BoxSlide {
        checkSweep(x, y, width, height, dx, dy)
        checkFloorSnap(floorSnap)
        solids.ensureCurrent(cellSize)
        solids.slide(
            x, y, 0f, 0f, width * 0.5f, height * 0.5f, dx, dy, mask.bits,
            dropLine = Float.NEGATIVE_INFINITY, floorSnap = floorSnap, result = result,
        )
        return result
    }

    /**
     * Moves [entity]'s own box [Collider] as [slideBox] moves a box, starting from where the
     * collider would be if the entity's `Transform` were at ([x], [y]), and writes into [result],
     * which it returns, where that `Transform` position comes to rest and what the box touched. The
     * box is placed as for a [KinematicBody], offset and scaled by the entity's current `Transform`
     * unless [Collider.scalesWithTransform] is `false`, and stops at the solids in the collider's
     * mask. Pass the arguments by name.
     *
     * Starting from a given position rather than the entity's own lets a planner follow a jump step
     * by step, feeding each step's [BoxSlide.x] and [BoxSlide.y] into the next, with no copy of the
     * collider's size and offset. Starting from the entity's `Transform` and moving by its body's
     * speed times the step length, it ends where the engine's move will put the body, given the same
     * [floorSnap].
     *
     * The entity is looked up as [sweepCollider] looks it up. It allocates nothing when given a
     * [result].
     *
     * @throws IllegalArgumentException if [x], [y], [dx] or [dy] is not finite, [floorSnap] is
     *   negative or not finite, or [entity] is not alive, has no `Transform`, no [Collider] or one
     *   that is not a [Shape.Box], has a parent, or is a [Solid].
     * @throws IllegalStateException if this instance has not met a world yet, or a solid is set up
     *   wrongly, as [Solid] describes.
     *
     * @sample samples.collision.Slides.slideCollider
     */
    public fun slideCollider(
        entity: Entity,
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        floorSnap: Float = 0f,
        result: BoxSlide = BoxSlide(),
    ): BoxSlide {
        require(x.isFinite() && y.isFinite()) { "Slide start ($x, $y) is not finite." }
        require(dx.isFinite() && dy.isFinite()) { "Slide move ($dx, $dy) is not finite." }
        checkFloorSnap(floorSnap)
        placeCollider(entity)
        solids.ensureCurrent(cellSize)
        solids.slide(
            x, y, placedOffsetX, placedOffsetY, placedHalfWidth, placedHalfHeight, dx, dy, placedMask,
            dropLine = Float.NEGATIVE_INFINITY, floorSnap = floorSnap, result = result,
        )
        return result
    }

    /**
     * Returns the first [Solid] on a layer in [mask] whose top a level line [width] wide centered on
     * ([x], [y]) meets when moved [depth] units straight down, or `null` if there is none within
     * [depth]. Pass the arguments by name, as in
     * `floorBelowOrNull(x = x + 16f, y = feet, width = 4f, depth = 8f)` to ask whether there is
     * floor just ahead of a walker's feet.
     *
     * The line is a falling body's feet: it lands on one-way solids whose top is at or below [y],
     * and a solid whose top is exactly at [y] is met at once. A solid the line starts inside is not a
     * floor below it and is passed through, as a body moves out of a solid it starts inside. The hit
     * is a [SweepHit] whose [SweepHit.fraction] of [depth] gives the floor's top,
     * `y + depth * fraction`, and whose normal is (0, -1). The solids are read as [sweepBox] reads
     * them. It allocates nothing besides the hit it returns.
     *
     * @throws IllegalArgumentException if [x] or [y] is not finite, or [width] or [depth] is not a
     *   positive finite number.
     * @throws IllegalStateException if a solid is set up wrongly, as [Solid] describes.
     *
     * @sample samples.collision.Sweeps.floorBelowOrNull
     */
    public fun floorBelowOrNull(
        x: Float,
        y: Float,
        width: Float,
        depth: Float,
        mask: CollisionLayers = CollisionLayers.ALL,
    ): SweepHit? {
        require(x.isFinite() && y.isFinite()) { "Probe start ($x, $y) is not finite." }
        require(width > 0f && width.isFinite()) { "Probe width $width is not a positive finite number." }
        require(depth > 0f && depth.isFinite()) { "Probe depth $depth is not a positive finite number." }
        solids.ensureCurrent(cellSize)
        val halfWidth = width * 0.5f
        solids.sweep(x - halfWidth, y, x + halfWidth, y, 0f, depth, mask.bits, skipsOverlaps = true)
        return solids.lastHitOrNull()
    }

    // The collider placeCollider found: its offset and half size in world units and its mask bits.
    private var placedOffsetX = 0f
    private var placedOffsetY = 0f
    private var placedHalfWidth = 0f
    private var placedHalfHeight = 0f
    private var placedMask = 0

    /**
     * Checks that [entity] has a box collider that can be swept, records its offset, half size and
     * mask, and returns its `Transform`.
     */
    private fun placeCollider(entity: Entity): Transform {
        val world = solids.worldOrNull
        checkNotNull(world) { "Collisions has met no world yet; call detect or moveBodies first." }
        require(world.isAlive(entity)) { "Entity $entity is not alive." }
        val transform = world.getOrNull(entity, Transform)
        requireNotNull(transform) { "Entity $entity has no Transform." }
        val collider = world.getOrNull(entity, Collider)
        requireNotNull(collider) { "Entity $entity has no Collider." }
        val shape = collider.shape
        require(shape is Shape.Box) { "Entity $entity has a collider shape $shape that is not a Box." }
        require(world.parentOrNull(entity) == null) { "Entity $entity has a parent, but only a root entity's collider can be moved in world space." }
        require(world.getOrNull(entity, Solid) == null) { "Entity $entity is a Solid, and a solid cannot be moved against the solids." }
        val scaleX = if (collider.scalesWithTransform) transform.scaleX else 1f
        val scaleY = if (collider.scalesWithTransform) transform.scaleY else 1f
        placedOffsetX = collider.offsetX * scaleX
        placedOffsetY = collider.offsetY * scaleY
        placedHalfWidth = shape.width * abs(scaleX) * 0.5f
        placedHalfHeight = shape.height * abs(scaleY) * 0.5f
        placedMask = collider.mask.bits
        return transform
    }

    private fun checkSweep(x: Float, y: Float, width: Float, height: Float, dx: Float, dy: Float) {
        require(x.isFinite() && y.isFinite()) { "Sweep start ($x, $y) is not finite." }
        require(width > 0f && height > 0f && width.isFinite() && height.isFinite()) {
            "Sweep box size ${width}x$height is not a positive finite size."
        }
        require(dx.isFinite() && dy.isFinite()) { "Sweep move ($dx, $dy) is not finite." }
    }

    private fun checkFloorSnap(floorSnap: Float) {
        require(floorSnap >= 0f && floorSnap.isFinite()) { "Floor snap $floorSnap is negative or not finite." }
    }

    /**
     * Adds to [destination] every entity whose collider overlaps [shape] centered on ([x], [y])
     * and is on a layer in [mask], in ascending order of [Entity.index], and returns
     * [destination]. The shape is in world units and is not scaled. Overlap is tested as in
     * [detect], so a collider that only touches the shape is not found.
     *
     * The query reads the snapshot of the last [detect], as the class description explains.
     * It allocates nothing beyond what [destination] needs to grow.
     *
     * @throws IllegalArgumentException if [x] or [y] is not finite.
     *
     * @sample samples.collision.CollisionQueries.overlappingTo
     */
    public fun <C : MutableCollection<in Entity>> overlappingTo(
        destination: C,
        shape: Shape,
        x: Float,
        y: Float,
        mask: CollisionLayers = CollisionLayers.ALL,
    ): C {
        val found = collectOverlaps(shape, x, y, mask)
        matchKeys.sort(0, found)
        for (k in 0 until found) destination.add(Entity(entities[matchKeys[k].toInt()]))
        return destination
    }

    /**
     * Returns the entity with the smallest [Entity.index] whose collider overlaps [shape]
     * centered on ([x], [y]) and is on a layer in [mask], or `null` if there is none. It finds
     * the first entity [overlapping] would return, without building a list.
     *
     * The query reads the snapshot of the last [detect], as the class description explains.
     *
     * @throws IllegalArgumentException if [x] or [y] is not finite.
     *
     * @sample samples.collision.CollisionQueries.firstOverlappingOrNull
     */
    public fun firstOverlappingOrNull(
        shape: Shape,
        x: Float,
        y: Float,
        mask: CollisionLayers = CollisionLayers.ALL,
    ): Entity? {
        val found = collectOverlaps(shape, x, y, mask)
        if (found == 0) return null
        var least = matchKeys[0]
        for (k in 1 until found) if (matchKeys[k] < least) least = matchKeys[k]
        return Entity(entities[least.toInt()])
    }

    /**
     * Fills the snapshot from every entity in [world] with a [Collider] and a position, except those
     * owned by a scene [scenes] holds frozen.
     */
    private fun gather(world: World, scenes: SceneStack?) {
        var count = 0
        val colliders = world.storeOrNull(Collider)
        if (colliders != null) {
            ensureColliderCapacity(colliders.size)
            val locals = world.storeOrNull(Transform)
            val worlds = world.storeOrNull(WorldTransform)
            val bounds = bounds
            val frozen = if (scenes != null && scenes.hasFrozen) scenes else null
            for (k in 0 until colliders.size) {
                val raw = colliders.entities[k]
                val index = Entity(raw).index
                if (frozen != null && frozen.isFrozen(world.ownerAt(index))) continue
                val x: Float
                val y: Float
                val scaleX: Float
                val scaleY: Float
                val worldSlot = if (worlds != null) slotOf(worlds, index, raw) else -1
                if (worldSlot >= 0) {
                    val transform = worlds!!.items.itemAt<WorldTransform>(worldSlot)
                    x = transform.x
                    y = transform.y
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                } else {
                    val localSlot = if (locals != null) slotOf(locals, index, raw) else -1
                    if (localSlot < 0) continue
                    val transform = locals!!.items.itemAt<Transform>(localSlot)
                    x = transform.x
                    y = transform.y
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                }
                val collider = colliders.items.itemAt<Collider>(k)
                val scalesWithTransform = collider.scalesWithTransform
                val colliderScaleX = if (scalesWithTransform) scaleX else 1f
                val colliderScaleY = if (scalesWithTransform) scaleY else 1f
                val centerX = x + collider.offsetX * colliderScaleX
                val centerY = y + collider.offsetY * colliderScaleY
                val halfWidth: Float
                val halfHeight: Float
                val circle: Boolean
                when (val shape = collider.shape) {
                    is Shape.Circle -> {
                        halfWidth = shape.radius * abs(colliderScaleX)
                        halfHeight = halfWidth
                        circle = true
                    }
                    is Shape.Box -> {
                        halfWidth = shape.width * abs(colliderScaleX) * 0.5f
                        halfHeight = shape.height * abs(colliderScaleY) * 0.5f
                        circle = false
                    }
                }
                val minX = centerX - halfWidth
                val minY = centerY - halfHeight
                val maxX = centerX + halfWidth
                val maxY = centerY + halfHeight
                if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) continue
                val o = count * STRIDE
                bounds[o] = minX
                bounds[o + 1] = minY
                bounds[o + 2] = maxX
                bounds[o + 3] = maxY
                bounds[o + 4] = centerX
                bounds[o + 5] = centerY
                bounds[o + 6] = halfWidth
                bounds[o + 7] = halfHeight
                isCircle[count] = circle
                layerBits[count] = collider.layers.bits
                maskBits[count] = collider.mask.bits
                entities[count] = raw
                count++
            }
        }
        colliderCount = count
    }

    /** Sorts the snapshot into cells with a counting sort: count per cell, prefix sums, scatter. */
    private fun buildGrid() {
        val inverse = 1f / cellSize
        gridInverseCell = inverse
        if (tableStamp == Int.MAX_VALUE) {
            tableStamps.fill(0)
            tableStamp = 0
        }
        tableStamp++
        cellCount = 0
        entryCount = 0
        largeCount = 0
        val bounds = bounds
        for (c in 0 until colliderCount) {
            val o = c * STRIDE
            val x0 = cellOf(bounds[o], inverse)
            val y0 = cellOf(bounds[o + 1], inverse)
            val x1 = cellOf(bounds[o + 2], inverse)
            val y1 = cellOf(bounds[o + 3], inverse)
            val cells = (x1.toLong() - x0 + 1) * (y1.toLong() - y0 + 1)
            if (cells > LARGE_CELLS) {
                isLarge[c] = true
                if (largeCount == large.size) large = large.copyOf(largeCount * 2)
                large[largeCount++] = c
                continue
            }
            isLarge[c] = false
            ensureEntryCapacity(entryCount + cells.toInt())
            for (cy in y0..y1) {
                for (cx in x0..x1) {
                    val cell = cellOrAdd(cx, cy)
                    cellCursors[cell]++
                    entryCells[entryCount] = cell
                    entryColliders[entryCount] = c
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
        if (sorted.size < entryCount) {
            val size = entryColliders.size
            sorted = IntArray(size)
            sortedBoxes = FloatArray(size * BOX_STRIDE)
            sortedLayers = IntArray(size)
            sortedMasks = IntArray(size)
        }
        val boxes = sortedBoxes
        for (e in 0 until entryCount) {
            val position = cellCursors[entryCells[e]]++
            val c = entryColliders[e]
            sorted[position] = c
            val o = c * STRIDE
            val d = position * BOX_STRIDE
            boxes[d] = bounds[o]
            boxes[d + 1] = bounds[o + 1]
            boxes[d + 2] = bounds[o + 2]
            boxes[d + 3] = bounds[o + 3]
            sortedLayers[position] = layerBits[c]
            sortedMasks[position] = maskBits[c]
        }
    }

    /**
     * Tests the colliders of each cell against each other, reporting a pair only from the cell
     * that holds the top-left corner of the intersection of their bounding boxes. That corner lies
     * in both boxes, so it is in a cell both colliders were placed in, and in exactly one cell.
     * Large colliders are then tested against every other collider.
     */
    private fun findPairs(events: Events) {
        pairCount = 0
        val overlaps = events.channelFor(Overlap::class)
        val boxes = sortedBoxes
        val layers = sortedLayers
        val masks = sortedMasks
        val inverse = gridInverseCell
        for (cell in 0 until cellCount) {
            val start = cellStarts[cell]
            val end = cellStarts[cell + 1]
            if (end - start < 2) continue
            val key = cellKeys[cell]
            val cellX = (key shr 32).toInt()
            val cellY = key.toInt()
            for (p in start until end - 1) {
                val d = p * BOX_STRIDE
                val aMinX = boxes[d]
                val aMinY = boxes[d + 1]
                val aMaxX = boxes[d + 2]
                val aMaxY = boxes[d + 3]
                val layersA = layers[p]
                val maskA = masks[p]
                for (q in p + 1 until end) {
                    val e = q * BOX_STRIDE
                    val bMinX = boxes[e]
                    val bMinY = boxes[e + 1]
                    // Non-short-circuit `and` makes one branch instead of four hard-to-predict ones.
                    if (!((aMinX < boxes[e + 2]) and (bMinX < aMaxX) and (aMinY < boxes[e + 3]) and (bMinY < aMaxY))) continue
                    if (maskA and layers[q] == 0 && masks[q] and layersA == 0) continue
                    if (cellOf(max(aMinX, bMinX), inverse) != cellX || cellOf(max(aMinY, bMinY), inverse) != cellY) continue
                    val i = sorted[p]
                    val j = sorted[q]
                    if (!shapesOverlap(i, j)) continue
                    report(overlaps, i, j)
                }
            }
        }
        for (k in 0 until largeCount) {
            val i = large[k]
            for (j in 0 until colliderCount) {
                // A pair of large colliders is tested from the one gathered first.
                if (j == i || (isLarge[j] && j < i)) continue
                if (maskBits[i] and layerBits[j] == 0 && maskBits[j] and layerBits[i] == 0) continue
                if (!boundsOverlap(i, j) || !shapesOverlap(i, j)) continue
                report(overlaps, i, j)
            }
        }
    }

    /** Publishes the pair of colliders [i] and [j] into [overlaps] with the smaller entity index first. */
    private fun report(overlaps: Events.Channel, i: Int, j: Int) {
        val a = Entity(entities[i])
        val b = Entity(entities[j])
        overlaps.add(if (a.index < b.index) Overlap(a, b) else Overlap(b, a))
        pairCount++
    }

    private fun boundsOverlap(i: Int, j: Int): Boolean {
        val io = i * STRIDE
        val jo = j * STRIDE
        return boxesOverlap(
            bounds[io], bounds[io + 1], bounds[io + 2], bounds[io + 3],
            bounds[jo], bounds[jo + 1], bounds[jo + 2], bounds[jo + 3],
        )
    }

    /** Returns whether the shapes of colliders [i] and [j], whose bounding boxes overlap, overlap. */
    private fun shapesOverlap(i: Int, j: Int): Boolean {
        val circleI = isCircle[i]
        val circleJ = isCircle[j]
        if (!circleI && !circleJ) return true
        val io = i * STRIDE
        val jo = j * STRIDE
        if (circleI && circleJ) {
            return circlesOverlap(bounds[io + 4], bounds[io + 5], bounds[io + 6], bounds[jo + 4], bounds[jo + 5], bounds[jo + 6])
        }
        val circle = if (circleI) io else jo
        val box = if (circleI) jo else io
        return circleOverlapsBox(
            bounds[circle + 4], bounds[circle + 5], bounds[circle + 6],
            bounds[box], bounds[box + 1], bounds[box + 2], bounds[box + 3],
        )
    }

    /**
     * Collects into [matchKeys] every collider on a layer in [mask] that overlaps [shape] centered
     * on ([x], [y]), each once, and returns how many there are.
     */
    private fun collectOverlaps(shape: Shape, x: Float, y: Float, mask: CollisionLayers): Int {
        require(x.isFinite() && y.isFinite()) { "Query position ($x, $y) is not finite." }
        if (colliderCount == 0) return 0
        val halfWidth: Float
        val halfHeight: Float
        when (shape) {
            is Shape.Circle -> {
                halfWidth = shape.radius
                halfHeight = shape.radius
                queryIsCircle = true
            }
            is Shape.Box -> {
                halfWidth = shape.width * 0.5f
                halfHeight = shape.height * 0.5f
                queryIsCircle = false
            }
        }
        queryMinX = x - halfWidth
        queryMinY = y - halfHeight
        queryMaxX = x + halfWidth
        queryMaxY = y + halfHeight
        queryCenterX = x
        queryCenterY = y
        queryRadius = halfWidth
        queryMask = mask.bits
        if (queryStamp == Int.MAX_VALUE) {
            queryStamps.fill(0)
            queryStamp = 0
        }
        val stamp = ++queryStamp
        val inverse = gridInverseCell
        val x0 = cellOf(queryMinX, inverse)
        val y0 = cellOf(queryMinY, inverse)
        val x1 = cellOf(queryMaxX, inverse)
        val y1 = cellOf(queryMaxY, inverse)
        val cells = (x1.toLong() - x0 + 1) * (y1.toLong() - y0 + 1)
        var found = 0
        if (cells > cellCount || !queryMaxX.isFinite() || !queryMaxY.isFinite()) {
            // The query covers more cells than the grid has, so testing every collider is cheaper.
            for (c in 0 until colliderCount) found = testQuery(c, found)
            return found
        }
        for (cy in y0..y1) {
            for (cx in x0..x1) {
                val cell = cellOrNegative(cx, cy)
                if (cell < 0) continue
                for (p in cellStarts[cell] until cellStarts[cell + 1]) {
                    val c = sorted[p]
                    if (queryStamps[c] == stamp) continue
                    queryStamps[c] = stamp
                    found = testQuery(c, found)
                }
            }
        }
        for (k in 0 until largeCount) found = testQuery(large[k], found)
        return found
    }

    /** Tests collider [c] against the query, appends it to [matchKeys] on a hit, and returns the new match count. */
    private fun testQuery(c: Int, found: Int): Int {
        if (queryMask and layerBits[c] == 0) return found
        val o = c * STRIDE
        val bounds = bounds
        if (!boxesOverlap(queryMinX, queryMinY, queryMaxX, queryMaxY, bounds[o], bounds[o + 1], bounds[o + 2], bounds[o + 3])) return found
        val hit = when {
            queryIsCircle && isCircle[c] ->
                circlesOverlap(queryCenterX, queryCenterY, queryRadius, bounds[o + 4], bounds[o + 5], bounds[o + 6])
            queryIsCircle ->
                circleOverlapsBox(queryCenterX, queryCenterY, queryRadius, bounds[o], bounds[o + 1], bounds[o + 2], bounds[o + 3])
            isCircle[c] ->
                circleOverlapsBox(bounds[o + 4], bounds[o + 5], bounds[o + 6], queryMinX, queryMinY, queryMaxX, queryMaxY)
            else -> true
        }
        if (!hit) return found
        if (found == matchKeys.size) matchKeys = matchKeys.copyOf(found * 2)
        matchKeys[found] = (Entity(entities[c]).index.toLong() shl 32) or c.toLong()
        return found + 1
    }

    /** Returns the number of the cell at ([cx], [cy]), adding the cell if it is new. */
    private fun cellOrAdd(cx: Int, cy: Int): Int {
        val key = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFF_FFFFL)
        val mask = table.size - 1
        var i = hash(key) and mask
        while (tableStamps[i] == tableStamp) {
            val cell = table[i]
            if (cellKeys[cell] == key) return cell
            i = (i + 1) and mask
        }
        if (cellCount == cellKeys.size) growCells()
        val cell = cellCount++
        cellKeys[cell] = key
        cellCursors[cell] = 0
        table[i] = cell
        tableStamps[i] = tableStamp
        if (cellCount * 2 > table.size) rehash()
        return cell
    }

    /** Returns the number of the cell at ([cx], [cy]), or -1 if no collider touches it. */
    private fun cellOrNegative(cx: Int, cy: Int): Int {
        val key = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFF_FFFFL)
        val mask = table.size - 1
        var i = hash(key) and mask
        while (tableStamps[i] == tableStamp) {
            val cell = table[i]
            if (cellKeys[cell] == key) return cell
            i = (i + 1) and mask
        }
        return -1
    }

    private fun rehash() {
        val size = table.size * 2
        val grown = IntArray(size)
        val stamps = IntArray(size)
        val mask = size - 1
        for (cell in 0 until cellCount) {
            var i = hash(cellKeys[cell]) and mask
            while (stamps[i] == tableStamp) i = (i + 1) and mask
            grown[i] = cell
            stamps[i] = tableStamp
        }
        table = grown
        tableStamps = stamps
    }

    private fun growCells() {
        val size = cellKeys.size * 2
        cellKeys = cellKeys.copyOf(size)
        cellCursors = cellCursors.copyOf(size)
    }

    private fun ensureEntryCapacity(size: Int) {
        if (entryCells.size >= size) return
        var grown = entryCells.size * 2
        while (grown < size) grown *= 2
        entryCells = entryCells.copyOf(grown)
        entryColliders = entryColliders.copyOf(grown)
    }

    /** Makes room for [size] colliders. The snapshot is rewritten by every detection, so nothing is copied. */
    private fun ensureColliderCapacity(size: Int) {
        if (isCircle.size >= size) return
        var grown = isCircle.size * 2
        while (grown < size) grown *= 2
        bounds = FloatArray(grown * STRIDE)
        isCircle = BooleanArray(grown)
        layerBits = IntArray(grown)
        maskBits = IntArray(grown)
        entities = LongArray(grown)
        isLarge = BooleanArray(grown)
        queryStamps = IntArray(grown)
    }

    override fun toString(): String =
        "Collisions(cellSize=$cellSize, colliders=$colliderCount, cells=$cellCount, pairs=$pairCount, solids=$solidCount)"

    /** Holds the distances a [KinematicBody] move keeps to and looks for solids. */
    public companion object {
        /**
         * The gap in world units a [KinematicBody] keeps from a [Solid] it stops at, 0.01. The body
         * stops this far short of the face, so it never overlaps the solid and the next move along
         * the face, such as walking on a floor, does not count as running into it. It is small
         * enough not to show, and larger than the rounding of positions within tens of thousands
         * of units of the origin.
         */
        public const val CONTACT_GAP: Float = 0.01f

        /**
         * The distance in world units, 2, that a [KinematicBody] that stood on a floor and is not
         * moving up looks down after its move for a floor to land on, so walking down a step of
         * up to 2 units keeps it on the floor instead of falling for a step.
         */
        public const val FLOOR_SNAP_DISTANCE: Float = 2f

        private const val DEFAULT_CELL_SIZE: Float = 64f
        private const val INITIAL_COLLIDERS: Int = 64

        /** Floats per collider in the snapshot's bounds array. */
        private const val STRIDE: Int = 8

        /** Floats per sorted entry in the bounding box copies. */
        private const val BOX_STRIDE: Int = 4

        /** The most cells a collider may touch and still be placed in the grid. */
        private const val LARGE_CELLS: Long = 256

        private fun checkCellSize(size: Float): Float {
            require(size > 0f && size.isFinite()) { "Cell size $size is not a positive finite number." }
            return size
        }

        private fun hash(key: Long): Int {
            val mixed = key * -7046029254386353131L
            return (mixed ushr 32).toInt() xor mixed.toInt()
        }
    }
}

/**
 * Returns every entity whose collider overlaps [shape] centered on ([x], [y]) and is on a layer
 * in [mask], in ascending order of [Entity.index]. It reads the snapshot of the last
 * [Collisions.detect]; see [Collisions.overlappingTo] for the details, and use it with a reused list
 * to avoid allocating one per call.
 *
 * @throws IllegalArgumentException if [x] or [y] is not finite.
 *
 * @sample samples.collision.CollisionQueries.overlapping
 */
public fun Collisions.overlapping(
    shape: Shape,
    x: Float,
    y: Float,
    mask: CollisionLayers = CollisionLayers.ALL,
): List<Entity> = overlappingTo(ArrayList(), shape, x, y, mask)

/** Returns the grid cell coordinate of [value], given the inverse of the cell size. */
@Suppress("NOTHING_TO_INLINE")
private inline fun cellOf(value: Float, inverseCell: Float): Int = floor(value * inverseCell).toInt()

/** Returns the dense slot of the entity with index [index] and handle [raw] in [store], or -1. */
@Suppress("NOTHING_TO_INLINE")
private inline fun slotOf(store: ComponentStore<*>, index: Int, raw: Long): Int {
    val slot = store.sparse[index]
    return if (slot >= 0 && store.entities[slot] == raw) slot else -1
}

/** Reads a store item without a type check; the store's type guarantees it. */
@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
private inline fun <T> Array<Any?>.itemAt(slot: Int): T = this[slot] as T

/** Returns whether the circles at ([ax], [ay]) of radius [ar] and at ([bx], [by]) of radius [br] share interior area. */
internal fun circlesOverlap(ax: Float, ay: Float, ar: Float, bx: Float, by: Float, br: Float): Boolean {
    val dx = bx - ax
    val dy = by - ay
    val reach = ar + br
    return dx * dx + dy * dy < reach * reach
}

/**
 * Returns whether two axis-aligned boxes, given by their min and max corners, share interior area.
 * The non-short-circuit `and` compiles to one branch, which mispredicts far less than four.
 */
internal fun boxesOverlap(
    aMinX: Float, aMinY: Float, aMaxX: Float, aMaxY: Float,
    bMinX: Float, bMinY: Float, bMaxX: Float, bMaxY: Float,
): Boolean = (aMinX < bMaxX) and (bMinX < aMaxX) and (aMinY < bMaxY) and (bMinY < aMaxY)

/**
 * Returns whether the circle at ([cx], [cy]) of radius [radius] and the axis-aligned box from
 * ([minX], [minY]) to ([maxX], [maxY]) share interior area, by the box point closest to the center.
 */
internal fun circleOverlapsBox(cx: Float, cy: Float, radius: Float, minX: Float, minY: Float, maxX: Float, maxY: Float): Boolean {
    val dx = cx - cx.coerceIn(minX, maxX)
    val dy = cy - cy.coerceIn(minY, maxY)
    return dx * dx + dy * dy < radius * radius
}
