package klyph.tiles

import klyph.math.Rect

/**
 * A grid of [columns] by [rows] cells, each holding a [Tile] of [tileSet], read through [get].
 * Column 0 is the left edge and row 0 the top, so cell (`column`, `row`) covers the area from
 * `column * tileSet.tileWidth`, `row * tileSet.tileHeight` across one tile, in the grid's own
 * units, and y points down as in the rest of the world.
 *
 * Both kinds of tile layer are grids: a [TileMapLayer], read from a map file and never changed,
 * and a [TileLayer], the component that draws and that a game may change. [tileAt],
 * [solidRectangles], [toTileLayer] and `Commands.spawnSolids` work on either.
 *
 * Has no sample because a [TileSet] needs a texture, which needs a GPU device.
 */
public interface TileGrid {
    /** The tile set every cell's tile belongs to, which gives the tile size and which tiles are solid. */
    public val tileSet: TileSet

    /** Number of cells across. */
    public val columns: Int

    /** Number of cells down. */
    public val rows: Int

    /**
     * Returns the tile in the cell at [column] and [row], [Tile.EMPTY] when the cell has none.
     * For a cell at a position in world units, use [tileAt].
     *
     * @throws IndexOutOfBoundsException if [column] is not in `0 until columns` or [row] is not in
     *   `0 until rows`.
     */
    public operator fun get(column: Int, row: Int): Tile
}

/**
 * Returns the tile under the point ([x], [y]), given in the grid's own units, with (0, 0) at the
 * grid's top-left corner, or [Tile.EMPTY] when the point is outside the grid. For a [TileLayer]
 * on an entity at (`layerX`, `layerY`) with scale 1, pass the world position minus the entity's,
 * as in `layer.tileAt(x - layerX, y - layerY)`.
 *
 * A point on the edge between two cells belongs to the cell right of it or below it.
 *
 * Has no sample because a [TileSet] needs a texture, which needs a GPU device.
 */
public fun TileGrid.tileAt(x: Float, y: Float): Tile {
    if (!(x >= 0f && y >= 0f)) return Tile.EMPTY
    val column = x / tileSet.tileWidth
    val row = y / tileSet.tileHeight
    return if (column < columns && row < rows) get(column.toInt(), row.toInt()) else Tile.EMPTY
}

/**
 * Returns rectangles that together cover exactly the cells whose tile [isSolid], in the grid's
 * own units, with (0, 0) at the grid's top-left corner, as few as this simple merge finds: each
 * row's runs of neighboring solid cells become one rectangle, and runs that cover the same
 * columns in the rows below grow it downwards. The rectangles are sorted from the top row down
 * and left to right, and never overlap.
 *
 * [isSolid] defaults to [TileSet.isSolid], the tiles of [TileSet.solidIds]; pass
 * `tileSet::isOneWay` for the one-way ledges, or any test of your own. `Commands.spawnSolids`
 * spawns a solid for each rectangle; call this to make colliders of another kind, or to draw
 * the walls in a debug view.
 *
 * Has no sample because a [TileSet] needs a texture, which needs a GPU device.
 */
public fun TileGrid.solidRectangles(isSolid: (Tile) -> Boolean = tileSet::isSolid): List<Rect> {
    val runs = SolidRuns(tileSet.tileWidth, tileSet.tileHeight)
    for (row in 0 until rows) {
        var column = 0
        while (column < columns) {
            if (isSolid(get(column, row))) {
                val start = column
                while (column < columns && isSolid(get(column, row))) column++
                runs.add(start, column, row)
            } else {
                column++
            }
        }
        runs.endRow(row)
    }
    runs.endRow(rows)
    val result = runs.rectangles
    result.sortWith(compareBy<Rect> { it.y }.thenBy { it.x })
    return result
}

/**
 * The runs of solid cells of [TileGrid.solidRectangles], merged downwards into [rectangles] of
 * cells [width] by [height]. Each row's runs are [add]ed left to right and the row is ended with
 * [endRow]; a run that covers the same columns as one of the row above grows it, and one of the
 * row above that no run continues becomes a rectangle.
 */
private class SolidRuns(private val width: Float, private val height: Float) {
    val rectangles = ArrayList<Rect>()

    // The runs still growing downwards, left to right, as [first column, end column, first row] each,
    // and those the row being read continues or starts.
    private var open = ArrayList<IntArray>()
    private var next = ArrayList<IntArray>()

    /** The first run of [open] that no run of this row has been matched against yet. */
    private var above = 0

    /**
     * Adds the run from column [start] until [end] in [row]. Both lists are sorted by first column and their runs do not
     * overlap, so one pass over each matches them up: every open run left of this one, or starting where it starts with
     * another end, can grow no further.
     */
    fun add(start: Int, end: Int, row: Int) {
        while (above < open.size && open[above][0] < start) close(open[above++], row)
        val run = open.getOrNull(above)
        if (run != null && run[0] == start) {
            above++
            if (run[1] == end) {
                next.add(run)
                return
            }
            close(run, row)
        }
        next.add(intArrayOf(start, end, row))
    }

    /**
     * Ends [row]: the open runs it did not continue become rectangles. Called with the grid's row
     * count after the last row, it turns every run still open into one.
     */
    fun endRow(row: Int) {
        while (above < open.size) close(open[above++], row)
        val done = open
        open = next
        next = done
        next.clear()
        above = 0
    }

    /** Adds the rectangle of [run], which grew down to the row before [row]. */
    private fun close(run: IntArray, row: Int) {
        rectangles.add(Rect(run[0] * width, run[2] * height, (run[1] - run[0]) * width, (row - run[2]) * height))
    }

    override fun toString(): String = "SolidRuns(open=${open.size}, rectangles=${rectangles.size})"
}

/**
 * Returns a new [TileLayer] holding a copy of this grid's cells and its [TileGrid.tileSet], which
 * a game may change and add to an entity to draw. The copy is independent: changing one does not
 * change the other. Its sprite layer, color and visibility are [TileLayer]'s defaults;
 * `Commands.spawnTileLayer` copies a map layer's visibility and opacity too.
 *
 * Has no sample because a [TileSet] needs a texture, which needs a GPU device.
 */
public fun TileGrid.toTileLayer(): TileLayer {
    val layer = TileLayer(tileSet, columns, rows)
    // The two kinds of layer klyph makes hold their cells as one array, already checked against the tile set.
    when (this) {
        is TileLayer -> layer.copyCellsFrom(cells, tileCount)
        is TileMapLayer -> layer.copyCellsFrom(cells, cells.count { it != 0 })
        else -> for (row in 0 until rows) {
            for (column in 0 until columns) layer[column, row] = get(column, row)
        }
    }
    return layer
}
