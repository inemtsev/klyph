package klyph.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HierarchyTest {
    @Test
    fun setParentRoundTripKeepsAttachOrder() {
        val world = World()
        val parent = world.create()
        val a = world.create()
        val b = world.create()
        val c = world.create()
        assertNull(world.parentOrNull(a))
        assertEquals(emptyList(), world.childrenOf(parent))
        world.setParent(b, parent)
        world.setParent(a, parent)
        world.setParent(c, parent)
        assertEquals(parent, world.parentOrNull(a))
        assertEquals(parent, world.parentOrNull(b))
        assertEquals(listOf(b, a, c), world.childrenOf(parent))
    }

    @Test
    fun setParentToSameParentKeepsOrder() {
        val world = World()
        val parent = world.create()
        val a = world.create()
        val b = world.create()
        world.setParent(a, parent)
        world.setParent(b, parent)
        world.setParent(a, parent)
        assertEquals(listOf(a, b), world.childrenOf(parent))
    }

    @Test
    fun childrenIsASnapshot() {
        val world = World()
        val parent = world.create()
        val a = world.create()
        world.setParent(a, parent)
        val snapshot = world.childrenOf(parent)
        world.setParent(world.create(), parent)
        assertEquals(listOf(a), snapshot)
        assertEquals(2, world.childrenOf(parent).size)
    }

    @Test
    fun reparentMovesBetweenLists() {
        val world = World()
        val first = world.create()
        val second = world.create()
        val child = world.create()
        val other = world.create()
        world.setParent(other, second)
        world.setParent(child, first)
        world.setParent(child, second)
        assertEquals(second, world.parentOrNull(child))
        assertEquals(emptyList(), world.childrenOf(first))
        assertEquals(listOf(other, child), world.childrenOf(second))
    }

    @Test
    fun removeParentDetaches() {
        val world = World()
        val parent = world.create()
        val child = world.create()
        world.removeParent(child)
        world.setParent(child, parent)
        world.removeParent(child)
        assertNull(world.parentOrNull(child))
        assertEquals(emptyList(), world.childrenOf(parent))
        world.removeParent(child)
        assertNull(world.parentOrNull(child))
    }

    @Test
    fun cycleIsRejected() {
        val world = World()
        val root = world.create()
        val middle = world.create()
        val leaf = world.create()
        world.setParent(middle, root)
        world.setParent(leaf, middle)
        assertFailsWith<IllegalArgumentException> { world.setParent(root, leaf) }
        assertFailsWith<IllegalArgumentException> { world.setParent(middle, leaf) }
        assertNull(world.parentOrNull(root))
        assertEquals(listOf(leaf), world.childrenOf(middle))
    }

    @Test
    fun selfParentIsRejected() {
        val world = World()
        val e = world.create()
        assertFailsWith<IllegalArgumentException> { world.setParent(e, e) }
        assertNull(world.parentOrNull(e))
    }

    @Test
    fun deadEntityIsRejected() {
        val world = World()
        val alive = world.create()
        val dead = world.create()
        world.destroy(dead)
        assertFailsWith<IllegalArgumentException> { world.setParent(dead, alive) }
        assertFailsWith<IllegalArgumentException> { world.setParent(alive, dead) }
        assertEquals(emptyList(), world.childrenOf(alive))
    }

    @Test
    fun destroyCascadesThreeLevels() {
        val world = World()
        val root = world.create()
        val parent = world.create()
        val sibling = world.create()
        val child = world.create()
        val grandchild = world.create()
        val greatGrandchild = world.create()
        world.setParent(parent, root)
        world.setParent(sibling, root)
        world.setParent(child, parent)
        world.setParent(grandchild, child)
        world.setParent(greatGrandchild, grandchild)
        world.add(greatGrandchild, Health, Health(1))

        world.destroy(child)

        assertFalse(world.isAlive(child))
        assertFalse(world.isAlive(grandchild))
        assertFalse(world.isAlive(greatGrandchild))
        assertTrue(world.isAlive(parent))
        assertTrue(world.isAlive(root))
        assertEquals(emptyList(), world.childrenOf(parent))
        assertEquals(listOf(parent, sibling), world.childrenOf(root))
        assertEquals(0, world.count(Health))
        assertEquals(3, world.entityCount)

        world.destroy(root)
        assertEquals(0, world.entityCount)
        assertEquals(0, world.count(Parent))
        assertEquals(0, world.count(Children))
    }

    @Test
    fun reusedSlotHasNoStaleLinks() {
        val world = World()
        val parent = world.create()
        val child = world.create()
        world.setParent(child, parent)
        world.destroy(child)
        val fresh = world.create()
        assertEquals(child.index, fresh.index)
        assertNull(world.parentOrNull(fresh))
        assertNull(world.parentOrNull(child))
        assertEquals(emptyList(), world.childrenOf(parent))
    }

    @Test
    fun destroyHandlesVeryDeepChain() {
        val world = World()
        val depth = 20_000
        val chain = ArrayList<Entity>(depth)
        chain.add(world.create())
        for (i in 1 until depth) {
            val child = world.create()
            world.setParent(child, chain[i - 1])
            chain.add(child)
        }
        assertEquals(depth, world.entityCount)
        world.destroy(chain[0])
        assertTrue(chain.none { world.isAlive(it) })
        assertEquals(0, world.entityCount)
    }
}
