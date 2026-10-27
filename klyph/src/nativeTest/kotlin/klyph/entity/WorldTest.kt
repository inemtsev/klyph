package klyph.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Health(var points: Int) {
    companion object : ComponentType<Health>("Health")
}

class Mana(var points: Int) {
    companion object : ComponentType<Mana>("Mana")
}

class Armor(var points: Int) {
    companion object : ComponentType<Armor>("Armor")
}

class WorldTest {
    @Test
    fun createReturnsDistinctAliveEntities() {
        val world = World()
        val a = world.create()
        val b = world.create()
        assertNotEquals(a, b)
        assertTrue(world.isAlive(a))
        assertTrue(world.isAlive(b))
        assertEquals(2, world.entityCount)
    }

    @Test
    fun destroyMakesEntityDeadAndRemovesComponents() {
        val world = World()
        val e = world.create()
        world.add(e, Health, Health(1))
        world.add(e, Mana, Mana(2))
        world.destroy(e)
        assertFalse(world.isAlive(e))
        assertEquals(0, world.entityCount)
        assertEquals(0, world.count(Health))
        assertEquals(0, world.count(Mana))
        assertNull(world.getOrNull(e, Health))
    }

    @Test
    fun destroyTwiceIsHarmless() {
        val world = World()
        val e = world.create()
        world.destroy(e)
        world.destroy(e)
        assertEquals(0, world.entityCount)
    }

    @Test
    fun reusedSlotGetsNewGeneration() {
        val world = World()
        val old = world.create()
        world.add(old, Health, Health(5))
        world.destroy(old)
        val fresh = world.create()
        assertEquals(old.index, fresh.index)
        assertEquals(old.generation + 1, fresh.generation)
        assertTrue(world.isAlive(fresh))
        assertFalse(world.isAlive(old))
        assertNull(world.getOrNull(old, Health))
        assertFalse(world.has(fresh, Health))
    }

    @Test
    fun addGetHasRemoveCount() {
        val world = World()
        val e = world.create()
        val health = Health(10)
        assertFalse(world.has(e, Health))
        assertNull(world.getOrNull(e, Health))
        assertEquals(0, world.count(Health))
        world.add(e, Health, health)
        assertTrue(world.has(e, Health))
        assertSame(health, world.get(e, Health))
        assertSame(health, world.getOrNull(e, Health))
        assertEquals(1, world.count(Health))
        assertTrue(world.remove(e, Health))
        assertFalse(world.remove(e, Health))
        assertFalse(world.has(e, Health))
        assertEquals(0, world.count(Health))
    }

    @Test
    fun removeOfUnusedTypeReturnsFalse() {
        val world = World()
        assertFalse(world.remove(world.create(), Health))
    }

    @Test
    fun addOnDeadEntityThrows() {
        val world = World()
        val e = world.create()
        world.destroy(e)
        assertFailsWith<IllegalArgumentException> { world.add(e, Health, Health(1)) }
    }

    @Test
    fun getOnMissingComponentThrowsWithSentence() {
        val world = World()
        val e = world.create()
        val error = assertFailsWith<NoSuchElementException> { world.get(e, Health) }
        assertTrue(error.message!!.endsWith("."))
        assertTrue(error.message!!.contains("Health"))
    }

    @Test
    fun addReplacesExistingComponent() {
        val world = World()
        val e = world.create()
        world.add(e, Health, Health(1))
        val second = Health(2)
        world.add(e, Health, second)
        assertSame(second, world.get(e, Health))
        assertEquals(1, world.count(Health))
    }

    @Test
    fun swapRemoveKeepsOtherComponents() {
        val world = World()
        val a = world.create()
        val b = world.create()
        val c = world.create()
        world.add(a, Health, Health(1))
        world.add(b, Health, Health(2))
        world.add(c, Health, Health(3))
        world.remove(b, Health)
        assertEquals(2, world.count(Health))
        assertEquals(1, world.get(a, Health).points)
        assertEquals(3, world.get(c, Health).points)
        assertFalse(world.has(b, Health))
        world.remove(a, Health)
        assertEquals(3, world.get(c, Health).points)
        assertEquals(1, world.count(Health))
    }

    @Test
    fun forEachTwoVisitsOnlyEntitiesWithBoth() {
        val world = World()
        val both = world.create()
        val onlyHealth = world.create()
        val onlyMana = world.create()
        world.add(both, Health, Health(1))
        world.add(both, Mana, Mana(2))
        world.add(onlyHealth, Health, Health(3))
        world.add(onlyMana, Mana, Mana(4))
        val visited = mutableListOf<Entity>()
        world.forEach(Health, Mana) { e, h, m ->
            visited += e
            assertEquals(1, h.points)
            assertEquals(2, m.points)
        }
        assertEquals(listOf(both), visited)
    }

    @Test
    fun forEachWithUnusedTypeVisitsNothing() {
        val world = World()
        world.add(world.create(), Health, Health(1))
        var calls = 0
        world.forEach(Health, Mana) { _, _, _ -> calls++ }
        world.forEach(Mana, Health) { _, _, _ -> calls++ }
        assertEquals(0, calls)
    }

    @Test
    fun forEachThreeVisitsOnlyEntitiesWithAll() {
        val world = World()
        val all = world.create()
        val noArmor = world.create()
        val noMana = world.create()
        world.add(all, Health, Health(1))
        world.add(all, Mana, Mana(2))
        world.add(all, Armor, Armor(3))
        world.add(noArmor, Health, Health(4))
        world.add(noArmor, Mana, Mana(5))
        world.add(noMana, Health, Health(6))
        world.add(noMana, Armor, Armor(7))
        val visited = mutableListOf<Entity>()
        world.forEach(Health, Mana, Armor) { e, h, m, a ->
            visited += e
            assertEquals(listOf(1, 2, 3), listOf(h.points, m.points, a.points))
        }
        assertEquals(listOf(all), visited)
    }

    @Test
    fun growthBeyondInitialCapacity() {
        val world = World(2)
        val entities = List(100) { world.create() }
        entities.forEachIndexed { i, e ->
            world.add(e, Health, Health(i))
            if (i % 2 == 0) world.add(e, Mana, Mana(i * 10))
        }
        assertEquals(100, world.entityCount)
        assertEquals(100, world.count(Health))
        assertEquals(50, world.count(Mana))
        entities.forEachIndexed { i, e ->
            assertTrue(world.isAlive(e))
            assertEquals(i, world.get(e, Health).points)
            assertEquals(i % 2 == 0, world.has(e, Mana))
        }
        var visited = 0
        world.forEach(Health, Mana) { _, h, m ->
            assertEquals(h.points * 10, m.points)
            visited++
        }
        assertEquals(50, visited)
        entities.forEach { world.destroy(it) }
        assertEquals(0, world.entityCount)
        assertEquals(0, world.count(Health))
    }

    @Test
    fun componentTypeToString() {
        assertEquals("ComponentType(Health)", Health.toString())
        assertEquals("Entity(3:1)", Entity.of(3, 1).toString())
    }
}
