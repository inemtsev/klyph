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

class DistanceFieldTest {
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

    /** Returns the field's distances row by row, a digit for each cell and '-' for one with no route. */
    private fun render(field: DistanceField): List<String> = List(field.rows) { row ->
        buildString {
            for (column in 0 until field.columns) {
                val distance = field[column, row]
                append(if (distance == DistanceField.UNREACHABLE) '-' else '0' + distance)
            }
        }
    }

    @Test
    fun oneGoalGivesTheStepsAroundWalls() {
        val field = DistanceField()
        field.compute(layerOf("..#.", "..#.", "...."), listOf(Cell(0, 0)))
        assertEquals(listOf("01-7", "12-6", "2345"), render(field))
        assertEquals("DistanceField(size=4x3, neighborhood=FOUR, reachable=10)", field.toString())
    }

    @Test
    fun severalGoalsGiveTheDistanceToTheNearest() {
        val field = DistanceField()
        field.compute(layerOf("......"), listOf(Cell(0, 0), Cell(5, 0), Cell(5, 0)))
        assertEquals(listOf("012210"), render(field))
    }

    @Test
    fun aCellWithNoRouteIsUnreachable() {
        val field = DistanceField()
        field.compute(layerOf("..#.", "..#."), listOf(Cell(0, 0)))
        assertEquals(DistanceField.UNREACHABLE, field[3, 1])
        assertEquals(DistanceField.UNREACHABLE, field[2, 0])
        assertNull(field.nextStepOrNull(3, 1))
        assertNull(field.nextStepOrNull(2, 0))
    }

    @Test
    fun eightNeighborsStepToCornersWithoutCuttingThem() {
        val grid = layerOf("....", ".#..", "....")
        val field = DistanceField()
        field.compute(grid, listOf(Cell(0, 0)), Neighborhood.EIGHT)
        // (3, 1) takes the corner from (2, 0); (2, 1) and (1, 2) cannot take the corners past the wall.
        assertEquals(listOf("0123", "1-33", "2344"), render(field))
        assertEquals(Neighborhood.EIGHT, field.neighborhood)
        field.compute(grid, listOf(Cell(0, 0)))
        assertEquals(listOf("0123", "1-34", "2345"), render(field))
        assertEquals(Neighborhood.FOUR, field.neighborhood)
    }

    @Test
    fun nextStepGoesOneStepNearer() {
        val field = DistanceField()
        field.compute(layerOf("..#.", "..#.", "...."), listOf(Cell(0, 0)))
        assertEquals(Cell(3, 1), field.nextStepOrNull(3, 0))
        assertEquals(Cell(3, 2), field.nextStepOrNull(3, 1))
        // Two neighbors are a step nearer; the one above comes first.
        assertEquals(Cell(1, 0), field.nextStepOrNull(1, 1))
        assertNull(field.nextStepOrNull(0, 0))
    }

    @Test
    fun nextStepDoesNotCutCorners() {
        // From the middle the goals at both corners are a step away, but the wall stands beside the top-left one.
        val field = DistanceField()
        field.compute(layerOf(".#.", "...", "..."), listOf(Cell(0, 0), Cell(2, 2)), Neighborhood.EIGHT)
        assertEquals(1, field[1, 1])
        assertEquals(Cell(2, 2), field.nextStepOrNull(1, 1))
    }

    @Test
    fun walkingTheNextStepsGivesTheDistance() {
        val field = DistanceField()
        field.compute(layerOf("...#....", ".#.#.##.", ".#...#..", ".####.#.", "........"), listOf(Cell(7, 0)), Neighborhood.EIGHT)
        for (row in 0 until field.rows) {
            for (column in 0 until field.columns) {
                if (field[column, row] == DistanceField.UNREACHABLE) continue
                var cell = Cell(column, row)
                var steps = 0
                while (true) {
                    val next = field.nextStepOrNull(cell.column, cell.row) ?: break
                    assertEquals(field[cell.column, cell.row] - 1, field[next.column, next.row])
                    cell = next
                    steps++
                }
                assertEquals(Cell(7, 0), cell)
                assertEquals(field[column, row], steps)
            }
        }
    }

