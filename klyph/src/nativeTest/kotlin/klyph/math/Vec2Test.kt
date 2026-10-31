package klyph.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class Vec2Test {
    @Test
    fun operators() {
        val a = Vec2(1f, 2f)
        val b = Vec2(3f, -4f)
        assertEquals(Vec2(4f, -2f), a + b)
        assertEquals(Vec2(-2f, 6f), a - b)
        assertEquals(Vec2(2.5f, 5f), a * 2.5f)
        assertEquals(Vec2(-1f, -2f), -a)
    }

    @Test
    fun lengthAndDot() {
        assertEquals(5f, Vec2(3f, 4f).length)
        assertEquals(0f, Vec2.ZERO.length)
        assertEquals(-5f, Vec2(1f, 2f).dot(Vec2(3f, -4f)))
        assertEquals(0f, Vec2(1f, 0f).dot(Vec2(0f, 7f)))
    }

    @Test
    fun equalityMatchesHashCode() {
        assertEquals(Vec2(1f, 2f), Vec2(1f, 2f))
        assertEquals(Vec2(1f, 2f).hashCode(), Vec2(1f, 2f).hashCode())
        assertNotEquals(Vec2(1f, 2f), Vec2(2f, 1f))
        assertEquals(Vec2(Float.NaN, 0f), Vec2(Float.NaN, 0f))
        assertNotEquals(Vec2(0f, 0f), Vec2(-0f, 0f))
        assertNotEquals<Any>(Vec2(1f, 2f), "Vec2(1.0, 2.0)")
    }

    @Test
    fun toStringPrintsComponents() {
        assertEquals("Vec2(1.0, 2.0)", Vec2(1f, 2f).toString())
        assertEquals("Vec2(0.0, 0.0)", Vec2.ZERO.toString())
    }
}
