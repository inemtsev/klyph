package klyph.tiles

/**
 * A cell of a grid, by its [column] and [row], as [TileGrid.get] counts them: column 0 is the left
 * edge and row 0 the top. Make one with `Cell(column = 3, row = 1)`. A cell is only a position;
 * it may lie outside a grid, and the functions that take one say what they do then.
 *
 * Cells are what [DistanceField.compute] starts from and what [TileGrid.shortestPath] returns.
 * Two cells are equal when their columns and rows are. [toString] gives `Cell(3, 1)`.
 *
 * @sample samples.tiles.Paths.cell
 */
public value class Cell internal constructor(internal val bits: Long) {
    /** Column of the cell, counted from 0 at the grid's left edge. */
    public val column: Int get() = (bits shr Int.SIZE_BITS).toInt()

    /** Row of the cell, counted from 0 at the grid's top edge. */
    public val row: Int get() = bits.toInt()

    /** Returns `Cell(3, 1)`, the column and then the row. The format may change. */
    override fun toString(): String = "Cell($column, $row)"
}

/**
 * Returns the cell at [column] and [row]. Any two `Int`s make a cell, negative ones included, so a
 * cell can name a place outside a grid. Pass both by name, as in `Cell(column = 3, row = 1)`.
 *
 * @sample samples.tiles.Paths.cell
 */
public fun Cell(column: Int, row: Int): Cell = Cell((column.toLong() shl Int.SIZE_BITS) or (row.toLong() and 0xFFFFFFFFL))
