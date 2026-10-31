package klyph.event

import klyph.platform.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(KeyPressed(escape, isRepeat = false), KeyPressed(escape, isRepeat = false))
        assertEquals("KeyPressed(key=KeyCode(27), isRepeat=true)", KeyPressed(escape, isRepeat = true).toString())
        assertEquals("KeyReleased(key=KeyCode(27))", KeyReleased(escape).toString())
        assertEquals(WindowResized(800, 600), WindowResized(800, 600))
        assertEquals("WindowResized(pixelWidth=800, pixelHeight=600)", WindowResized(800, 600).toString())
        assertEquals("QuitRequested", QuitRequested.toString())
    }
}
