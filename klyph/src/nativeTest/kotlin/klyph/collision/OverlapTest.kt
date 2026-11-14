package klyph.collision

import klyph.entity.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverlapTest {
    @Test
    fun otherThanReturnsTheOtherSlot() {
        val world = World()
        val bullet = world.create()
        val enemy = world.create()
        val overlap = Overlap(bullet, enemy)
        assertEquals(enemy, overlap.otherThan(bullet))
        assertEquals(bullet, overlap.otherThan(enemy))
    }

    @Test
    fun otherThanRejectsAnEntityInNeitherSlot() {
        val world = World()
        val overlap = Overlap(world.create(), world.create())
        val stranger = world.create()
        val error = assertFailsWith<IllegalArgumentException> { overlap.otherThan(stranger) }
        assertEquals("Entity $stranger is in neither slot of $overlap.", error.message)
    }

    @Test
    fun otherOrNullReturnsTheOtherSlot() {
        val world = World()
        val bullet = world.create()
        val enemy = world.create()
        val overlap = Overlap(bullet, enemy)
        assertEquals(enemy, overlap.otherOrNull(bullet))
        assertEquals(bullet, overlap.otherOrNull(enemy))
    }

    @Test
    fun otherOrNullReturnsNullForAnEntityInNeitherSlot() {
        val world = World()
        val overlap = Overlap(world.create(), world.create())
        assertNull(overlap.otherOrNull(world.create()))
    }

    @Test
    fun otherOrNullAgreesWithContains() {
        val world = World()
        val a = world.create()
        val b = world.create()
        val stranger = world.create()
        val overlap = Overlap(a, b)
        for (entity in listOf(a, b, stranger)) {
            assertEquals(entity in overlap, overlap.otherOrNull(entity) != null, "Entity $entity.")
        }
    }

    @Test
    fun containsChecksBothSlots() {
        val world = World()
        val a = world.create()
        val b = world.create()
        val overlap = Overlap(a, b)
        assertTrue(a in overlap)
        assertTrue(b in overlap)
        assertFalse(world.create() in overlap)
    }
}
