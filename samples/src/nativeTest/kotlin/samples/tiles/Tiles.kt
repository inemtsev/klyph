package samples.tiles

import klyph.tiles.Tile
import klyph.tiles.flippedDiagonally
import klyph.tiles.flippedX
import klyph.tiles.flippedY
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Tiles {
    @Sample
    fun tile() {
        // The third tile of the tileset image, counted from 0 as Tiled counts them.
        val brick = Tile.of(2)
        assertPrints(brick.id, "2")
        assertFalse(brick.isEmpty)
        assertFalse(brick.isFlippedX)

        assertTrue(Tile.EMPTY.isEmpty)
        assertPrints(Tile.EMPTY.id, "-1")
        assertPrints(Tile.EMPTY, "Tile.EMPTY")
        assertFailsWith<IllegalArgumentException> { Tile.of(-1) }
    }

    @Sample
    fun flipped() {
        val slope = Tile.of(5)
        assertPrints(slope.flippedX(), "Tile(5, flippedX)")
        assertTrue(slope.flippedY().isFlippedY)
        // Tiled's rotate button stores a quarter turn clockwise as a diagonal flip plus a flip in x.
        val turned = slope.flippedDiagonally().flippedX()
        assertPrints(turned, "Tile(5, flippedX, flippedDiagonally)")
        // Flipping twice gives the tile back, and the id never changes.
        assertPrints(turned.flippedX().flippedDiagonally(), "Tile(5)")
        assertPrints(turned.id, "5")
    }
}
