package klyph.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class EventsTest {
    @Test
    fun scancodeCountCoversEveryConstant() {
        assertEquals(512, Scancode.COUNT)
        for (scancode in listOf(Scancode.A, Scancode.Z, Scancode.RIGHT_ALT, Scancode.ESCAPE)) {
            assertTrue(scancode.code.toInt() in 0 until Scancode.COUNT)
        }
        assertEquals(Scancode(4u), Scancode.A)
    }

    @Test
    fun mouseButtonsAreZeroBased() {
        assertEquals(MouseButton.LEFT, mouseButtonOrNull(1))
        assertEquals(MouseButton.X2, mouseButtonOrNull(5))
        assertNull(mouseButtonOrNull(6))
        assertNull(mouseButtonOrNull(0))
        assertEquals(5, MouseButton.COUNT)
    }

    @Test
    fun gamepadConstantsFitTheirCounts() {
        assertEquals(0, GamepadButton.SOUTH.index)
        assertTrue(GamepadButton.DPAD_RIGHT.index < GamepadButton.COUNT)
        assertEquals(6, GamepadAxis.COUNT)
        assertEquals(5, GamepadAxis.RIGHT_TRIGGER.index)
    }

    @Test
    fun axisValuesAreNormalized() {
        assertEquals(-1f, normalizedAxisValue(Short.MIN_VALUE))
        assertEquals(1f, normalizedAxisValue(Short.MAX_VALUE))
        assertEquals(0f, normalizedAxisValue(0))
    }

    @Test
    fun eventsCompareByValue() {
        val down = PlatformEvent.KeyDown(KeyCode.ESCAPE, isRepeat = false, scancode = Scancode.ESCAPE)
        assertEquals(down, PlatformEvent.KeyDown(KeyCode.ESCAPE, isRepeat = false, scancode = Scancode.ESCAPE))
        assertEquals(down.hashCode(), PlatformEvent.KeyDown(KeyCode.ESCAPE, isRepeat = false, scancode = Scancode.ESCAPE).hashCode())
        assertNotEquals<PlatformEvent>(down, PlatformEvent.KeyDown(KeyCode.ESCAPE, isRepeat = true, scancode = Scancode.ESCAPE))
        assertEquals(
            PlatformEvent.MouseMoved(x = 1f, y = 2f, dx = 3f, dy = 4f),
            PlatformEvent.MouseMoved(x = 1f, y = 2f, dx = 3f, dy = 4f),
        )
        assertEquals(
            PlatformEvent.GamepadAxisMoved(GamepadId(3u), GamepadAxis.LEFT_X, 0.5f),
            PlatformEvent.GamepadAxisMoved(GamepadId(3u), GamepadAxis.LEFT_X, 0.5f),
        )
    }

    @Test
    fun eventsPrintTheirFields() {
        assertEquals(
            "KeyDown(key=KeyCode(27), isRepeat=true, scancode=Scancode(41))",
            PlatformEvent.KeyDown(KeyCode.ESCAPE, isRepeat = true, scancode = Scancode.ESCAPE).toString(),
        )
        assertEquals("KeyUp(key=KeyCode(97), scancode=Scancode(4))", PlatformEvent.KeyUp(KeyCode.A, Scancode.A).toString())
        assertEquals(
            "MouseButtonDown(button=MouseButton(0), x=1.0, y=2.0, clicks=2)",
            PlatformEvent.MouseButtonDown(MouseButton.LEFT, x = 1f, y = 2f, clicks = 2).toString(),
        )
        assertEquals("MouseWheelScrolled(dx=0.0, dy=1.0)", PlatformEvent.MouseWheelScrolled(dx = 0f, dy = 1f).toString())
        assertEquals(
            "GamepadConnected(id=GamepadId(7), name=Pad)",
            PlatformEvent.GamepadConnected(GamepadId(7u), "Pad").toString(),
        )
        assertEquals(
            "GamepadButtonUp(id=GamepadId(7), button=GamepadButton(1))",
            PlatformEvent.GamepadButtonUp(GamepadId(7u), GamepadButton.EAST).toString(),
        )
        assertEquals("WindowFocusLost", PlatformEvent.WindowFocusLost.toString())
    }

    @Test
    fun negativeWaitIsRejected() {
        EventPump().use { pump ->
            assertFailsWith<IllegalArgumentException> { pump.waitOrNull((-1).milliseconds) }
        }
    }
}
