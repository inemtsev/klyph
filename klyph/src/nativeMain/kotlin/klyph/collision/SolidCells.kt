package klyph.collision

/**
 * A set of grid cells, each holding the numbers of the solids that touch it, found through a hash
 * table keyed by the cell's coordinates. [SolidGrid] keeps two: one for the solids that stand
 * still, rebuilt only when they change, and one for the moving solids, rebuilt at every refresh.
 *
 * It is filled in three steps: [clear], one [add] per solid with the range of cells it covers,
 * then [finish], which sorts the entries into cells with a counting sort. Cells are numbered in
 * first-touched order. Not thread-safe. Allocates nothing once its arrays fit the largest level.
 */
internal class SolidCells {
    /** The number of cells that hold at least one solid. */
    var cellCount: Int = 0
        private set

    // cellKeys holds each cell's packed (x, y) coordinates, and cellStarts its first position in
    // cellSolids, cellStarts[cellCount] the end.
    private var cellKeys = LongArray(INITIAL_CELLS)
    private var cellCursors = IntArray(INITIAL_CELLS)
    private var cellStarts = IntArray(INITIAL_CELLS + 1)
    private var cellSolids = IntArray(INITIAL_CELLS)

    // Open-addressing hash table from cell key to cell number, at most half full; -1 marks a free slot.
    private var table = IntArray(INITIAL_CELLS * 2) { -1 }

    // One entry per (solid, cell) pair between clear and finish.
    private var entryCells = IntArray(INITIAL_CELLS)
    private var entrySolids = IntArray(INITIAL_CELLS)
    private var entryCount = 0

    /** Empties every cell, so [add] starts a new set. */
    fun clear() {
        if (cellCount > 0) table.fill(-1)
        cellCount = 0
        entryCount = 0
    }

    /** Adds [solid] to every cell from column [x0] to [x1] and row [y0] to [y1]. */
    fun add(solid: Int, x0: Int, y0: Int, x1: Int, y1: Int) {
        ensureEntryCapacity(entryCount + (x1 - x0 + 1) * (y1 - y0 + 1))
        for (cy in y0..y1) {
            for (cx in x0..x1) {
                val cell = cellOrAdd(cx, cy)
                cellCursors[cell]++
                entryCells[entryCount] = cell
                entrySolids[entryCount] = solid
                entryCount++
            }
        }
    }

    /** Sorts the entries added since [clear] into their cells: prefix sums, then scatter. */
    fun finish() {
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

    /** Returns the first position of cell [cell]'s solids in [solidAt]. */
    fun startOf(cell: Int): Int = cellStarts[cell]

    /** Returns the position after the last of cell [cell]'s solids in [solidAt]. */
    fun endOf(cell: Int): Int = cellStarts[cell + 1]

    /** Returns the solid at position [position] of the sorted cells. */
    fun solidAt(position: Int): Int = cellSolids[position]

    /** Returns the number of the cell at ([cx], [cy]), or -1 if no solid touches it. */
    fun cellOrNegative(cx: Int, cy: Int): Int {
        val key = keyOf(cx, cy)
        val mask = table.size - 1
        var i = hash(key) and mask
        while (true) {
            val cell = table[i]
            if (cell < 0) return -1
            if (cellKeys[cell] == key) return cell
            i = (i + 1) and mask
        }
    }

    /** Returns the number of the cell at ([cx], [cy]), adding the cell if it is new. */
    private fun cellOrAdd(cx: Int, cy: Int): Int {
        val key = keyOf(cx, cy)
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

    override fun toString(): String = "SolidCells(cells=$cellCount, entries=$entryCount)"

    private companion object {
        const val INITIAL_CELLS: Int = 64

        fun keyOf(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFF_FFFFL)

        fun hash(key: Long): Int {
            val mixed = key * -7046029254386353131L
            return (mixed ushr 32).toInt() xor mixed.toInt()
        }
    }
}
