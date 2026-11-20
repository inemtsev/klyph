package klyph.render

import klyph.entity.ComponentStore
import klyph.entity.Entity
import klyph.entity.Parent
import klyph.entity.PreviousTransform
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.drawnTransform
import klyph.tiles.Tile
import klyph.tiles.TileLayer
import kotlin.math.PI
import kotlin.math.floor

/**
 * The cells of every [TileLayer] that the camera sees in one frame. [update] works out, before the
 * renderer writes any record, which columns and rows of each layer touch the view, so the
 * renderer can make room for them and a layer that cannot be drawn throws before any buffer is
 * mapped. The renderer then walks a layer's cells with [begin] and [next], which fills [template]
 * with the record of each non-empty cell in turn.
 *
 * A cell's record puts a quad of the cell's size at the cell's center, with the origin in the
 * middle. A tile flipped diagonally, which swaps its top-right and bottom-left corners, is drawn
 * as a quarter turn clockwise with the flips that make the turn a mirror across that diagonal: the
 * turned quad's x flip is the tile's y flip and its y flip the opposite of the tile's x flip,
 * which is what Tiled draws.
 *
 * The arrays are kept between frames, so a frame allocates nothing once they have grown to the
 * number of layers.
 */
internal class TilePlan {
    /** Number of layers with cells in view in the last [update], the slots from 0 up to it. */
    var layerCount: Int = 0
        private set

    /** The most records the layers of the last [update] write: each layer's cells in view or its tiles, whichever are fewer. */
    var cellsInView: Long = 0
        private set

    /** The record of the cell [next] found last. */
    val template: FloatArray = FloatArray(SPRITE_RECORD_FLOATS)

    private var layers = arrayOfNulls<TileLayer>(INITIAL_SLOTS)

    /** Per slot, [RANGE_INTS] ints: the first column and row in view, and the column and row after the last. */
    private var ranges = IntArray(INITIAL_SLOTS * RANGE_INTS)

    /** Per slot, [GEOMETRY_FLOATS] floats: the layer's top-left corner and the size of a cell, in world units. */
    private var geometry = FloatArray(INITIAL_SLOTS * GEOMETRY_FLOATS)

    // The area the camera sees: the box along the world's axes around the view.
    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f

    // The walk of [begin] and [next]: the layer, the cell after the last one returned, and the end of its range.
    private var walkLayer: TileLayer? = null
    private var walkSlot = 0
    private var walkColumn = 0
    private var walkRow = 0

    /**
     * Plans the cells of every visible [TileLayer] in [world] seen through [camera], with layers
     * on interpolated entities blended [alpha] of the way between fixed steps. The camera is read
     * as it is: the engine clamps it to its bounds before it draws.
     *
     * @throws IllegalStateException if a layer that would be drawn has a parent, a rotation or a
     *   scale that is not positive and finite; the message names the entity.
     */
    fun update(world: World, camera: Camera, alpha: Float) {
        val previousCount = layerCount
        layerCount = 0
        cellsInView = 0
        walkLayer = null
        try {
            planLayers(world, camera, alpha)
        } finally {
            // Slots the last plan used and this one did not would otherwise keep layers, and their worlds' tiles, alive.
            if (layerCount < previousCount) layers.fill(null, layerCount, previousCount)
        }
    }

    private fun planLayers(world: World, camera: Camera, alpha: Float) {
        val store = world.storeOrNull(TileLayer)
        if (store == null || store.size == 0) return
        setView(camera)
        val transforms = world.storeOrNull(Transform) ?: return
        val previous = world.storeOrNull(PreviousTransform)
        val parents = world.storeOrNull(Parent)
        for (index in 0 until store.size) {
            val layer = store.items[index] as TileLayer
            val entity = Entity(store.entities[index])
            val local = if (layer.isVisible) transforms.getOrNull(entity) else null
            if (local != null) planLayer(entity, layer, local, previous, parents, alpha)
        }
    }

    /** Returns the [TileLayer] of [slot]. */
    fun layerOf(slot: Int): TileLayer = checkNotNull(layers[slot]) { "Tile plan slot $slot is empty." }

    /** Starts a walk over the cells in view of the layer in [slot]; [next] returns them one by one. */
    fun begin(slot: Int) {
        walkSlot = slot
        walkLayer = layerOf(slot)
        walkColumn = ranges[slot * RANGE_INTS]
        walkRow = ranges[slot * RANGE_INTS + 1]
    }

    /**
     * Moves to the next non-empty cell in view of the layer [begin] started, row by row, writes its
     * record into [template] and returns `true`, or returns `false` when there is none left.
     */
    fun next(): Boolean {
        val layer = walkLayer ?: return false
        val base = walkSlot * RANGE_INTS
        val firstColumn = ranges[base]
        val endColumn = ranges[base + 2]
        val endRow = ranges[base + 3]
        val cells = layer.cells
        val columns = layer.columns
        var found = false
        while (!found && walkRow < endRow) {
            val cell = cells[walkRow * columns + walkColumn]
            val column = walkColumn
            val row = walkRow
            if (++walkColumn == endColumn) {
                walkColumn = firstColumn
                walkRow++
            }
            if (cell != 0) {
                fill(layer, Tile(cell), column, row)
                found = true
            }
        }
        if (!found) walkLayer = null
        return found
    }

    private fun grow(slots: Int) {
        var size = layers.size
        while (size < slots) size *= 2
        layers = layers.copyOf(size)
        ranges = ranges.copyOf(size * RANGE_INTS)
        geometry = geometry.copyOf(size * GEOMETRY_FLOATS)
    }

