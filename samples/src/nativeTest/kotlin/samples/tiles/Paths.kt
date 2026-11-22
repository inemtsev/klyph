package samples.tiles

import klyph.tiles.Cell
import klyph.tiles.DistanceField
import klyph.tiles.Tile
import klyph.tiles.TileGrid
import klyph.tiles.TileSet
import klyph.tiles.findPath
import klyph.tiles.findPathOrNull
import klyph.tiles.nextStepOrNull
import samples.Sample
import samples.assertPrints
import kotlin.test.assertTrue

class Paths {
    @Sample
    fun findPath() {
        // '#' is a wall, tile 0, and '~' a ford, tile 1. A game would search its TileLayer; this grid has no tile set,
        // which needs a texture, so every search here says which tiles are passable.
        val lines = listOf("..#.", "..#.", "..~.")
        val grid = object : TileGrid {
            override val tileSet: TileSet get() = error("This grid has no tile set.")
            override val columns: Int = 4
            override val rows: Int = 3
            override fun get(column: Int, row: Int): Tile = when (lines[row][column]) {
                '#' -> Tile.of(0)
                '~' -> Tile.of(1)
                else -> Tile.EMPTY
            }
        }

        // Wading through the ford, the way goes down, across and up again.
        val path = grid.findPath(Cell(0, 0), Cell(3, 0)) { it.id != 0 }
        assertPrints(path, "[Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(1, 2), Cell(2, 2), Cell(3, 2), Cell(3, 1), Cell(3, 0)]")

        // Without the ford there is no way across.
        assertPrints(grid.findPathOrNull(Cell(0, 0), Cell(3, 0)) { it.isEmpty }, "null")
    }

    @Sample
    fun compute() {
        // '#' is a wall. A game would compute from its TileLayer; this grid has no tile set, which needs a texture, so
        // the call says which tiles are passable.
        val lines = listOf("....", ".##.", "....")
        val grid = object : TileGrid {
            override val tileSet: TileSet get() = error("This grid has no tile set.")
            override val columns: Int = 4
            override val rows: Int = 3
            override fun get(column: Int, row: Int): Tile = if (lines[row][column] == '#') Tile.of(0) else Tile.EMPTY
        }

        // Every cell's steps to the base in the bottom-right corner, around the wall.
        val field = DistanceField()
        field.compute(grid, listOf(Cell(3, 2))) { it.isEmpty }
        assertPrints(field[3, 2], "0")
        assertPrints(field[0, 0], "5")
        assertTrue(field[1, 1] == DistanceField.UNREACHABLE)
    }

    @Sample
    fun nextStepOrNull() {
        // '#' is a wall. A game would compute from its TileLayer; this grid has no tile set, which needs a texture, so
        // the call says which tiles are passable.
        val lines = listOf("....", ".##.", "....")
        val grid = object : TileGrid {
            override val tileSet: TileSet get() = error("This grid has no tile set.")
            override val columns: Int = 4
            override val rows: Int = 3
            override fun get(column: Int, row: Int): Tile = if (lines[row][column] == '#') Tile.of(0) else Tile.EMPTY
        }
        val field = DistanceField()
        field.compute(grid, listOf(Cell(3, 2))) { it.isEmpty }

        // An enemy in the top-left corner walks along the top toward the base.
        assertPrints(field.nextStepOrNull(0, 0), "Cell(1, 0)")
        // At the base it has arrived.
        assertPrints(field.nextStepOrNull(3, 2), "null")
    }
}
