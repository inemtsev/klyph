package klyph.tiles

import klyph.render.SpriteSheet
import klyph.render.Texture
import klyph.render.fromGrid
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathsTest {
    /** Two 8 by 8 tiles: 0 solid, 1 water, which is not solid. */
    private val tileSet = TileSet(
        SpriteSheet.fromGrid(Texture(16, 8), columns = 2, rows = 1),
        tileWidth = 16f,
        tileHeight = 16f,
        solidIds = setOf(0),
    )

    /** Returns a layer whose cells are the characters of [rows]: '#' tile 0, '~' tile 1, '.' empty. */
    private fun layerOf(vararg rows: String): TileLayer {
        val layer = TileLayer(tileSet, rows[0].length, rows.size)
        for ((row, line) in rows.withIndex()) {
            for ((column, c) in line.withIndex()) {
                val id = "#~".indexOf(c)
                if (id >= 0) layer[column, row] = Tile.of(id)
            }
        }
        return layer
    }

    /**
     * Checks that [path] goes from [start] to [end] through cells [isPassable] lets in, each step to a
     * neighbor under [neighborhood] without cutting a corner, and that it is no longer than the
     * distance a [DistanceField] from [end] gives [start].
     */
    private fun assertShortestPath(
        grid: TileGrid,
        path: List<Cell>,
        start: Cell,
        end: Cell,
        neighborhood: Neighborhood = Neighborhood.FOUR,
        isPassable: (Tile) -> Boolean = { !grid.tileSet.isSolid(it) },
    ) {
        assertEquals(start, path.first())
        assertEquals(end, path.last())
        for (cell in path) assertTrue(isPassable(grid[cell.column, cell.row]), "$cell on $path is not passable")
        for ((from, to) in path.zipWithNext()) {
            val across = abs(to.column - from.column)
            val down = abs(to.row - from.row)
            assertTrue(across <= 1 && down <= 1 && across + down > 0, "$from to $to on $path is not a step")
            if (across + down == 2) {
                assertEquals(Neighborhood.EIGHT, neighborhood, "$from to $to on $path takes a corner")
                assertTrue(isPassable(grid[to.column, from.row]) && isPassable(grid[from.column, to.row]), "$from to $to cuts a corner")
            }
        }
        val field = DistanceField()
        field.compute(grid, listOf(end), neighborhood, isPassable)
        assertEquals(field[start.column, start.row], path.size - 1, "$path is not a shortest path")
    }

    @Test
    fun findPathWalksACorridor() {
        assertEquals(
            listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0)),
            layerOf("....").findPath(Cell(0, 0), Cell(3, 0)),
        )
    }

    @Test
    fun findPathGoesAroundWalls() {
        val grid = layerOf("..#.", "..#.", "....")
        val path = grid.findPath(Cell(0, 0), Cell(3, 0))
        assertEquals(8, path.size)
        assertShortestPath(grid, path, Cell(0, 0), Cell(3, 0))
    }

    @Test
    fun findPathTakesCornersWithEightNeighbors() {
        val open = layerOf("....", "....", "....", "....")
        assertEquals(
            listOf(Cell(0, 0), Cell(1, 1), Cell(2, 2), Cell(3, 3)),
            open.findPath(Cell(0, 0), Cell(3, 3), Neighborhood.EIGHT),
        )
        assertEquals(7, open.findPath(Cell(0, 0), Cell(3, 3)).size)
    }

    @Test
    fun findPathDoesNotCutCorners() {
        // The only corner step from (0, 0) toward (1, 1) passes the wall at (1, 0).
        val grid = layerOf(".#", "..")
        val path = grid.findPath(Cell(0, 0), Cell(1, 1), Neighborhood.EIGHT)
        assertEquals(listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1)), path)
    }

    @Test
    fun findPathOfOneCell() {
        assertEquals(listOf(Cell(1, 0)), layerOf("...").findPath(Cell(1, 0), Cell(1, 0)))
        assertEquals(listOf(Cell(1, 0)), layerOf("...").findPathOrNull(Cell(1, 0), Cell(1, 0), Neighborhood.EIGHT))
    }

    @Test
    fun theDefaultPassesEveryTileThatIsNotSolid() {
        val grid = layerOf(".~.", "...")
        assertEquals(listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0)), grid.findPath(Cell(0, 0), Cell(2, 0)))
        assertNull(layerOf(".#.").findPathOrNull(Cell(0, 0), Cell(2, 0)))
    }

    @Test
    fun aPredicateChoosesThePassableTiles() {
        // Water stops a walker, and the solid tile does not.
        val grid = layerOf(".~.", ".#.")
        val isPassable: (Tile) -> Boolean = { it.id != 1 }
        val path = grid.findPath(Cell(0, 0), Cell(2, 0)) { it.id != 1 }
        assertEquals(listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(2, 0)), path)
        assertShortestPath(grid, path, Cell(0, 0), Cell(2, 0), isPassable = isPassable)
    }

    @Test
    fun noPathGivesNullOrThrows() {
        val grid = layerOf("..#.", "..#.")
        assertNull(grid.findPathOrNull(Cell(0, 0), Cell(3, 1)))
        assertNull(grid.findPathOrNull(Cell(0, 0), Cell(3, 1), Neighborhood.EIGHT))
        assertEquals(
            "No path joins cell (0, 0) to cell (3, 1).",
            assertFailsWith<NoSuchElementException> { grid.findPath(Cell(0, 0), Cell(3, 1)) }.message,
        )
    }

    @Test
    fun findPathRejectsCellsOutsideOrNotPassable() {
        val grid = layerOf("..#", "...")
        assertEquals(
            "Start cell (-1, 0) is outside the grid of 3x2 cells.",
            assertFailsWith<IllegalArgumentException> { grid.findPath(Cell(-1, 0), Cell(0, 0)) }.message,
        )
        assertEquals(
            "End cell (0, 2) is outside the grid of 3x2 cells.",
            assertFailsWith<IllegalArgumentException> { grid.findPathOrNull(Cell(0, 0), Cell(0, 2)) }.message,
        )
        assertEquals(
            "Start cell (2, 0) is not passable.",
            assertFailsWith<IllegalArgumentException> { grid.findPathOrNull(Cell(2, 0), Cell(0, 0)) }.message,
        )
        assertEquals(
            "End cell (2, 0) is not passable.",
            assertFailsWith<IllegalArgumentException> { grid.findPath(Cell(0, 0), Cell(2, 0)) }.message,
        )
    }

    @Test
    fun pathsOnRandomGridsAreShortest() {
        val random = Random(7)
        repeat(200) {
            val grid = TileLayer(tileSet, columns = 2 + random.nextInt(12), rows = 2 + random.nextInt(12))
            for (row in 0 until grid.rows) {
                for (column in 0 until grid.columns) if (random.nextInt(10) < 3) grid[column, row] = Tile.of(0)
            }
            val start = Cell(random.nextInt(grid.columns), random.nextInt(grid.rows))
            val end = Cell(random.nextInt(grid.columns), random.nextInt(grid.rows))
            grid[start.column, start.row] = Tile.EMPTY
            grid[end.column, end.row] = Tile.EMPTY
            for (neighborhood in Neighborhood.entries) {
                val expected = referenceDistance(grid, start, end, neighborhood)
                val path = grid.findPathOrNull(start, end, neighborhood)
                if (expected == null) {
                    assertNull(path)
                } else {
                    assertNotEquals(null, path)
                    assertEquals(expected + 1, path!!.size, "$grid from $start to $end with $neighborhood")
                    assertShortestPath(grid, path, start, end, neighborhood)
                }
            }
        }
    }

    /**
     * Returns the fewest steps from [start] to [end] over the cells that are not solid, found by relaxing every cell until
     * nothing changes, a search that shares no code with the one under test; `null` when there is no route.
     */
    private fun referenceDistance(grid: TileGrid, start: Cell, end: Cell, neighborhood: Neighborhood): Int? {
        fun open(column: Int, row: Int) = column in 0 until grid.columns && row in 0 until grid.rows &&
            !grid.tileSet.isSolid(grid[column, row])
        val steps = Array(grid.rows) { IntArray(grid.columns) { Int.MAX_VALUE } }
        steps[start.row][start.column] = 0
        var changed = true
        while (changed) {
            changed = false
            for (row in 0 until grid.rows) {
                for (column in 0 until grid.columns) {
                    if (!open(column, row)) continue
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val corner = dx != 0 && dy != 0
                            if (dx == 0 && dy == 0 || corner && neighborhood == Neighborhood.FOUR) continue
                            if (!open(column + dx, row + dy) || corner && !(open(column + dx, row) && open(column, row + dy))) continue
                            val from = steps[row + dy][column + dx]
                            if (from != Int.MAX_VALUE && from + 1 < steps[row][column]) {
                                steps[row][column] = from + 1
                                changed = true
                            }
                        }
                    }
                }
            }
        }
        return steps[end.row][end.column].takeIf { it != Int.MAX_VALUE }
    }

    @Test
    fun cellsAreEqualByColumnAndRow() {
        assertEquals(Cell(3, 4), Cell(3, 4))
        assertEquals(Cell(3, 4).hashCode(), Cell(3, 4).hashCode())
        assertNotEquals(Cell(3, 4), Cell(4, 3))
        assertEquals("Cell(3, 4)", Cell(3, 4).toString())
    }
}
