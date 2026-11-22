package klyph.tiles

/**
 * The distance in steps from every cell of a [TileGrid] to the nearest of one or more goal cells,
 * over the cells a game calls passable. Many walkers heading for the same goal read their next
 * step from it, as the enemies of a tower defense game walk to the base: each asks
 * [nextStepOrNull] for the cell it stands in.
 *
 * Make one and keep it. [compute] fills it from a grid and its goals, and a game calls it again
 * whenever the grid changes, such as when a tower is placed. The field grows its arrays to the
 * largest grid it was computed for and then reuses them, so computing again for a grid of that
 * many cells or fewer allocates nothing. Before the first [compute] it has no cells.
 *
 * `field[column, row]` reads a cell's distance: 0 for a goal, the fewest steps to a goal for
 * a cell with a route, and [UNREACHABLE] for a cell with none. A step goes to a neighbor under
 * [neighborhood] and counts 1, diagonal or not.
 *
 * Has no sample because a [TileSet] needs a texture, which needs a GPU device.
 */
public class DistanceField {
    /** Number of cells across, those of the grid of the last [compute]; 0 before the first. */
    public var columns: Int = 0
        private set

    /** Number of cells down, those of the grid of the last [compute]; 0 before the first. */
    public var rows: Int = 0
        private set

    /** Which cells were a step apart in the last [compute]; [Neighborhood.FOUR] before the first. */
    public var neighborhood: Neighborhood = Neighborhood.FOUR
        private set

    /** Distance of each cell, row by row from the top; only the first `columns * rows` are the field's. */
    private var distances = IntArray(0)

    /** The cells the search has reached and not yet left, as indices into [distances]. */
    private var queue = IntArray(0)

    /** Whether each cell of the grid being computed may be entered, as [passableFor] sized it. */
    private var passable = BooleanArray(0)

    /** Columns of the grid [passable] was last filled for; it becomes [columns] once the goals pass their checks. */
    private var pendingColumns = 0

    /** Rows of the grid [passable] was last filled for; it becomes [rows] once the goals pass their checks. */
    private var pendingRows = 0

    /** Number of cells with a distance after the last [compute]. */
    private var reachableCount = 0

    /** End of the cells in [queue] during a search. */
    private var tail = 0

    /**
     * Computes the distance of every cell of [grid] to the nearest of [goals], replacing what the
     * field held. A cell's distance is the fewest steps from it to a goal through passable cells:
     * 0 for a goal, and [UNREACHABLE] for a cell with no such route, which every cell that is not
     * passable is.
     *
     * [isPassable] says which tiles a walker may enter. It defaults to every tile that is not
     * solid in the grid's [TileSet], `!tileSet.isSolid(tile)`, so [Tile.EMPTY] is passable, as
     * [solidRectangles] defaults to the solid ones. [neighborhood] says which cells are a step
     * apart: [Neighborhood.FOUR], the default, the four that share a side, or
     * [Neighborhood.EIGHT], which adds the four corners, each only when both cells beside the step
     * are passable, so no step cuts the corner of a wall.
     *
     * The search is breadth-first from every goal at once and visits each cell once. [isPassable]
     * is called once for each cell, row by row from the top. The function is inline, so the test
     * runs without boxing each [Tile], and the field allocates only for a grid with more cells than
     * any it held before. A goal listed twice counts once. A rejected call leaves the field as it was.
     *
     * @throws IllegalArgumentException if [goals] is empty, or a goal is outside [grid] or not
     *   passable.
     */
    public inline fun compute(
        grid: TileGrid,
        goals: List<Cell>,
        neighborhood: Neighborhood = Neighborhood.FOUR,
        isPassable: (Tile) -> Boolean = { !grid.tileSet.isSolid(it) },
    ) {
        readPassable(grid, isPassable)
        search(goals, neighborhood)
    }

    /**
     * Returns the distance in steps from the cell at [column] and [row] to the nearest goal, 0 for
     * a goal and [UNREACHABLE] for a cell with no route to one.
     *
     * @throws IndexOutOfBoundsException if [column] is not in `0 until columns` or [row] is not in
     *   `0 until rows`.
     */
    public operator fun get(column: Int, row: Int): Int {
        if (column !in 0 until columns || row !in 0 until rows) {
            throw IndexOutOfBoundsException("Cell ($column, $row) is outside the distance field of ${columns}x$rows cells.")
        }
        return distances[row * columns + column]
    }

