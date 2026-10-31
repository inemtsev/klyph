package klyph.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SpriteSheetTest {
    @Test
    fun gridCellsAreRowMajor() {
        val texture = Texture(64, 32)
        val sheet = SpriteSheet.fromGrid(texture, columns = 4, rows = 2)
        assertEquals(8, sheet.size)
        assertEquals(texture.region(x = 0, y = 0, width = 16, height = 16), sheet[0])
        assertEquals(texture.region(x = 48, y = 0, width = 16, height = 16), sheet[3])
        assertEquals(texture.region(x = 0, y = 16, width = 16, height = 16), sheet[4])
        assertEquals(texture.region(x = 48, y = 16, width = 16, height = 16), sheet[7])
    }

    @Test
    fun marginAndSpacingOffsetCells() {
        // 2 + 3 * 10 + 2 * 1 + 2 = 36 wide; 2 + 2 * 8 + 1 + 2 = 21 high.
        val texture = Texture(36, 21)
        val sheet = SpriteSheet.fromGrid(texture, columns = 3, rows = 2, margin = 2, spacing = 1)
        assertEquals(texture.region(x = 2, y = 2, width = 10, height = 8), sheet[0])
        assertEquals(texture.region(x = 13, y = 2, width = 10, height = 8), sheet[1])
        assertEquals(texture.region(x = 24, y = 2, width = 10, height = 8), sheet[2])
        assertEquals(texture.region(x = 2, y = 11, width = 10, height = 8), sheet[3])
    }

    @Test
    fun explicitCellSizeLeavesTheRestUnused() {
        val texture = Texture(100, 100)
        val sheet = SpriteSheet.fromGrid(texture, columns = 2, rows = 1, margin = 1, spacing = 2, cellWidth = 10, cellHeight = 12)
        assertEquals(texture.region(x = 1, y = 1, width = 10, height = 12), sheet[0])
        assertEquals(texture.region(x = 13, y = 1, width = 10, height = 12), sheet[1])
    }

    @Test
    fun rejectsBadGrids() {
        val texture = Texture(64, 32)
        assertFailsWith<IllegalArgumentException> { SpriteSheet.fromGrid(texture, columns = 0, rows = 1) }
        assertFailsWith<IllegalArgumentException> { SpriteSheet.fromGrid(texture, columns = 1, rows = -1) }
        assertFailsWith<IllegalArgumentException> { SpriteSheet.fromGrid(texture, columns = 1, rows = 1, margin = -1) }
        assertFailsWith<IllegalArgumentException> { SpriteSheet.fromGrid(texture, columns = 1, rows = 1, spacing = -1) }
        assertFailsWith<IllegalArgumentException> { SpriteSheet.fromGrid(texture, columns = 128, rows = 1) }
        assertFailsWith<IllegalArgumentException> { SpriteSheet.fromGrid(texture, columns = 5, rows = 1, cellWidth = 16) }
        assertFailsWith<IllegalArgumentException> { SpriteSheet.fromGrid(texture, columns = 1, rows = 1, margin = 40) }
    }

    @Test
    fun getOutsideTheSheetThrowsAndGetOrNullReturnsNull() {
        val sheet = SpriteSheet.fromGrid(Texture(32, 16), columns = 2, rows = 1)
        assertFailsWith<IndexOutOfBoundsException> { sheet[2] }
        assertFailsWith<IndexOutOfBoundsException> { sheet[-1] }
        assertNull(sheet.getOrNull(2))
        assertEquals(sheet[1], sheet.getOrNull(1))
    }

    @Test
    fun rejectsRegionsOfAnotherTexture() {
        val texture = Texture(32, 32)
        val other = Texture(32, 32)
        assertFailsWith<IllegalArgumentException> { SpriteSheet(texture, listOf(texture.asRegion(), other.asRegion())) }
    }

    @Test
    fun copiesTheRegionList() {
        val texture = Texture(32, 32)
        val regions = mutableListOf(texture.asRegion())
        val sheet = SpriteSheet(texture, regions)
        regions.clear()
        assertEquals(1, sheet.size)
        assertEquals("SpriteSheet(size=1, texture=Texture(32x32))", sheet.toString())
    }
}
