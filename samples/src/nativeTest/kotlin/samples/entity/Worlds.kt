package samples.entity

import klyph.entity.ComponentType
import klyph.entity.World
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Worlds {
    @Sample
    fun create() {
        val world = World()
        val first = world.create()
        val second = world.create()
        assertTrue(world.isAlive(first))
        assertTrue(world.isAlive(second))
        assertPrints(world.entityCount, "2")
    }

    @Sample
    fun destroy() {
        val Health = object : ComponentType<Int>("Health") {}

        val world = World()
        val entity = world.create()
        world.add(entity, Health, 10)
        world.destroy(entity)
        assertFalse(world.isAlive(entity))
        assertPrints(world.getOrNull(entity, Health), "null")
        assertPrints(world.entityCount, "0")
    }

    @Sample
    fun add() {
        val Health = object : ComponentType<Int>("Health") {}

        val world = World()
        val entity = world.create()
        world.add(entity, Health, 10)
        world.add(entity, Health, 20)
        assertPrints(world.get(entity, Health), "20")
        world.destroy(entity)
        assertFailsWith<IllegalArgumentException> { world.add(entity, Health, 1) }
    }

    @Sample
    fun get() {
        val Health = object : ComponentType<Int>("Health") {}

        val world = World()
        val entity = world.create()
        world.add(entity, Health, 10)
        assertPrints(world.get(entity, Health), "10")
        world.remove(entity, Health)
        assertFailsWith<NoSuchElementException> { world.get(entity, Health) }
    }

    @Sample
    fun getOrNull() {
        val Health = object : ComponentType<Int>("Health") {}

        val world = World()
        val entity = world.create()
        assertPrints(world.getOrNull(entity, Health), "null")
        world.add(entity, Health, 10)
        assertPrints(world.getOrNull(entity, Health), "10")
    }

    @Sample
    fun has() {
        val Health = object : ComponentType<Int>("Health") {}

        val world = World()
        val entity = world.create()
        assertFalse(world.has(entity, Health))
        world.add(entity, Health, 10)
        assertTrue(world.has(entity, Health))
    }

    @Sample
    fun remove() {
        val Health = object : ComponentType<Int>("Health") {}

        val world = World()
        val entity = world.create()
        world.add(entity, Health, 10)
        assertTrue(world.remove(entity, Health))
        assertFalse(world.remove(entity, Health))
    }

    @Sample
    fun count() {
        val Health = object : ComponentType<Int>("Health") {}

        val world = World()
        world.add(world.create(), Health, 10)
        world.add(world.create(), Health, 20)
        world.create()
        assertPrints(world.count(Health), "2")
    }

    @Sample
    fun forEach() {
        val Health = object : ComponentType<Int>("Health") {}

        val Mana = object : ComponentType<Int>("Mana") {}

        val Armor = object : ComponentType<Int>("Armor") {}

        val world = World()
        val both = world.create()
        world.add(both, Health, 10)
        world.add(both, Mana, 5)
        world.add(world.create(), Health, 7)

        val seenByOne = mutableListOf<Int>()
        world.forEach(Health) { _, health -> seenByOne += health }
        assertPrints(seenByOne, "[10, 7]")

        val seenByTwo = mutableListOf<Int>()
        world.forEach(Health, Mana) { _, health, mana -> seenByTwo += health + mana }
        assertPrints(seenByTwo, "[15]")

        world.add(both, Armor, 3)
        val seenByThree = mutableListOf<Int>()
        world.forEach(Health, Mana, Armor) { _, health, mana, armor ->
            seenByThree += health + mana + armor
        }
        assertPrints(seenByThree, "[18]")
    }
}
