package samples.tiles

import klyph.tiles.Cell
import klyph.tiles.DistanceField
import klyph.tiles.Neighbors
import klyph.tiles.Tile
import klyph.tiles.TileGrid
import klyph.tiles.TileSet
import klyph.tiles.shortestPath
import klyph.tiles.shortestPathOrNull
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Paths {
    @Sample
    fun cell() {
        val door = Cell(column = 3, row = 1)
        assertPrints(door.column, "3")
        assertPrints(door.row, "1")
        assertPrints(door, "Cell(3, 1)")
        assertTrue(door == Cell(column = 3, row = 1))
    }

    @Sample
    fun compute() {
        // A level of 4 by 3 cells with a wall in the middle; '#' is a wall tile.
        val level = listOf(
            "....",
            ".##.",
            "....",
        )
        val grid = object : TileGrid {
            override val tileSet: TileSet get() = error("This sample's grid has no tile set.")
            override val columns: Int = level[0].length
            override val rows: Int = level.size
            override fun get(column: Int, row: Int): Tile = if (level[row][column] == '#') Tile.of(0) else Tile.EMPTY
        }

        val field = DistanceField()
        field.compute(grid, goals = listOf(Cell(column = 0, row = 0))) { tile -> tile.isEmpty }
        assertPrints((0 until 4).map { field[it, 0] }, "[0, 1, 2, 3]")
        assertPrints((0 until 4).map { field[it, 2] }, "[2, 3, 4, 5]")
        // A wall cannot be walked, so no goal is reached from it.
        assertPrints(field[1, 1], "-1")
        assertTrue(field[1, 1] == DistanceField.UNREACHABLE)
        assertPrints(field, "DistanceField(size=4x3, reachable=10)")

        // Computing again replaces the distances, here with the nearer of two goals.
        field.compute(grid, goals = listOf(Cell(column = 0, row = 0), Cell(column = 3, row = 2))) { tile -> tile.isEmpty }
        assertPrints((0 until 4).map { field[it, 2] }, "[2, 2, 1, 0]")
    }

    @Sample
    fun shortestPathOrNull() {
        // A wall runs down the third column, so nothing on the left reaches the right.
        val level = listOf(
            "..#.",
            "..#.",
            "..#.",
        )
        val grid = object : TileGrid {
            override val tileSet: TileSet get() = error("This sample's grid has no tile set.")
            override val columns: Int = level[0].length
            override val rows: Int = level.size
            override fun get(column: Int, row: Int): Tile = if (level[row][column] == '#') Tile.of(0) else Tile.EMPTY
        }

        val path = grid.shortestPathOrNull(start = Cell(column = 0, row = 0), end = Cell(column = 1, row = 2)) { tile -> tile.isEmpty }
        assertPrints(path, "[Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(1, 2)]")
        // Nothing leads past the wall.
        val blocked = grid.shortestPathOrNull(start = Cell(column = 0, row = 0), end = Cell(column = 3, row = 0)) { tile -> tile.isEmpty }
        assertPrints(blocked, "null")
    }

    @Sample
    fun shortestPath() {
        val level = listOf(
            "....",
            ".#..",
            "....",
        )
        val grid = object : TileGrid {
            override val tileSet: TileSet get() = error("This sample's grid has no tile set.")
            override val columns: Int = level[0].length
            override val rows: Int = level.size
            override fun get(column: Int, row: Int): Tile = if (level[row][column] == '#') Tile.of(0) else Tile.EMPTY
        }
        val start = Cell(column = 0, row = 0)
        val end = Cell(column = 3, row = 2)

        // Four neighbors take five straight steps.
        assertPrints(grid.shortestPath(start = start, end = end) { tile -> tile.isEmpty }.size, "6")
        // Eight neighbors save one with a diagonal step, but never cut the corner of the wall.
        val path = grid.shortestPath(start = start, end = end, neighbors = Neighbors.EIGHT) { tile -> tile.isEmpty }
        assertPrints(path, "[Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(2, 1), Cell(3, 2)]")
        // The wall tile is not walkable, so no path ends on it.
        assertFailsWith<NoSuchElementException> {
            grid.shortestPath(start = start, end = Cell(column = 1, row = 1)) { tile -> tile.isEmpty }
        }
    }
}
