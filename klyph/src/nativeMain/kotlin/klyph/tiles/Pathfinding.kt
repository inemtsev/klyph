package klyph.tiles

/**
 * Which cells next to a cell a walker may step to in one move: [FOUR] for the cells beside, above
 * and below, [EIGHT] for those and the four corners. Every move counts as one step, a diagonal one
 * too, and a diagonal move is only taken when both cells beside the corner can be walked, so a
 * walker never cuts a corner of a wall.
 *
 * @sample samples.tiles.Paths.shortestPath
 */
public enum class Neighbors(internal val count: Int) {
    /** The four cells that share a side with the cell. */
    FOUR(4),

    /** The four cells that share a side with the cell and the four that share a corner. */
    EIGHT(8),
}

/**
 * The number of steps from every cell of a grid to the nearest of one or more goal cells, the map
 * many walkers read to head for the same place: each one steps to a neighbor whose distance is one
 * less than its own. [compute] fills it for a [TileGrid], and can fill it again whenever the grid
 * changes, as a tower defense game does after every tower it places.
 *
 * A new field is empty, 0 by 0 cells. Its arrays grow to the grid the first time [compute] runs,
 * and recomputing it for a grid of that size or smaller allocates nothing. Read a cell's distance
 * with [get], as in `field[column, row]`; a cell no goal can be reached from reads [UNREACHABLE].
 *
 * [toString] gives `DistanceField(size=4x3, reachable=10)`.
 *
 * @sample samples.tiles.Paths.compute
 */
public class DistanceField {
    /** Number of cells across, the [TileGrid.columns] of the grid last computed; 0 before that. */
    public var columns: Int = 0
        private set

    /** Number of cells down, the [TileGrid.rows] of the grid last computed; 0 before that. */
    public var rows: Int = 0
        private set

    private var distances = IntArray(0)
    private var passable = BooleanArray(0)
    private val answers = TileAnswers()

    // The cells in the order the search reached them; [reached] of them are filled.
    private var queue = IntArray(0)
    private var reached = 0

    /**
     * Returns the number of steps from the cell at [column] and [row] to the nearest goal of the last
     * [compute], 0 for a goal itself, or [UNREACHABLE] when the cell cannot be walked or no goal can
     * be reached from it.
     *
     * @throws IndexOutOfBoundsException if [column] is not in `0 until columns` or [row] is not in
     *   `0 until rows`.
     *
     * @sample samples.tiles.Paths.compute
     */
    public operator fun get(column: Int, row: Int): Int {
        if (column !in 0 until columns || row !in 0 until rows) {
            throw IndexOutOfBoundsException("Cell ($column, $row) is outside the distance field of ${columns}x$rows cells.")
        }
        return distances[row * columns + column]
    }

    /**
     * Fills this field with the number of steps from every cell of [grid] to the nearest of [goals],
     * moving to the [neighbors] of each cell and only through cells whose tile [isPassable]. What the
     * field held before is replaced, and its size becomes the grid's.
     *
     * [isPassable] defaults to the tiles that are not [TileSet.isSolid], the opposite of what
     * [solidRectangles] makes walls of; pass a test of your own to keep walkers off water, or to let
     * them through doors. It is asked once for each different tile of the grid, flips included, so
     * it has to answer by the tile alone. A goal on a cell that cannot be walked is
     * left out, and with no goal left every cell reads [UNREACHABLE]. Once the field's arrays have
     * grown to the grid, this allocates nothing: keep [goals] in a list of your own to recompute
     * without making one each time. Pass the arguments after [grid] by name, as in
     * `field.compute(grid, goals = listOf(base), neighbors = Neighbors.EIGHT)`.
     *
     * @throws IllegalArgumentException if a cell of [goals] is outside [grid], with the field left
     *   as it was.
     *
     * @sample samples.tiles.Paths.compute
     */
    public fun compute(
        grid: TileGrid,
        goals: List<Cell>,
        neighbors: Neighbors = Neighbors.FOUR,
        isPassable: (Tile) -> Boolean = grid.tileSet.isNotSolid,
    ) {
        for (index in goals.indices) {
            val goal = goals[index]
            require(goal.column in 0 until grid.columns && goal.row in 0 until grid.rows) {
                "Goal cell $goal is outside the grid of ${grid.columns}x${grid.rows} cells."
            }
        }
        fill(grid, goals, neighbors, isPassable, stopAt = -1)
    }

