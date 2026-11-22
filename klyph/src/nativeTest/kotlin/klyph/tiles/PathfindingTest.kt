package klyph.tiles

import klyph.render.SpriteSheet
import klyph.render.Texture
import klyph.render.fromGrid
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class PathfindingTest {
    /** Two 8 by 8 tiles: 0 solid, a wall, and 1 not, a floor that can be walked. */
    private val tileSet = TileSet(
        SpriteSheet.fromGrid(Texture(16, 8), columns = 2, rows = 1),
        tileWidth = 16f,
        tileHeight = 16f,
        solidIds = setOf(0),
    )

    /** Returns a layer whose cells are the characters of [rows]: '#' a wall, ',' a floor tile, '.' empty. */
    private fun layerOf(vararg rows: String): TileLayer {
        val layer = TileLayer(tileSet, rows[0].length, rows.size)
        for ((row, line) in rows.withIndex()) {
            for ((column, c) in line.withIndex()) {
                when (c) {
                    '#' -> layer[column, row] = Tile.of(0)
                    ',' -> layer[column, row] = Tile.of(1)
                }
            }
        }
        return layer
    }

    /** Returns every distance of [field], row by row, as the rows of a picture: `-` for unreachable. */
    private fun picture(field: DistanceField): List<String> = List(field.rows) { row ->
        (0 until field.columns).joinToString(" ") { column ->
            val distance = field[column, row]
            if (distance == DistanceField.UNREACHABLE) "-" else distance.toString()
        }
    }

    private fun cell(column: Int, row: Int) = Cell(column = column, row = row)

    @Test
    fun cellKeepsItsColumnAndRow() {
        val cell = Cell(column = -3, row = 70_000)
        assertEquals(-3, cell.column)
        assertEquals(70_000, cell.row)
        assertEquals(Cell(column = -3, row = 70_000), cell)
        assertTrue(Cell(column = 1, row = 2) != Cell(column = 2, row = 1))
        assertEquals("Cell(-3, 70000)", cell.toString())
    }

    @Test
    fun fourNeighborsCountStraightSteps() {
        val layer = layerOf(
            ".,..",
            ".##.",
            "....",
        )
        val field = DistanceField()
        field.compute(layer, goals = listOf(cell(0, 0)))
        // The floor tile at (1, 0) is not solid, so the default lets walkers through it.
        assertEquals(listOf("0 1 2 3", "1 - - 4", "2 3 4 5"), picture(field))
        assertEquals("DistanceField(size=4x3, reachable=10)", field.toString())
    }

    @Test
    fun eightNeighborsStepDiagonallyWithoutCuttingCorners() {
        val layer = layerOf(
            "....",
            ".#..",
            "....",
        )
        val field = DistanceField()
        field.compute(layer, goals = listOf(cell(0, 0)), neighbors = Neighbors.EIGHT)
        // (2, 1) is reached in 3 steps, not 2: the step from (1, 0) would cut the wall's corner.
        assertEquals(listOf("0 1 2 3", "1 - 3 3", "2 3 4 4"), picture(field))
    }

    @Test
    fun aDiagonalStepBetweenTwoWallsIsNotTaken() {
        val layer = layerOf(
            ".#",
            "#.",
        )
        assertNull(layer.shortestPathOrNull(start = cell(0, 0), end = cell(1, 1), neighbors = Neighbors.EIGHT))
        val field = DistanceField()
        field.compute(layer, goals = listOf(cell(0, 0)), neighbors = Neighbors.EIGHT)
        assertEquals(DistanceField.UNREACHABLE, field[1, 1])
    }

    @Test
    fun theNearestOfSeveralGoalsCounts() {
        val layer = layerOf(
            ".....",
            ".....",
        )
        val field = DistanceField()
        field.compute(layer, goals = listOf(cell(0, 0), cell(4, 1), cell(0, 0)))
        assertEquals(listOf("0 1 2 2 1", "1 2 2 1 0"), picture(field))
    }

    @Test
    fun aGoalOnAWallIsLeftOut() {
        val layer = layerOf(
            "..#",
            "...",
        )
        val field = DistanceField()
        field.compute(layer, goals = listOf(cell(2, 0)))
        assertEquals(listOf("- - -", "- - -"), picture(field))
        assertEquals("DistanceField(size=3x2, reachable=0)", field.toString())
        // Beside a goal that can be walked, the one on the wall changes nothing.
        field.compute(layer, goals = listOf(cell(2, 0), cell(0, 0)))
        assertEquals(listOf("0 1 -", "1 2 3"), picture(field))
        field.compute(layer, goals = emptyList())
        assertEquals(listOf("- - -", "- - -"), picture(field))
    }

    @Test
    fun cellsWalledOffFromEveryGoalAreUnreachable() {
        val layer = layerOf(
            "..#..",
            "..#..",
        )
        val field = DistanceField()
        field.compute(layer, goals = listOf(cell(0, 0)))
        assertEquals(listOf("0 1 - - -", "1 2 - - -"), picture(field))
        assertNull(layer.shortestPathOrNull(start = cell(0, 0), end = cell(4, 1)))
        assertEquals(
            "No path leads from Cell(0, 0) to Cell(4, 1).",
            assertFailsWith<NoSuchElementException> { layer.shortestPath(start = cell(0, 0), end = cell(4, 1)) }.message,
        )
    }

    @Test
    fun thePassabilityTestReplacesTheDefault() {
        val layer = layerOf(
            ".,.",
            "...",
        )
        val field = DistanceField()
        // Only empty cells may be walked, so the floor tile at (1, 0) is in the way and the wall is not.
        field.compute(layer, goals = listOf(cell(0, 0))) { it.isEmpty }
        assertEquals(listOf("0 - 4", "1 2 3"), picture(field))
    }

    @Test
    fun oneFieldAsksEachPassabilityTestAnew() {
        val layer = layerOf(
            ".,.",
            ".#.",
            "...",
        )
        val field = DistanceField()
        val goals = listOf(cell(0, 0))
        // The default walks the floor tile at (1, 0).
        field.compute(layer, goals = goals)
        assertEquals(listOf("0 1 2", "1 - 3", "2 3 4"), picture(field))
        // Only empty cells may be walked, so the same field now goes round the floor tile.
        field.compute(layer, goals = goals) { it.isEmpty }
        assertEquals(listOf("0 - 6", "1 - 5", "2 3 4"), picture(field))
        // Back to the default, the floor tile can be walked again.
        field.compute(layer, goals = goals)
        assertEquals(listOf("0 1 2", "1 - 3", "2 3 4"), picture(field))
    }

    @Test
    fun oneFieldIsReusedAcrossGridsAndChanges() {
        val field = DistanceField()
        assertEquals("DistanceField(size=0x0, reachable=0)", field.toString())
        assertFailsWith<IndexOutOfBoundsException> { field[0, 0] }

        val layer = layerOf(
            "....",
            "....",
            "....",
        )
        val goals = listOf(cell(3, 2))
        field.compute(layer, goals = goals)
        assertEquals(listOf("5 4 3 2", "4 3 2 1", "3 2 1 0"), picture(field))

        // A tower placed in the way: the walkers go round it.
        layer[2, 2] = Tile.of(0)
        layer[2, 1] = Tile.of(0)
        field.compute(layer, goals = goals)
        assertEquals(listOf("5 4 3 2", "6 5 - 1", "7 6 - 0"), picture(field))

        // A smaller grid in the same field: its size is the new grid's and nothing of the old one shows.
        val small = layerOf(
            "..",
            "#.",
        )
        field.compute(small, goals = listOf(cell(0, 0)))
        assertEquals(listOf("0 1", "- 2"), picture(field))
        assertEquals(
            "Cell (2, 0) is outside the distance field of 2x2 cells.",
            assertFailsWith<IndexOutOfBoundsException> { field[2, 0] }.message,
        )
    }

    @Test
    fun aGoalOutsideTheGridIsRejectedAndTheFieldKept() {
        val layer = layerOf(
            "...",
            "...",
        )
        val field = DistanceField()
        field.compute(layer, goals = listOf(cell(0, 0)))
        val message = assertFailsWith<IllegalArgumentException> {
            field.compute(layer, goals = listOf(cell(2, 1), cell(3, 0)))
        }.message
        assertEquals("Goal cell Cell(3, 0) is outside the grid of 3x2 cells.", message)
        assertEquals(listOf("0 1 2", "1 2 3"), picture(field))
        // One past each edge: the right and bottom ones, then the left and top ones.
        assertFailsWith<IllegalArgumentException> { field.compute(layer, goals = listOf(cell(0, 2))) }
        assertFailsWith<IllegalArgumentException> { field.compute(layer, goals = listOf(cell(-1, 0))) }
        assertFailsWith<IllegalArgumentException> { field.compute(layer, goals = listOf(cell(0, -1))) }
        assertEquals(listOf("0 1 2", "1 2 3"), picture(field))
    }

    @Test
    fun aPathRunsFromStartToEndThroughWalkableCells() {
        val layer = layerOf(
            "...#.",
            ".#.#.",
            ".#...",
        )
        val path = layer.shortestPath(start = cell(0, 2), end = cell(4, 0))
        assertEquals(
            listOf(
                cell(0, 2), cell(0, 1), cell(0, 0), cell(1, 0), cell(2, 0), cell(2, 1),
                cell(2, 2), cell(3, 2), cell(4, 2), cell(4, 1), cell(4, 0),
            ),
            path,
        )
        // Every diagonal step on the way would cut a corner of a wall, so eight neighbors take the same path.
        assertEquals(path, layer.shortestPath(start = cell(0, 2), end = cell(4, 0), neighbors = Neighbors.EIGHT))
    }

    @Test
    fun ofPathsAsShortTheOneThatGoesStraightFirstIsTaken() {
        val layer = layerOf(
            "...",
            "...",
        )
        // Both (1, 0) and (1, 1) lead to (2, 1) in two steps; the straight step comes first.
        assertEquals(
            listOf(cell(0, 0), cell(1, 0), cell(2, 1)),
            layer.shortestPath(start = cell(0, 0), end = cell(2, 1), neighbors = Neighbors.EIGHT),
        )
    }

    @Test
    fun aPathOfOneCellOrOfNone() {
        val layer = layerOf(
            "..#",
            "...",
        )
        assertEquals(listOf(cell(1, 1)), layer.shortestPath(start = cell(1, 1), end = cell(1, 1)))
        // A wall is not walkable, at either end.
        assertNull(layer.shortestPathOrNull(start = cell(2, 0), end = cell(2, 0)))
        assertNull(layer.shortestPathOrNull(start = cell(2, 0), end = cell(0, 0)))
        assertNull(layer.shortestPathOrNull(start = cell(0, 0), end = cell(2, 0)))
    }

    @Test
    fun aStartOrEndOutsideTheGridIsRejected() {
        val layer = layerOf(
            "...",
            "...",
        )
        assertEquals(
            "End cell Cell(0, 2) is outside the grid of 3x2 cells.",
            assertFailsWith<IllegalArgumentException> { layer.shortestPathOrNull(start = cell(0, 0), end = cell(0, 2)) }.message,
        )
        assertEquals(
            "Start cell Cell(-1, 0) is outside the grid of 3x2 cells.",
            assertFailsWith<IllegalArgumentException> { layer.shortestPath(start = cell(-1, 0), end = cell(0, 0)) }.message,
        )
    }

    @Test
    fun recomputingAGrownFieldAllocatesNothing() {
        val layer = windingLayer(size = 256)
        // A smaller grid between the large ones: the field keeps its grown arrays for it.
        val small = windingLayer(size = 64)
        val field = DistanceField()
        val goals = listOf(cell(0, 0), cell(255, 255))
        val smallGoals = listOf(cell(0, 0), cell(63, 63))
        val walkable: (Tile) -> Boolean = { it.isEmpty }
        val objects = measure {
            repeat(10) {
                field.compute(small, goals = smallGoals)
                field.compute(layer, goals = goals)
                field.compute(layer, goals = goals, neighbors = Neighbors.EIGHT)
                field.compute(layer, goals = goals, isPassable = walkable)
            }
        }
        assertTrue(field[128, 128] > 0, "The goals reached ${field[128, 128]} at the middle of the grid.")
        assertTrue(objects < 20, "40 recomputes allocated $objects objects")
    }

    /** Returns a [size] by [size] layer with walls on every fourth column and a gap that moves, so a search winds through it. */
    private fun windingLayer(size: Int): TileLayer {
        val layer = TileLayer(tileSet, columns = size, rows = size)
        for (column in 1 until size step 4) {
            for (row in 0 until size) if (row != column * 7 % size) layer[column, row] = Tile.of(0)
        }
        return layer
    }

    /**
     * Returns the objects allocated while [block] runs, after running it twice so everything it grows
     * has grown, counted from the garbage collector's statistics as `AllocationTest` in `klyph-physics`
     * counts them: collect, run the block, collect again, and every object the block made is either freed
     * by the second collection or kept by it. What an empty block counts is taken off.
     */
    private fun measure(block: () -> Unit): Long {
        repeat(2) { block() }
        return count(block) - count {}
    }

    @OptIn(NativeRuntimeApi::class, ExperimentalStdlibApi::class)
    private fun count(block: () -> Unit): Long {
        val interval = GC.regularGCInterval
        val target = GC.targetHeapBytes
        val autotune = GC.autotune
        GC.autotune = false
        GC.regularGCInterval = 24.hours
        GC.targetHeapBytes = 1L shl 34
        try {
            GC.collect()
            val before = checkNotNull(GC.lastGCInfo)
            val keptBefore = before.sweepStatistics.values.sumOf { it.keptCount }
            block()
            GC.collect()
            val after = checkNotNull(GC.lastGCInfo)
            assertTrue(after.epoch == before.epoch + 1, "A collection ran while the block did, so it allocated.")
            val swept = after.sweepStatistics.values.sumOf { it.sweptCount }
            val keptAfter = after.sweepStatistics.values.sumOf { it.keptCount }
            return swept + (keptAfter - keptBefore)
        } finally {
            GC.regularGCInterval = interval
            GC.targetHeapBytes = target
            GC.autotune = autotune
        }
    }
}