    /**
     * Returns the array to fill with whether each cell of a grid of [columns] by [rows] may be
     * entered, row by row from the top, grown when it is too small. The fill is the first half of
     * [compute], inlined into the caller; [search] or [searchPath] is the second.
     */
    @PublishedApi
    internal fun passableFor(columns: Int, rows: Int): BooleanArray {
        val cells = columns * rows
        if (passable.size < cells) passable = BooleanArray(cells)
        pendingColumns = columns
        pendingRows = rows
        return passable
    }

    /**
     * Checks [goals] against the passable cells [passableFor] was filled with, then computes the
     * distance of every cell to the nearest of them.
     */
    @PublishedApi
    internal fun search(goals: List<Cell>, neighborhood: Neighborhood) {
        require(goals.isNotEmpty()) { "The list of goals is empty." }
        for (i in 0 until goals.size) checkCell(goals[i], "Goal")
        begin(neighborhood)
        for (i in 0 until goals.size) {
            val goal = goals[i]
            val index = goal.row * columns + goal.column
            if (distances[index] != 0) {
                distances[index] = 0
                queue[tail++] = index
            }
        }
        reachableCount = spread(stop = -1)
    }

    /**
     * Checks [start] and [end] against the passable cells [passableFor] was filled with, then
     * returns the cells of a shortest path from [start] to [end], or `null` when there is none.
     * The search runs from [end] and stops once it reaches [start].
     */
    @PublishedApi
    internal fun searchPath(start: Cell, end: Cell, neighborhood: Neighborhood): List<Cell>? {
        checkCell(start, "Start")
        checkCell(end, "End")
        begin(neighborhood)
        val goal = end.row * columns + end.column
        distances[goal] = 0
        queue[tail++] = goal
        val from = start.row * columns + start.column
        reachableCount = spread(stop = from)
        if (distances[from] == UNREACHABLE) return null
        // Every cell nearer to the end than the start has been left, so its neighbors have their distances, which is all a
        // step reads.
        val path = ArrayList<Cell>(distances[from] + 1)
        path.add(start)
        var step = nextStepOrNull(start.column, start.row)
        while (step != null) {
            path.add(step)
            step = nextStepOrNull(step.column, step.row)
        }
        return path
    }

    private fun checkCell(cell: Cell, name: String) {
        require(cell.column in 0 until pendingColumns && cell.row in 0 until pendingRows) {
            "$name cell (${cell.column}, ${cell.row}) is outside the grid of ${pendingColumns}x$pendingRows cells."
        }
        require(passable[cell.row * pendingColumns + cell.column]) { "$name cell (${cell.column}, ${cell.row}) is not passable." }
    }

    /** Takes on the pending grid size and [neighborhood], grows the arrays if they are too small and marks every cell unreachable. */
    private fun begin(neighborhood: Neighborhood) {
        val cells = pendingColumns * pendingRows
        if (distances.size < cells) {
            distances = IntArray(cells)
            queue = IntArray(cells)
        }
        columns = pendingColumns
        rows = pendingRows
        this.neighborhood = neighborhood
        distances.fill(UNREACHABLE, 0, cells)
        tail = 0
    }

    /**
     * Spreads the distances from the cells queued so far, breadth-first, until every reachable
     * cell has one or the cell [stop] is taken from the queue: each cell taken gives its unreached
     * passable neighbors its distance plus one and queues them. Returns how many cells were reached.
     */
    private fun spread(stop: Int): Int {
        val corners = neighborhood == Neighborhood.EIGHT
        var head = 0
        while (head < tail) {
            val cell = queue[head++]
            if (cell == stop) break
            val column = cell % columns
            val row = cell / columns
            val next = distances[cell] + 1
            val hasLeft = column > 0
            val hasRight = column < columns - 1
            if (hasLeft) visit(cell - 1, next)
            if (hasRight) visit(cell + 1, next)
            if (row > 0) {
                visit(cell - columns, next)
                if (corners) visitCorners(cell, -columns, hasLeft, hasRight, next)
            }
            if (row < rows - 1) {
                visit(cell + columns, next)
                if (corners) visitCorners(cell, columns, hasLeft, hasRight, next)
            }
        }
        return tail
    }

