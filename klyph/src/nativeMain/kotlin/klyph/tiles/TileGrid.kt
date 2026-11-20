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
    val width = tileSet.tileWidth
    val height = tileSet.tileHeight
    val result = ArrayList<Rect>()
    // The rectangles still growing downwards, as [first column, end column, first row] each.
    var open = ArrayList<IntArray>()
    for (row in 0..rows) {
        val next = ArrayList<IntArray>()
        var column = 0
        while (row < rows && column < columns) {
            if (!isSolid(get(column, row))) {
                column++
                continue
            }
            val start = column
            while (column < columns && isSolid(get(column, row))) column++
            val above = open.firstOrNull { it[0] == start && it[1] == column }
            next.add(above ?: intArrayOf(start, column, row))
        }
        for (run in open) {
            if (next.none { it === run }) {
                result.add(Rect(run[0] * width, run[2] * height, (run[1] - run[0]) * width, (row - run[2]) * height))
            }
        }
        open = next
    }
    result.sortWith(compareBy<Rect> { it.y }.thenBy { it.x })
    return result
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
    for (row in 0 until rows) {
        for (column in 0 until columns) layer[column, row] = get(column, row)
    }
    return layer
}
