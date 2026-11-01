package klyph.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private open class Score(val points: Int)

private class BonusScore(points: Int) : Score(points)

private class Settings(val volume: Int)

class ResourcesTest {
    @Test
    fun insertGetHasAndRemove() {
        val resources = Resources()
        val score = Score(3)
        assertFalse(resources.has<Score>())
        assertNull(resources.getOrNull<Score>())

        resources.insert(score)
        assertTrue(resources.has<Score>())
        assertSame(score, resources.get<Score>())
        assertSame(score, resources.getOrNull<Score>())
        assertEquals(1, resources.size)

        assertSame(score, resources.remove<Score>())
        assertNull(resources.remove<Score>())
        assertFalse(resources.has<Score>())
        assertEquals(0, resources.size)
    }

    @Test
    fun insertReplacesResourceOfSameClass() {
        val resources = Resources()
        resources.insert(Score(1))
        resources.insert(Score(2))
        assertEquals(2, resources.get<Score>().points)
        assertEquals(1, resources.size)
    }

    @Test
    fun differentClassesAreKeptApart() {
        val resources = Resources()
        resources.insert(Score(1))
        resources.insert(Settings(80))
        assertEquals(1, resources.get<Score>().points)
        assertEquals(80, resources.get<Settings>().volume)
        assertEquals(2, resources.size)
        assertEquals("Resources(size=2)", resources.toString())
    }

    @Test
    fun subclassDoesNotSatisfySuperclass() {
        val resources = Resources()
        resources.insert(BonusScore(5))
        assertNull(resources.getOrNull<Score>())
        assertFalse(resources.has<Score>())
        assertEquals(5, resources.get<BonusScore>().points)
    }

    @Test
    fun getThrowsWithTypeName() {
        val resources = Resources()
        val error = assertFailsWith<NoSuchElementException> { resources.get<Settings>() }
        assertEquals("No resource of type Settings is registered.", error.message)
    }
}
