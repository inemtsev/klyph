package klyph.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ScreenshotTest {
    @Test
    fun parsesFrameAndPath() {
        assertEquals(Screenshot(20, "/private/tmp/shot.png"), parseScreenshot("20:/private/tmp/shot.png"))
        assertEquals(Screenshot(1, "shot.png"), parseScreenshot("1:shot.png"))
    }

    @Test
    fun pathKeepsColonsAfterTheFirst() {
        assertEquals(Screenshot(5, "C:/shots/a.png"), parseScreenshot("5:C:/shots/a.png"))
    }

    @Test
    fun malformedValuesGiveNull() {
        assertNull(parseScreenshot(null))
        assertNull(parseScreenshot(""))
        assertNull(parseScreenshot("shot.png"))
        assertNull(parseScreenshot("20:"))
        assertNull(parseScreenshot(":shot.png"))
        assertNull(parseScreenshot("x:shot.png"))
        assertNull(parseScreenshot("0:shot.png"))
        assertNull(parseScreenshot("-3:shot.png"))
    }

    @Test
    fun rejectsInvalidValues() {
        val error = assertFailsWith<IllegalArgumentException> { Screenshot(0, "shot.png") }
        assertEquals("Screenshot frame 0 is less than one.", error.message)
        assertFailsWith<IllegalArgumentException> { Screenshot(1, "") }
    }

    @Test
    fun equalityComparesFrameAndPath() {
        assertEquals(Screenshot(3, "a.png"), Screenshot(3, "a.png"))
        assertEquals(Screenshot(3, "a.png").hashCode(), Screenshot(3, "a.png").hashCode())
        assertNotEquals(Screenshot(3, "a.png"), Screenshot(4, "a.png"))
        assertNotEquals(Screenshot(3, "a.png"), Screenshot(3, "b.png"))
    }

    @Test
    fun toStringShowsFrameAndPath() {
        assertEquals("Screenshot(frame=20, path=shot.png)", Screenshot(20, "shot.png").toString())
    }
}
