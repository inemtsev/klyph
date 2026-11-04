package klyph.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LifetimeTest {
    @Test
    fun countsDownAndDestroysThroughCommands() {
        val world = World()
        val commands = Commands(world)
        val entity = world.create()
        val lifetime = Lifetime(1.seconds)
        world.add(entity, Lifetime, lifetime)

        world.advanceLifetimes(400.milliseconds, commands)
        assertEquals(600.milliseconds, lifetime.remaining)
        assertTrue(commands.isEmpty())

        world.advanceLifetimes(700.milliseconds, commands)
        assertEquals(Duration.ZERO, lifetime.remaining)
        assertTrue(world.isAlive(entity), "Destruction is queued, not done during the walk.")
        assertEquals(1, commands.size)
        commands.apply()
        assertFalse(world.isAlive(entity))
    }

    @Test
    fun destroyingTakesTheChildrenToo() {
        val world = World()
        val commands = Commands(world)
        val parent = world.create()
        val child = world.create()
        world.setParent(child, parent)
        world.add(parent, Lifetime, Lifetime(Duration.ZERO))

        world.advanceLifetimes(Duration.ZERO, commands)
        commands.apply()
        assertFalse(world.isAlive(parent))
        assertFalse(world.isAlive(child), "The cascade of World.destroy holds.")
    }

    @Test
    fun remainingCanBeChangedButNotMadeNegative() {
        val lifetime = Lifetime(2.seconds)
        lifetime.remaining = 5.seconds
        assertEquals(5.seconds, lifetime.remaining)
        assertFailsWith<IllegalArgumentException> { lifetime.remaining = (-1).seconds }
        val error = assertFailsWith<IllegalArgumentException> { Lifetime(Duration.INFINITE) }
        assertEquals("Lifetime Infinity is negative or infinite.", error.message)
        assertEquals("Lifetime(remaining=5s)", lifetime.toString())
    }
}
