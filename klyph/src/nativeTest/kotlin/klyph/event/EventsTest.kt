package klyph.event

import klyph.platform.GamepadButton
import klyph.platform.GamepadId
import klyph.platform.KeyCode
import klyph.platform.MouseButton
import klyph.platform.Scancode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private class Hit(val id: Int)

private class Spawned(val id: Int)

private inline fun <reified T : Any> Events.collect(selector: (T) -> Int): List<Int> {
    val result = ArrayList<Int>()
    forEach<T> { result.add(selector(it)) }
    return result
}

class EventsTest {
    @Test
    fun eventIsReadableThisFrameThenDropped() {
        val events = Events()
        events.publish(Hit(1))
        assertEquals(listOf(1), events.collect<Hit> { it.id })
        assertEquals(listOf(1), events.collect<Hit> { it.id })
        events.endFrame()
        assertEquals(emptyList(), events.collect<Hit> { it.id })
        assertFalse(events.any<Hit>())
    }

    @Test
    fun eachReaderSeesAnEventExactlyOnceAcrossFrames() {
        val events = Events()
        var toggles = 0
        repeat(3) {
            events.endFrame()
            if (it == 0) events.publish(Hit(1))
            events.forEach<Hit> { toggles++ }
        }
        assertEquals(1, toggles)
    }

    @Test
    fun typesDoNotMixAndAnyReflectsPresence() {
        val events = Events()
        assertFalse(events.any<Hit>())
        assertFalse(events.any<Spawned>())
        events.publish(Hit(1))
        assertTrue(events.any<Hit>())
        assertFalse(events.any<Spawned>())
        events.publish(Spawned(2))
        assertEquals(listOf(1), events.collect<Hit> { it.id })
        assertEquals(listOf(2), events.collect<Spawned> { it.id })
    }

    @Test
    fun publishingInsideForEachOfSameTypeIsSafe() {
        val events = Events()
        events.publish(Hit(1))
        events.publish(Hit(2))
        val seen = ArrayList<Int>()
        events.forEach<Hit> {
            seen.add(it.id)
            events.publish(Hit(it.id + 10))
        }
        assertEquals(listOf(1, 2), seen)
        assertEquals(listOf(1, 2, 11, 12), events.collect<Hit> { it.id })
    }

    @Test
    fun publishOrderIsPreservedWithinAFrame() {
        val events = Events()
        events.publish(Hit(1))
        events.publish(Hit(2))
        events.publish(Hit(3))
        assertEquals(listOf(1, 2, 3), events.collect<Hit> { it.id })
        events.endFrame()
        events.publish(Hit(4))
        assertEquals(listOf(4), events.collect<Hit> { it.id })
    }

    @Test
    fun objectEventsAreDeliveredByTheirOwnClass() {
        val events = Events()
        events.publish(QuitRequested)
        assertTrue(events.any<QuitRequested>())
        var count = 0
        events.forEach<QuitRequested> { count++ }
        assertEquals(1, count)
    }

    @Test
    fun toStringCountsTypesAndEvents() {
        val events = Events()
        assertEquals("Events(types=0, pending=0)", events.toString())
        events.publish(Hit(1))
        events.publish(Hit(2))
        events.publish(Spawned(3))
        assertEquals("Events(types=2, pending=3)", events.toString())
        events.endFrame()
        assertEquals("Events(types=0, pending=0)", events.toString())
    }

    @Test
    fun engineEventsCompareByValueAndPrintTheirFields() {
        val escape = KeyCode(27u)
        val scancode = Scancode(41u)
        assertEquals(KeyPressed(escape, isRepeat = false, scancode), KeyPressed(escape, isRepeat = false, scancode))
        assertNotEquals(KeyPressed(escape, isRepeat = false, scancode), KeyPressed(escape, isRepeat = false, Scancode(1u)))
        assertEquals(
            "KeyPressed(key=KeyCode(27), isRepeat=true, scancode=Scancode(41))",
            KeyPressed(escape, isRepeat = true, scancode).toString(),
        )
        assertEquals("KeyReleased(key=KeyCode(27), scancode=Scancode(41))", KeyReleased(escape, scancode).toString())
        assertEquals(MouseMoved(1f, 2f, 3f, 4f), MouseMoved(1f, 2f, 3f, 4f))
        assertEquals("MouseMoved(x=1.0, y=2.0, dx=3.0, dy=4.0)", MouseMoved(1f, 2f, 3f, 4f).toString())
        assertEquals(MouseButtonPressed(MouseButton.LEFT, 1f, 2f, 1), MouseButtonPressed(MouseButton.LEFT, 1f, 2f, 1))
        assertEquals(
            "MouseButtonPressed(button=MouseButton(0), x=1.0, y=2.0, clicks=2)",
            MouseButtonPressed(MouseButton.LEFT, 1f, 2f, 2).toString(),
        )
        assertEquals("MouseButtonReleased(button=MouseButton(2), x=1.0, y=2.0)", MouseButtonReleased(MouseButton.RIGHT, 1f, 2f).toString())
        assertEquals(MouseWheel(0f, 1f), MouseWheel(0f, 1f))
        assertEquals("MouseWheel(dx=0.0, dy=1.0)", MouseWheel(0f, 1f).toString())
        val pad = GamepadId(7u)
        assertEquals(GamepadConnected(pad, "Pad"), GamepadConnected(pad, "Pad"))
        assertEquals("GamepadConnected(id=GamepadId(7), name=Pad)", GamepadConnected(pad, "Pad").toString())
        assertEquals("GamepadDisconnected(id=GamepadId(7))", GamepadDisconnected(pad).toString())
        assertEquals(GamepadButtonPressed(pad, GamepadButton.SOUTH), GamepadButtonPressed(pad, GamepadButton.SOUTH))
        assertEquals(
            "GamepadButtonReleased(id=GamepadId(7), button=${GamepadButton.SOUTH})",
            GamepadButtonReleased(pad, GamepadButton.SOUTH).toString(),
        )
        assertEquals("WindowFocusGained", WindowFocusGained.toString())
        assertEquals("WindowFocusLost", WindowFocusLost.toString())
        assertEquals(WindowResized(800, 600), WindowResized(800, 600))
        assertEquals("WindowResized(pixelWidth=800, pixelHeight=600)", WindowResized(800, 600).toString())
        assertEquals(WindowDensityChanged(2f), WindowDensityChanged(2f))
        assertEquals("WindowDensityChanged(pixelDensity=2.0)", WindowDensityChanged(2f).toString())
        assertEquals("QuitRequested", QuitRequested.toString())
    }
}
