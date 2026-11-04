package klyph.math

import kotlin.math.PI
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

    @Test
    fun lengthSquaredNormalizedAndDistance() {
        assertEquals(25f, Vec2(3f, 4f).lengthSquared)
        assertEquals(Vec2(0.6f, 0.8f), Vec2(3f, 4f).normalized())
        assertEquals(Vec2.ZERO, Vec2.ZERO.normalized(), "The zero vector points nowhere and stays zero.")
        assertEquals(5f, Vec2(1f, 1f).distanceTo(Vec2(4f, 5f)))
        assertEquals(0f, Vec2(2f, 2f).distanceTo(Vec2(2f, 2f)))
    }

    @Test
    fun angleAndFromAngleAreInverse() {
        assertEquals(0f, Vec2(1f, 0f).angle)
        assertEquals((PI / 2).toFloat(), Vec2(0f, 2f).angle, 1e-6f, "Down the screen is a quarter turn clockwise.")
        assertEquals(PI.toFloat(), Vec2(-1f, 0f).angle, 1e-6f)
        assertEquals(0f, Vec2.ZERO.angle)
        val down = Vec2.fromAngle((PI / 2).toFloat(), 3f)
        assertEquals(0f, down.x, 1e-6f)
        assertEquals(3f, down.y, 1e-6f)
        val unit = Vec2.fromAngle(1.2f)
        assertEquals(1f, unit.length, 1e-6f)
        assertEquals(1.2f, unit.angle, 1e-6f)
    }

    @Test
    fun lerpGoesStraightAndPastTheEnds() {
        val a = Vec2(0f, 10f)
        val b = Vec2(10f, 20f)
        assertEquals(a, a.lerp(b, 0f))
        assertEquals(b, a.lerp(b, 1f))
        assertEquals(Vec2(5f, 15f), a.lerp(b, 0.5f))
        assertEquals(Vec2(20f, 30f), a.lerp(b, 2f))
    }
}