    @Test
    fun theDefaultPassesEveryTileThatIsNotSolid() {
        val field = DistanceField()
        field.compute(layerOf(".~#."), listOf(Cell(0, 0)))
        assertEquals(listOf("01--"), render(field))
    }

    @Test
    fun aPredicateChoosesThePassableTiles() {
        val field = DistanceField()
        // Water stops a walker; the solid tile does not.
        field.compute(layerOf(".#~.", "...."), listOf(Cell(0, 0))) { it.id != 1 }
        assertEquals(listOf("01-5", "1234"), render(field))
    }

    @Test
    fun computeRejectsGoalsOutsideNotPassableOrMissing() {
        val grid = layerOf("..#", "...")
        val field = DistanceField()
        field.compute(grid, listOf(Cell(0, 0)))
        assertEquals(
            "Goal cell (3, 0) is outside the grid of 3x2 cells.",
            assertFailsWith<IllegalArgumentException> { field.compute(grid, listOf(Cell(1, 1), Cell(3, 0))) }.message,
        )
        assertFailsWith<IllegalArgumentException> { field.compute(grid, listOf(Cell(0, -1))) }
        assertEquals(
            "Goal cell (2, 0) is not passable.",
            assertFailsWith<IllegalArgumentException> { field.compute(grid, listOf(Cell(2, 0))) }.message,
        )
        assertEquals(
            "The list of goals is empty.",
            assertFailsWith<IllegalArgumentException> { field.compute(grid, emptyList()) }.message,
        )
        // A rejected call keeps what the field held, here the distances from (0, 0) on the grid of 3 by 2.
        assertFailsWith<IllegalArgumentException> { field.compute(layerOf("....."), listOf(Cell(9, 9))) }
        assertEquals(listOf("01-", "123"), render(field))
    }

    @Test
    fun getRejectsCellsOutside() {
        val field = DistanceField()
        assertFailsWith<IndexOutOfBoundsException> { field[0, 0] }
        assertEquals("DistanceField(size=0x0, neighborhood=FOUR, reachable=0)", field.toString())
        field.compute(layerOf("...", "..."), listOf(Cell(0, 0)))
        assertEquals(
            "Cell (3, 1) is outside the distance field of 3x2 cells.",
            assertFailsWith<IndexOutOfBoundsException> { field[3, 1] }.message,
        )
        assertFailsWith<IndexOutOfBoundsException> { field.nextStepOrNull(0, 2) }
    }

    @Test
    fun aSmallerGridAfterALargerOneReadsOnlyItsOwnCells() {
        val field = DistanceField()
        field.compute(layerOf("....", "....", "...."), listOf(Cell(3, 2)))
        field.compute(layerOf("..", ".."), listOf(Cell(0, 0)))
        assertEquals(listOf("01", "12"), render(field))
        assertFailsWith<IndexOutOfBoundsException> { field[2, 0] }
    }

    @OptIn(NativeRuntimeApi::class)
    @Test
    fun computingAgainAllocatesNothingOnceGrown() {
        val grid = TileLayer(tileSet, columns = 64, rows = 64)
        for (row in 0 until 63) grid[if (row % 2 == 0) 20 else 40, row] = Tile.of(0)
        grid[10, 10] = Tile.of(1)
        val small = layerOf("....", "....")
        val goals = listOf(Cell(0, 0), Cell(63, 63))
        val corner = listOf(Cell(0, 0))
        val field = DistanceField()
        val objects = measure {
            repeat(20) {
                field.compute(grid, goals)
                field.compute(grid, goals, Neighborhood.EIGHT) { it.id != 1 }
                field.compute(small, corner)
            }
        }
        assertTrue(objects < 20, "60 computes allocated $objects objects")
    }

    /**
     * Returns the objects allocated while [block] runs, after running it twice so everything it grows
     * has grown, as `AllocationTest` in klyph-physics counts them: the garbage collector's second
     * collection either frees or keeps every object the block made. Reading the statistics makes a few
     * objects of its own, so what an empty block counts is taken off.
     */
    @OptIn(NativeRuntimeApi::class, ExperimentalStdlibApi::class)
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
