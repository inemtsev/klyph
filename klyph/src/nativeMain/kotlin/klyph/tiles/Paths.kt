package klyph.tiles

/**
 * A cell of a [TileGrid], at [column] from its left edge and [row] from its top, both counted
 * from 0, as `grid[column, row]` reads it. Two cells are equal when their column and row are.
 * A cell may lie outside any grid; the functions that take one say what they do then.
 *
 * [toString] gives `Cell(3, 4)`, the column and then the row.
 */
public class Cell(
    /** Column of the cell, from 0 at the grid's left edge. */
    public val column: Int,
    /** Row of the cell, from 0 at the grid's top edge. */
    public val row: Int,
) {
    override fun equals(other: Any?): Boolean = other is Cell && other.column == column && other.row == row

    override fun hashCode(): Int = 31 * column + row

    /** Returns `Cell(3, 4)`, the column and then the row. The format may change. */
    override fun toString(): String = "Cell($column, $row)"
}

/**
 * Which cells of a grid are a step apart for [findPath] and [DistanceField.compute]. Every step
 * counts 1, to a side or to a corner.
 */
public enum class Neighborhood {
    /** The four cells that share a side with a cell: above, below, left and right. */
    FOUR,

    /**
     * The eight cells that share a side or a corner with a cell. A step to a corner is allowed only
     * when both cells beside it, those that share a side with both ends, are passable, so no step
     * cuts the corner of a wall.
     */
    EIGHT,
}

/**
 * Returns a shortest path from [start] to [end] through the passable cells of this grid, as the
 * cells walked, [start] first and [end] last, or `null` when no path joins them. [start] equal to
 * [end] gives a list of that one cell.
 *
 * [isPassable] says which tiles a walker may enter, and defaults to every tile that is not solid
 * in the grid's [TileSet], `!tileSet.isSolid(tile)`, as [DistanceField.compute] does. Each step
 * goes to a neighbor under [neighborhood], [Neighborhood.FOUR] by default, and counts 1, so a
 * shortest path is one of the fewest cells; among several, the one returned is always the same
 * for the same grid.
 *
 * The search is breadth-first from [end] and stops once it reaches [start]. Each call allocates
 * a [DistanceField] and the list; for many walkers heading to the same cell, compute one field
 * and read [nextStepOrNull] instead. Pass [isPassable] as a lambda or a function reference: as
 * with [DistanceField.compute], a function stored in a variable boxes every tile.
 *
 * @throws IllegalArgumentException if [start] or [end] is outside the grid or not passable.
 *
 * @sample samples.tiles.Paths.findPath
 */
public inline fun TileGrid.findPathOrNull(
    start: Cell,
    end: Cell,
    neighborhood: Neighborhood = Neighborhood.FOUR,
    isPassable: (Tile) -> Boolean = { !tileSet.isSolid(it) },
): List<Cell>? {
    val field = DistanceField()
    field.readPassable(this, isPassable)
    return field.searchPath(start, end, neighborhood)
}

/**
 * Returns a shortest path from [start] to [end] through the passable cells of this grid, as the
 * cells walked, [start] first and [end] last. [start] equal to [end] gives a list of that one cell.
 * When no path may join them, use [findPathOrNull].
 *
 * [isPassable] says which tiles a walker may enter, and defaults to every tile that is not solid
 * in the grid's [TileSet], `!tileSet.isSolid(tile)`, as [DistanceField.compute] does. Each step
 * goes to a neighbor under [neighborhood], [Neighborhood.FOUR] by default, and counts 1, so a
 * shortest path is one of the fewest cells; among several, the one returned is always the same
 * for the same grid.
 *
 * @throws IllegalArgumentException if [start] or [end] is outside the grid or not passable.
 * @throws NoSuchElementException if no path joins [start] and [end].
 *
 * @sample samples.tiles.Paths.findPath
 */
public inline fun TileGrid.findPath(
    start: Cell,
    end: Cell,
    neighborhood: Neighborhood = Neighborhood.FOUR,
    isPassable: (Tile) -> Boolean = { !tileSet.isSolid(it) },
): List<Cell> = findPathOrNull(start, end, neighborhood, isPassable)
    ?: throw NoSuchElementException("No path joins cell (${start.column}, ${start.row}) to cell (${end.column}, ${end.row}).")
