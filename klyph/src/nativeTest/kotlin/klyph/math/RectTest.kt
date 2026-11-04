package klyph.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RectTest {
    @Test
    fun containsIncludesTheTopLeftEdgesOnly() {
        val rect = Rect(10f, 20f, 100f, 50f)
        assertTrue(rect.contains(10f, 20f))
        assertTrue(rect.contains(109.9f, 69.9f))
        assertFalse(rect.contains(110f, 30f))
        assertFalse(rect.contains(50f, 70f))
        assertFalse(rect.contains(9.9f, 30f))
        assertTrue(Vec2(50f, 40f) in rect)
        assertFalse(Rect(0f, 0f, 0f, 10f).contains(0f, 5f), "A rectangle of no width contains nothing.")
    }

    @Test
    fun overlapsNeedsSharedArea() {
        val rect = Rect(0f, 0f, 10f, 10f)
        assertTrue(rect.overlaps(Rect(5f, 5f, 10f, 10f)))
        assertTrue(rect.overlaps(Rect(2f, 2f, 1f, 1f)), "A rectangle inside overlaps.")
        assertFalse(rect.overlaps(Rect(10f, 0f, 5f, 5f)), "Touching along an edge is no overlap.")
        assertFalse(rect.overlaps(Rect(10f, 10f, 5f, 5f)), "Touching at a corner is no overlap.")
        assertFalse(rect.overlaps(Rect(20f, 0f, 5f, 5f)))
    }

    @Test
    fun centerEqualityAndToString() {
        val rect = Rect(10f, 20f, 100f, 50f)
        assertEquals(Vec2(60f, 45f), rect.center)
        assertEquals(Rect(10f, 20f, 100f, 50f), rect)
        assertEquals(Rect(10f, 20f, 100f, 50f).hashCode(), rect.hashCode())
        assertNotEquals(Rect(10f, 20f, 100f, 51f), rect)
        assertEquals("Rect(x=10.0, y=20.0, width=100.0, height=50.0)", rect.toString())
    }

    @Test
    fun rejectsNegativeOrInfiniteSizes() {
        val error = assertFailsWith<IllegalArgumentException> { Rect(0f, 0f, -1f, 5f) }
        assertEquals("Rectangle width -1.0 is not a finite number of at least zero.", error.message)
        assertFailsWith<IllegalArgumentException> { Rect(0f, 0f, 1f, Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { Rect(Float.NaN, 0f, 1f, 1f) }
    }
}
