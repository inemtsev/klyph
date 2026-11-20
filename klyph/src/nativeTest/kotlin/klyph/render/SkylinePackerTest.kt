package klyph.render

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkylinePackerTest {
    private fun segment(x: Int, y: Int, width: Int) = SkylinePacker.Segment(x, y, width)

    @Test
    fun placesLowestThenLeftmost() {
        val packer = SkylinePacker(16, 16)
        assertEquals(Placement(0, 0), packer.insert(6, 4))
        assertEquals(Placement(6, 0), packer.insert(6, 2), "Beside the first, at the top, rather than under it.")
        assertEquals(listOf(segment(0, 4, 6), segment(6, 2, 6), segment(12, 0, 4)), packer.skyline)
        assertEquals(Placement(12, 0), packer.insert(4, 3), "The free column at the right is the lowest place.")
        assertEquals(Placement(6, 2), packer.insert(5, 2), "Then the lower of the two remaining steps.")
        assertEquals(Placement(11, 3), packer.insert(3, 3), "From 11 and from 12 it rests at 3; on a tie the leftmost place wins.")
    }

    @Test
    fun aWideRectangleRestsOnTheHighestSegmentItSpans() {
        val packer = SkylinePacker(16, 16)
        packer.insert(4, 5)
        packer.insert(4, 1)
        assertEquals(Placement(8, 0), packer.insert(8, 2))
        // From x = 0 it would rest on the first segment, at 5; from x = 4 it rests on the third, at 2.
        assertEquals(Placement(4, 2), packer.insert(10, 1))
    }

    @Test
    fun neighboringSegmentsAtTheSameHeightMerge() {
        val packer = SkylinePacker(12, 16)
        packer.insert(4, 3)
        packer.insert(4, 1)
        packer.insert(4, 3)
        assertEquals(listOf(segment(0, 3, 4), segment(4, 1, 4), segment(8, 3, 4)), packer.skyline)
        assertEquals(Placement(4, 1), packer.insert(4, 2), "Raises the middle segment to the height of its neighbors.")
        assertEquals(listOf(segment(0, 3, 12)), packer.skyline, "Three segments at height 3 become one.")
    }

    @Test
    fun aRectangleThatCutsASegmentShortensIt() {
        val packer = SkylinePacker(16, 16)
        packer.insert(10, 2)
        packer.insert(6, 3)
        // It rests lower on the first segment than on the second, and covers only part of it.
        assertEquals(Placement(0, 2), packer.insert(4, 3))
        assertEquals(listOf(segment(0, 5, 4), segment(4, 2, 6), segment(10, 3, 6)), packer.skyline)
    }

    @Test
    fun aFullAreaReturnsNullAndChangesNothing() {
        val packer = SkylinePacker(8, 8)
        assertEquals(Placement(0, 0), packer.insert(8, 6))
        val before = packer.skyline
        assertNull(packer.insert(1, 3), "Two rows are left, not three.")
        assertNull(packer.insert(9, 1), "Wider than the area.")
        assertNull(packer.insert(1, 9), "Taller than the area.")
        assertEquals(before, packer.skyline)
        assertEquals(Placement(0, 6), packer.insert(8, 2), "The last two rows still fit exactly.")
        assertNull(packer.insert(1, 1))
    }

    @Test
    fun placementsNeverOverlapAndStayInside() {
        val packer = SkylinePacker(64, 64)
        val random = Random(42)
        val placed = ArrayList<IntArray>()
        repeat(200) {
            val width = random.nextInt(1, 12)
            val height = random.nextInt(1, 12)
            val placement = packer.insert(width, height) ?: return@repeat
            assertTrue(placement.x >= 0 && placement.y >= 0 && placement.x + width <= 64 && placement.y + height <= 64)
            for (other in placed) {
                val overlaps = placement.x < other[0] + other[2] && other[0] < placement.x + width &&
                    placement.y < other[1] + other[3] && other[1] < placement.y + height
                assertTrue(!overlaps, "$placement ${width}x$height overlaps ${other.toList()}.")
            }
            placed += intArrayOf(placement.x, placement.y, width, height)
        }
        assertTrue(placed.size > 20, "Placed ${placed.size}.")
        assertEquals(64, packer.skyline.sumOf { it.width }, "The skyline spans the whole width.")
    }

    @Test
    fun rejectsEmptyRectangles() {
        val packer = SkylinePacker(8, 8)
        assertFailsWith<IllegalArgumentException> { packer.insert(0, 1) }
        assertFailsWith<IllegalArgumentException> { packer.insert(1, -1) }
        assertFailsWith<IllegalArgumentException> { SkylinePacker(0, 8) }
    }

    @Test
    fun toStringListsTheSkyline() {
        val packer = SkylinePacker(64, 64)
        packer.insert(16, 8)
        assertEquals("SkylinePacker(64x64, skyline=[0+16@8, 16+48@0])", packer.toString())
    }
}