    /**
     * Fills the field as [compute] does, from goals already checked to be inside [grid], and stops as
     * soon as the cell at index [stopAt] has its distance, when [stopAt] is not -1. Every cell nearer
     * a goal than that one has its distance by then.
     */
    internal fun fill(grid: TileGrid, goals: List<Cell>, neighbors: Neighbors, isPassable: (Tile) -> Boolean, stopAt: Int) {
        columns = grid.columns
        rows = grid.rows
        val size = columns * rows
        if (distances.size < size) {
            distances = IntArray(size)
            passable = BooleanArray(size)
            queue = IntArray(size)
        }
        answers.forget()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val index = row * columns + column
                passable[index] = answers.isPassable(grid[column, row], isPassable)
                distances[index] = UNREACHABLE
            }
        }
        reached = 0
        for (index in goals.indices) {
            val goal = goals[index]
            val cell = goal.row * columns + goal.column
            if (passable[cell] && distances[cell] == UNREACHABLE) {
                distances[cell] = 0
                queue[reached++] = cell
            }
        }
        if (stopAt >= 0 && distances[stopAt] != UNREACHABLE) return
        spread(neighbors, stopAt)
    }

    /** Spreads the distances of the cells in the queue to their neighbors, breadth first. */
    private fun spread(neighbors: Neighbors, stopAt: Int) {
        var head = 0
        while (head < reached) {
            val cell = queue[head++]
            val next = distances[cell] + 1
            for (move in 0 until neighbors.count) {
                val neighbor = neighborOf(cell, move)
                if (neighbor < 0 || distances[neighbor] != UNREACHABLE) continue
                distances[neighbor] = next
                queue[reached++] = neighbor
                if (neighbor == stopAt) return
            }
        }
    }

    /**
     * Returns the index of the cell [move] leads to from the cell at index [cell], or -1 when it is
     * outside the field, cannot be walked, or is a corner that one of the cells beside it blocks.
     * Moves 0 to 3 go right, left, down and up, 4 to 7 to the corners.
     */
    private fun neighborOf(cell: Int, move: Int): Int {
        val column = cell % columns + MOVE_X[move]
        val row = cell / columns + MOVE_Y[move]
        if (column !in 0 until columns || row !in 0 until rows) return -1
        val neighbor = row * columns + column
        // A corner is open only when the cells beside it, across and down from this one, are too.
        val isCornerOpen = move < 4 || passable[cell + MOVE_X[move]] && passable[cell + MOVE_Y[move] * columns]
        return if (passable[neighbor] && isCornerOpen) neighbor else -1
    }

    /**
     * Returns the cells from [start] to the goal nearest it, stepping each time to the first of the
     * [neighbors] that is one step nearer, or `null` when [start] reads [UNREACHABLE].
     */
    internal fun pathFrom(start: Cell, neighbors: Neighbors): List<Cell>? {
        var cell = start.row * columns + start.column
        var distance = distances[cell]
        if (distance == UNREACHABLE) return null
        val path = ArrayList<Cell>(distance + 1)
        path.add(start)
        while (distance > 0) {
            for (move in 0 until neighbors.count) {
                val neighbor = neighborOf(cell, move)
                if (neighbor >= 0 && distances[neighbor] == distance - 1) {
                    cell = neighbor
                    break
                }
            }
            distance--
            path.add(Cell(column = cell % columns, row = cell / columns))
        }
        return path
    }

    /** Returns `DistanceField(size=4x3, reachable=10)`, with the number of cells the last [compute] reached. The format may change. */
    override fun toString(): String = "DistanceField(size=${columns}x$rows, reachable=$reached)"

    /** Holds [UNREACHABLE]. */
    public companion object {
        /** What [get] returns for a cell that cannot be walked or that no goal can be reached from. */
        public const val UNREACHABLE: Int = -1
    }
}

/**
 * The answers of one passability test for each different tile of a grid, by the tile's bits, so a
 * [DistanceField] asks the test once per tile instead of once per cell. Calling a `(Tile) -> Boolean`
 * boxes the tile, so the box of each tile is made the first time it is seen and kept, and a field
 * that has seen every tile of its grid asks again without allocating. An open-addressing table whose
 * capacity is a power of two, at most half full.
 */
private class TileAnswers {
    private var keys = IntArray(INITIAL_CAPACITY)
    private var boxes = arrayOfNulls<Any>(INITIAL_CAPACITY)

    // ASKED_NOT for a tile the test has not been asked about since the last [forget].
    private var answers = ByteArray(INITIAL_CAPACITY)
    private var size = 0

    /** Forgets every answer, keeping the boxes, before a test that may answer differently. */
    fun forget() {
        answers.fill(ASKED_NOT)
    }

    /** Returns what [test] says about [tile], asking it only the first time since [forget]. */
    fun isPassable(tile: Tile, test: (Tile) -> Boolean): Boolean {
        var slot = slotOf(tile.bits)
        if (boxes[slot] == null) {
            if ((size + 1) * 2 > keys.size) {
                grow()
                slot = slotOf(tile.bits)
            }
            keys[slot] = tile.bits
            boxes[slot] = tile
            size++
        }
        if (answers[slot] == ASKED_NOT) {
            // Hands the test the kept box, which its own unboxing reads as the tile.
            @Suppress("UNCHECKED_CAST")
            val passes = (test as (Any?) -> Boolean)(boxes[slot])
            answers[slot] = if (passes) PASSABLE else BLOCKED
        }
        return answers[slot] == PASSABLE
    }

