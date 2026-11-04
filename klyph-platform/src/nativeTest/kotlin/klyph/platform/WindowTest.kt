package klyph.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowTest {
    @Test
    fun cursorModeToStringIsItsName() {
        assertEquals(listOf("VISIBLE", "HIDDEN"), CursorMode.entries.map { it.toString() })
    }

    @Test
    fun windowModeToStringIsItsName() {
        assertEquals(listOf("WINDOWED", "FULLSCREEN"), WindowMode.entries.map { it.toString() })
    }
}