    /**
     * Visits the corner neighbors of [cell] in the row [down] cells away, left when [hasLeft] and
     * right when [hasRight], each only when both cells beside the step are passable.
     */
    private fun visitCorners(cell: Int, down: Int, hasLeft: Boolean, hasRight: Boolean, distance: Int) {
        val side = passable[cell + down]
        if (hasLeft && side && passable[cell - 1]) visit(cell + down - 1, distance)
        if (hasRight && side && passable[cell + 1]) visit(cell + down + 1, distance)
    }

    /** Gives [cell] [distance] and queues it, unless it is not passable or already has one. */
    private fun visit(cell: Int, distance: Int) {
        if (passable[cell] && distances[cell] == UNREACHABLE) {
            distances[cell] = distance
            queue[tail++] = cell
        }
    }

    /**
     * Returns `DistanceField(size=16x9, neighborhood=FOUR, reachable=120)`, `reachable` counting
     * the cells with a distance. The format may change.
     */
    override fun toString(): String = "DistanceField(size=${columns}x$rows, neighborhood=$neighborhood, reachable=$reachableCount)"

    /** Holds [UNREACHABLE]. */
    public companion object {
        /** The distance of a cell with no route to a goal, larger than any other distance. */
        public const val UNREACHABLE: Int = Int.MAX_VALUE
    }
}

/**
 * Fills the field's passable cells from [grid] through [isPassable], one call per cell row by
 * row; the first half of [DistanceField.compute] and [findPathOrNull], inline so that no [Tile] is
 * boxed.
 */
@PublishedApi
internal inline fun DistanceField.readPassable(grid: TileGrid, isPassable: (Tile) -> Boolean) {
    val columns = grid.columns
    val rows = grid.rows
    val passable = passableFor(columns, rows)
    var index = 0
    for (row in 0 until rows) {
        for (column in 0 until columns) passable[index++] = isPassable(grid[column, row])
    }
}

/**
 * Returns the cell a walker in the cell at [column] and [row] steps to next on its way to the
 * nearest goal, one step nearer to it, or `null` when the cell is a goal or has no route to one.
 * The step goes to a neighbor under [DistanceField.neighborhood]; when several neighbors are one
 * step nearer, the cells beside it come before the corners, each in reading order, from the top
 * row down and left to right. A walker that takes this step from every cell it reaches walks a
 * shortest path.
 *
 * Has no sample because a [TileSet] needs a texture, which needs a GPU device.
 *
 * @throws IndexOutOfBoundsException if [column] is not in `0 until columns` or [row] is not in
 *   `0 until rows`.
 */
public fun DistanceField.nextStepOrNull(column: Int, row: Int): Cell? {
    val distance = this[column, row]
    if (distance == 0 || distance == DistanceField.UNREACHABLE) return null
    val directions = if (neighborhood == Neighborhood.EIGHT) STEP_COLUMNS.size else SIDE_COUNT
    var step: Cell? = null
    var i = 0
    while (step == null && i < directions) {
        val toColumn = column + STEP_COLUMNS[i]
        val toRow = row + STEP_ROWS[i]
        // A corner is a step only when both cells beside it are passable. Each of them is next to this reachable cell, so
        // it is passable exactly when it has a distance.
        val isStep = distanceOrUnreachable(toColumn, toRow) == distance - 1 &&
            (i < SIDE_COUNT || isReachable(toColumn, row) && isReachable(column, toRow))
        if (isStep) step = Cell(toColumn, toRow)
        i++
    }
    return step
}

/** Number of [STEP_COLUMNS] that go to a cell sharing a side; the rest go to a corner. */
private const val SIDE_COUNT = 4

/** Column offsets of the neighbors [nextStepOrNull] tries, in its order: the sides, then the corners, each in reading order. */
private val STEP_COLUMNS = intArrayOf(0, -1, 1, 0, -1, 1, -1, 1)

/** Row offsets of the same neighbors as [STEP_COLUMNS]. */
private val STEP_ROWS = intArrayOf(-1, 0, 0, 1, -1, -1, 1, 1)

private fun DistanceField.distanceOrUnreachable(column: Int, row: Int): Int =
    if (column in 0 until columns && row in 0 until rows) this[column, row] else DistanceField.UNREACHABLE

private fun DistanceField.isReachable(column: Int, row: Int): Boolean = distanceOrUnreachable(column, row) != DistanceField.UNREACHABLE
