package klyph.tiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TileTest {
    @Test
    fun ofKeepsTheIdWithoutFlips() {
        val tile = Tile.of(5)
        assertEquals(5, tile.id)
        assertFalse(tile.isEmpty)
        assertFalse(tile.isFlippedX || tile.isFlippedY || tile.isFlippedDiagonally)
        assertEquals("Tile(5)", tile.toString())
        assertEquals(Tile.of(0), Tile.of(0))
        assertEquals(Tile.MAX_ID, Tile.of(Tile.MAX_ID).id)
    }

    @Test
    fun emptyHasNoId() {
        assertTrue(Tile.EMPTY.isEmpty)
        assertEquals(-1, Tile.EMPTY.id)
        assertEquals("Tile.EMPTY", Tile.EMPTY.toString())
        assertFalse(Tile.of(0).isEmpty, "Tile 0 is the first tile of the set, not an empty cell.")
    }

    @Test
    fun ofRejectsIdsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { Tile.of(-1) }
        assertFailsWith<IllegalArgumentException> { Tile.of(Tile.MAX_ID + 1) }
    }

    @Test
    fun flipBitsAreTiledsTopThreeBits() {
        // A Tiled id of 3 with tile set firstgid 1 is tile 2; bit 32 flips x, bit 31 y, bit 30 the diagonal.
        val x = Tile((0x80000000L or 3L).toInt())
        assertTrue(x.isFlippedX)
        assertFalse(x.isFlippedY || x.isFlippedDiagonally)
        assertEquals(2, x.id)
        val y = Tile(0x40000003)
        assertTrue(y.isFlippedY)
        assertEquals(2, y.id)
        val diagonal = Tile(0x20000003)
        assertTrue(diagonal.isFlippedDiagonally)
        assertEquals(2, diagonal.id)
    }

    @Test
    fun flippedTogglesOneFlipAndKeepsTheRest() {
        val tile = Tile.of(7).flippedX().flippedDiagonally()
        assertEquals(7, tile.id)
        assertTrue(tile.isFlippedX && tile.isFlippedDiagonally)
        assertFalse(tile.isFlippedY)
        assertEquals("Tile(7, flippedX, flippedDiagonally)", tile.toString())
        assertEquals(Tile.of(7).flippedDiagonally(), tile.flippedX())
        assertEquals(Tile.of(7), tile.flippedX().flippedDiagonally())
        assertTrue(Tile.of(7).flippedY().isFlippedY)
        assertEquals(Tile.EMPTY, Tile.EMPTY.flippedX())
    }
}
