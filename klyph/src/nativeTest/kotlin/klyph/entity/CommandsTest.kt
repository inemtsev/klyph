package klyph.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandsTest {
    @Test
    fun spawnedComponentsAppearOnlyAfterApply() {
        val world = World()
        val commands = Commands(world)
        val e = commands.spawn {
            add(Health, Health(5))
            add(Mana, Mana(2))
        }
        assertTrue(world.isAlive(e))
        assertNull(world.getOrNull(e, Health))
        var seen = 0
        world.forEach(Health) { _, _ -> seen++ }
        assertEquals(0, seen)

        commands.apply()
        assertEquals(5, world.get(e, Health).points)
        assertEquals(2, world.get(e, Mana).points)
        assertTrue(commands.isEmpty)
    }

    @Test
    fun spawnWithoutBuilderCreatesBareEntity() {
        val world = World()
        val commands = Commands(world)
        val e = commands.spawn()
        assertTrue(world.isAlive(e))
        assertEquals(0, commands.size)
    }

    @Test
    fun queuedDestroyKillsEntityAtApply() {
        val world = World()
        val commands = Commands(world)
        val e = world.create()
        commands.destroy(e)
        assertTrue(world.isAlive(e))
        commands.apply()
        assertFalse(world.isAlive(e))
    }

    @Test
    fun queuedAddAndRemoveTakeEffectAtApply() {
        val world = World()
        val commands = Commands(world)
        val e = world.create()
        world.add(e, Mana, Mana(1))
        commands.add(e, Health, Health(3))
        commands.remove(e, Mana)
        assertFalse(world.has(e, Health))
        assertTrue(world.has(e, Mana))
        commands.apply()
        assertTrue(world.has(e, Health))
        assertFalse(world.has(e, Mana))
    }

    @Test
    fun commandsRunInQueueOrder() {
        val world = World()
        val commands = Commands(world)
        val addThenRemove = world.create()
        val removeThenAdd = world.create()
        commands.add(addThenRemove, Health, Health(1))
        commands.remove(addThenRemove, Health)
        commands.remove(removeThenAdd, Health)
        commands.add(removeThenAdd, Health, Health(2))
        commands.apply()
        assertFalse(world.has(addThenRemove, Health))
        assertEquals(2, world.get(removeThenAdd, Health).points)
        assertEquals(1, world.count(Health))
    }

    @Test
    fun commandsAgainstDestroyedEntityAreSkipped() {
        val world = World()
        val commands = Commands(world)
        val dead = world.create()
        val parent = world.create()
        commands.add(dead, Health, Health(1))
        commands.remove(dead, Mana)
        commands.setParent(dead, parent)
        commands.destroy(dead)
        world.destroy(dead)
        val reused = world.create()
        world.add(reused, Mana, Mana(7))

        commands.apply()
        assertFalse(world.isAlive(dead))
        assertEquals(0, world.count(Health))
        assertEquals(7, world.get(reused, Mana).points)
        assertNull(world.parentOrNull(reused))
        assertTrue(world.isAlive(reused))
    }

    @Test
    fun spawnedEntityDestroyedBeforeApplyGetsNoComponents() {
        val world = World()
        val commands = Commands(world)
        val e = commands.spawn { add(Health, Health(1)) }
        commands.destroy(e)
        commands.apply()
        assertFalse(world.isAlive(e))
        assertEquals(0, world.count(Health))
    }

    @Test
    fun setParentThroughCommands() {
        val world = World()
        val commands = Commands(world)
        val parent = world.create()
        val child = world.create()
        commands.setParent(child, parent)
        assertNull(world.parentOrNull(child))
        commands.apply()
        assertEquals(parent, world.parentOrNull(child))
        assertFailsWith<IllegalArgumentException> { commands.setParent(child, child) }
    }

    @Test
    fun cycleThrowsAndLeavesQueueEmpty() {
        val world = World()
        val commands = Commands(world)
        val a = world.create()
        val b = world.create()
        world.setParent(b, a)
        commands.setParent(a, b)
        commands.add(a, Health, Health(1))
        assertFailsWith<IllegalArgumentException> { commands.apply() }
        assertTrue(commands.isEmpty)
        assertFalse(world.has(a, Health))
        commands.add(a, Health, Health(2))
        commands.apply()
        assertEquals(2, world.get(a, Health).points)
    }

    @Test
    fun growsPastInitialCapacity() {
        val world = World()
        val commands = Commands(world)
        val entities = List(1000) { commands.spawn() }
        for (e in entities) {
            commands.add(e, Health, Health(e.index))
            commands.add(e, Mana, Mana(e.index))
        }
        assertEquals(2000, commands.size)
        commands.apply()
        assertEquals(1000, world.count(Health))
        assertEquals(1000, world.count(Mana))
        for (e in entities) assertEquals(e.index, world.get(e, Mana).points)
        assertEquals(0, commands.size)
    }

    @Test
    fun sizeIsEmptyAndToString() {
        val world = World()
        val commands = Commands(world)
        assertTrue(commands.isEmpty)
        assertEquals("Commands(queued=0)", commands.toString())
        val e = world.create()
        commands.add(e, Health, Health(1))
        commands.remove(e, Mana)
        commands.destroy(e)
        assertFalse(commands.isEmpty)
        assertEquals(3, commands.size)
        assertEquals("Commands(queued=3)", commands.toString())
        commands.apply()
        assertTrue(commands.isEmpty)
        assertEquals("Commands(queued=0)", commands.toString())
    }

    @Test
    fun builderExposesSpawnedEntityForNestedCommands() {
        val world = World()
        val commands = Commands(world)
        var child = Entity(-1L)
        var seenInside = Entity(-1L)
        val root = commands.spawn {
            seenInside = entity
            val parent = entity
            child = commands.spawn {
                add(Health, Health(1))
            }
            commands.setParent(child, parent)
            add(Mana, Mana(4))
        }
        assertEquals(root, seenInside)
        commands.apply()
        assertEquals(root, world.parentOrNull(child))
        assertEquals(4, world.get(root, Mana).points)
        assertFalse(world.has(root, Health))
        assertEquals(1, world.get(child, Health).points)
    }

    @Test
    fun builderParentQueuesSetParent() {
        val world = World()
        val commands = Commands(world)
        val parent = world.create()
        val child = commands.spawn { parent(parent) }
        assertNull(world.parentOrNull(child))
        commands.apply()
        assertEquals(parent, world.parentOrNull(child))
    }
}