    private fun setView(camera: Camera) {
        val centerX = camera.x + camera.offsetX
        val centerY = camera.y + camera.offsetY
        val halfWidth = camera.visibleWidth * 0.5f
        val halfHeight = camera.visibleHeight * 0.5f
        val reachX = reachAlong(camera.rotation, halfWidth, halfHeight)
        val reachY = reachAlong(camera.rotation, halfHeight, halfWidth)
        minX = centerX - reachX
        maxX = centerX + reachX
        minY = centerY - reachY
        maxY = centerY + reachY
    }

    /** Adds [layer], on [entity], to the plan if any of its cells is in view. */
    private fun planLayer(
        entity: Entity,
        layer: TileLayer,
        local: Transform,
        previous: ComponentStore<PreviousTransform>?,
        parents: ComponentStore<Parent>?,
        alpha: Float,
    ) {
        val parent = parents?.getOrNull(entity)?.entity
        check(parent == null) { "$entity has a TileLayer, which needs an entity without a parent, but its parent is $parent." }
        drawnTransform(local, null, previous?.getOrNull(entity), alpha) { x, y, rotation, scaleX, scaleY ->
            check(rotation == 0f) { "$entity has a TileLayer, which cannot be rotated, but its rotation is $rotation." }
            check(scaleX > 0f && scaleX.isFinite() && scaleY > 0f && scaleY.isFinite()) {
                "$entity has a TileLayer, which needs a positive, finite scale, but its scale is ${scaleX}x$scaleY."
            }
            val cellWidth = layer.tileSet.tileWidth * scaleX
            val cellHeight = layer.tileSet.tileHeight * scaleY
            val firstColumn = firstCell(minX - x, cellWidth)
            val endColumn = endCell(maxX - x, cellWidth, layer.columns)
            val firstRow = firstCell(minY - y, cellHeight)
            val endRow = endCell(maxY - y, cellHeight, layer.rows)
            if (firstColumn >= endColumn || firstRow >= endRow) return@drawnTransform
            val slot = layerCount
            if (slot == layers.size) grow(slot + 1)
            layers[slot] = layer
            ranges[slot * RANGE_INTS] = firstColumn
            ranges[slot * RANGE_INTS + 1] = firstRow
            ranges[slot * RANGE_INTS + 2] = endColumn
            ranges[slot * RANGE_INTS + 3] = endRow
            geometry[slot * GEOMETRY_FLOATS] = x
            geometry[slot * GEOMETRY_FLOATS + 1] = y
            geometry[slot * GEOMETRY_FLOATS + 2] = cellWidth
            geometry[slot * GEOMETRY_FLOATS + 3] = cellHeight
            cellsInView += minOf((endColumn - firstColumn).toLong() * (endRow - firstRow), layer.tileCount.toLong())
            layerCount = slot + 1
        }
    }

    /** Writes the record of [tile] in the cell at [column] and [row] of [layer], the layer being walked, into [template]. */
    private fun fill(layer: TileLayer, tile: Tile, column: Int, row: Int) {
        val base = walkSlot * GEOMETRY_FLOATS
        val cellWidth = geometry[base + 2]
        val cellHeight = geometry[base + 3]
        val region = layer.tileSet.sheet[tile.id]
        val record = template
        record[0] = geometry[base] + (column + 0.5f) * cellWidth
        record[1] = geometry[base + 1] + (row + 0.5f) * cellHeight
        record[3] = 0f
        record[6] = 0.5f
        record[7] = 0.5f
        val flipX: Boolean
        val flipY: Boolean
        if (tile.isFlippedDiagonally) {
            // A quarter turn clockwise puts the quad's x axis down the cell and its y axis to the left,
            // so the quad is as wide as the cell is tall, and the flips that make the turn a mirror
            // across the diagonal are the tile's y flip on x and the opposite of its x flip on y.
            record[2] = QUARTER_TURN
            record[4] = cellHeight
            record[5] = cellWidth
            flipX = tile.isFlippedY
            flipY = !tile.isFlippedX
        } else {
            record[2] = 0f
            record[4] = cellWidth
            record[5] = cellHeight
            flipX = tile.isFlippedX
            flipY = tile.isFlippedY
        }
        record[8] = if (flipX) region.u1 else region.u0
        record[10] = if (flipX) region.u0 else region.u1
        record[9] = if (flipY) region.v1 else region.v0
        record[11] = if (flipY) region.v0 else region.v1
        val color = layer.color
        record[12] = color.red
        record[13] = color.green
        record[14] = color.blue
        record[15] = color.alpha
    }

    private companion object {
        const val INITIAL_SLOTS = 4

        /** Ints of [ranges] per slot. */
        const val RANGE_INTS = 4

        /** Floats of [geometry] per slot. */
        const val GEOMETRY_FLOATS = 4

        /** A quarter turn clockwise on screen, in radians. */
        const val QUARTER_TURN: Float = (PI / 2).toFloat()
    }
}

/**
 * Returns the first of the cells [size] apart, starting at 0, that reaches past [offset], or 0 if
 * that is before the first; a number that is not finite gives 0 or a large count, so a layer at an
 * infinite position draws nothing.
 */
private fun firstCell(offset: Float, size: Float): Int {
    val cell = floor(offset / size)
    return if (cell > 0f) cell.coerceAtMost(Int.MAX_VALUE.toFloat()).toInt() else 0
}

/** Returns the cell after the last of the cells [size] apart, starting at 0, that starts before [offset], at most [count]. */
private fun endCell(offset: Float, size: Float, count: Int): Int {
    val cell = floor(offset / size) + 1f
    return if (cell > 0f) minOf(cell.coerceAtMost(count.toFloat()).toInt(), count) else 0
}