    /** Returns the slot that holds [bits], or the empty one where they go. */
    private fun slotOf(bits: Int): Int {
        val mask = keys.size - 1
        var slot = (bits * HASH_MULTIPLIER ushr HASH_SHIFT) and mask
        while (boxes[slot] != null && keys[slot] != bits) slot = (slot + 1) and mask
        return slot
    }

    /** Doubles the table, keeping every tile with its box and its answer. */
    private fun grow() {
        val oldKeys = keys
        val oldBoxes = boxes
        val oldAnswers = answers
        keys = IntArray(oldKeys.size * 2)
        boxes = arrayOfNulls(oldKeys.size * 2)
        answers = ByteArray(oldKeys.size * 2)
        for (old in oldKeys.indices) {
            if (oldBoxes[old] == null) continue
            val slot = slotOf(oldKeys[old])
            keys[slot] = oldKeys[old]
            boxes[slot] = oldBoxes[old]
            answers[slot] = oldAnswers[old]
        }
    }

    override fun toString(): String = "TileAnswers(tiles=$size)"

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val ASKED_NOT: Byte = 0
        const val PASSABLE: Byte = 1
        const val BLOCKED: Byte = 2

        /** Spreads neighboring bits over the table: the golden ratio's multiplier, as in Fibonacci hashing. */
        const val HASH_MULTIPLIER = -0x61c88647
        const val HASH_SHIFT = 16
    }
}

/** Column steps of the moves of [DistanceField.neighborOf]: right, left, down, up, then the four corners. */
private val MOVE_X = intArrayOf(1, -1, 0, 0, 1, -1, 1, -1)

/** Row steps of the moves of [DistanceField.neighborOf], in the order of [MOVE_X]. */
private val MOVE_Y = intArrayOf(0, 0, 1, -1, 1, 1, -1, -1)

/**
 * Returns one of the shortest paths from [start] to [end] over this grid, moving to the [neighbors]
 * of each cell and only through cells whose tile [isPassable], as a list of cells that begins with
 * [start] and ends with [end], or `null` when no path leads there. A path from a cell to itself is
 * that one cell, if it can be walked.
 *
 * Both ends have to be walkable cells, or there is no path. [isPassable] defaults to the tiles that
 * are not [TileSet.isSolid], as in [DistanceField.compute]. Where several paths are as short, the
 * one that goes straight before it goes diagonally is taken. Each call searches anew; for many
 * walkers heading to one place, compute a [DistanceField] once and let each read its next step from
 * it. Pass the arguments by name, as in `grid.shortestPathOrNull(start = here, end = there)`.
 *
 * @throws IllegalArgumentException if [start] or [end] is outside the grid.
 *
 * @sample samples.tiles.Paths.shortestPathOrNull
 */
public fun TileGrid.shortestPathOrNull(
    start: Cell,
    end: Cell,
    neighbors: Neighbors = Neighbors.FOUR,
    isPassable: (Tile) -> Boolean = tileSet.isNotSolid,
): List<Cell>? {
    require(start.column in 0 until columns && start.row in 0 until rows) {
        "Start cell $start is outside the grid of ${columns}x$rows cells."
    }
    require(end.column in 0 until columns && end.row in 0 until rows) {
        "End cell $end is outside the grid of ${columns}x$rows cells."
    }
    // The search runs from the end, so the walk back from the start lists the cells in order.
    val field = DistanceField()
    field.fill(this, listOf(end), neighbors, isPassable, stopAt = start.row * columns + start.column)
    return field.pathFrom(start, neighbors)
}

/**
 * Returns one of the shortest paths from [start] to [end] over this grid, as [shortestPathOrNull]
 * does, but throws when no path leads there. Use it where the level is made so that a path always
 * exists. Pass the arguments by name, as in `grid.shortestPath(start = here, end = there)`.
 *
 * @throws IllegalArgumentException if [start] or [end] is outside the grid.
 * @throws NoSuchElementException if no path leads from [start] to [end].
 *
 * @sample samples.tiles.Paths.shortestPath
 */
public fun TileGrid.shortestPath(
    start: Cell,
    end: Cell,
    neighbors: Neighbors = Neighbors.FOUR,
    isPassable: (Tile) -> Boolean = tileSet.isNotSolid,
): List<Cell> =
    shortestPathOrNull(start = start, end = end, neighbors = neighbors, isPassable = isPassable)
        ?: throw NoSuchElementException("No path leads from $start to $end.")
