package klyph.entity

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val TOLERANCE = 1e-4f

private fun assertNear(expected: Float, actual: Float, what: String) {
    assertTrue(abs(expected - actual) <= TOLERANCE, "$what: expected $expected but was $actual.")
}

private fun assertWorld(world: WorldTransform, x: Float, y: Float, rotation: Float, scaleX: Float, scaleY: Float) {
    assertNear(x, world.x, "x")
    assertNear(y, world.y, "y")
    assertNear(rotation, world.rotation, "rotation")
    assertNear(scaleX, world.scaleX, "scaleX")
    assertNear(scaleY, world.scaleY, "scaleY")
}

class TransformsTest {
    @Test
    fun rootGetsNoWorldTransform() {
        val world = World()
        val e = world.create()
        world.add(e, Transform, Transform(x = 3f, y = 4f, rotation = 0.5f, scaleX = 2f, scaleY = 3f))
        world.propagateTransforms()
        assertNull(world.getOrNull(e, WorldTransform))
        assertEquals(0, world.count(WorldTransform))
    }

    @Test
    fun rootOfAHierarchyGetsNoWorldTransformButItsChildDoes() {
        val world = World()
        val parent = world.create()
        val child = world.create()
        world.add(parent, Transform, Transform(x = 3f, y = 4f, rotation = 0.5f, scaleX = 2f, scaleY = 3f))
        world.add(child, Transform, Transform())
        world.setParent(child, parent)
        world.propagateTransforms()
        assertNull(world.getOrNull(parent, WorldTransform))
        assertWorld(world.get(child, WorldTransform), 3f, 4f, 0.5f, 2f, 3f)
        assertEquals(1, world.count(WorldTransform))
    }

    @Test
    fun childComposesWithRotatedScaledParent() {
        val world = World()
        val parent = world.create()
        val child = world.create()
        val grandchild = world.create()
        val quarter = (PI / 2).toFloat()
        world.add(parent, Transform, Transform(x = 10f, y = 10f, rotation = quarter, scaleX = 2f, scaleY = 2f))
        world.add(child, Transform, Transform(x = 1f, y = 0f))
        world.add(grandchild, Transform, Transform(x = 1f, y = 0f, rotation = quarter, scaleX = 0.5f, scaleY = 3f))
        // Children attached in reverse storage order so the walk, not iteration order, decides.
        world.setParent(grandchild, child)
        world.setParent(child, parent)

        world.propagateTransforms()

        assertWorld(world.get(child, WorldTransform), 10f, 12f, quarter, 2f, 2f)
        // Child world is at (10, 12) rotated 90 degrees with scale 2: (1, 0) maps to (0, 2).
        assertWorld(world.get(grandchild, WorldTransform), 10f, 14f, 2 * quarter, 1f, 6f)
    }

    @Test
    fun propagationFollowsLocalChanges() {
        val world = World()
        val parent = world.create()
        val child = world.create()
        world.add(parent, Transform, Transform(x = 1f))
        world.add(child, Transform, Transform(y = 1f))
        world.setParent(child, parent)
        world.propagateTransforms()
        val first = world.get(child, WorldTransform)
        world.get(parent, Transform).x = 5f
        world.propagateTransforms()
        assertSame(first, world.get(child, WorldTransform))
        assertWorld(first, 5f, 1f, 0f, 1f, 1f)
        world.removeParent(child)
        assertNull(world.getOrNull(child, WorldTransform))
        world.propagateTransforms()
        assertNull(world.getOrNull(child, WorldTransform))
    }

    @Test
    fun removeParentKeepsTheGrandchildsWorldTransform() {
        val world = World()
        val root = world.create()
        val middle = world.create()
        val leaf = world.create()
        world.add(root, Transform, Transform(x = 100f))
        world.add(middle, Transform, Transform(x = 10f))
        world.add(leaf, Transform, Transform(x = 1f))
        world.setParent(middle, root)
        world.setParent(leaf, middle)
        world.propagateTransforms()
        assertWorld(world.get(leaf, WorldTransform), 111f, 0f, 0f, 1f, 1f)
        world.removeParent(middle)
        world.propagateTransforms()
        assertNull(world.getOrNull(middle, WorldTransform))
        assertWorld(world.get(leaf, WorldTransform), 11f, 0f, 0f, 1f, 1f)
    }

    @Test
    fun reparentingRecomputesFromTheNewParent() {
        val world = World()
        val first = world.create()
        val second = world.create()
        val child = world.create()
        world.add(first, Transform, Transform(x = 1f))
        world.add(second, Transform, Transform(x = 2f))
        world.add(child, Transform, Transform(y = 1f))
        world.setParent(child, first)
        world.propagateTransforms()
        val kept = world.get(child, WorldTransform)
        world.setParent(child, second)
        world.propagateTransforms()
        assertSame(kept, world.get(child, WorldTransform))
        assertWorld(kept, 2f, 1f, 0f, 1f, 1f)
    }

    @Test
    fun destroyingTheRootRemovesEveryWorldTransform() {
        val world = World()
        val root = world.create()
        val child = world.create()
        val grandchild = world.create()
        for (e in listOf(root, child, grandchild)) world.add(e, Transform, Transform())
        world.setParent(child, root)
        world.setParent(grandchild, child)
        world.propagateTransforms()
        assertEquals(2, world.count(WorldTransform))
        world.destroy(root)
        world.propagateTransforms()
        assertEquals(0, world.count(WorldTransform))
    }

    @Test
    fun childOfParentWithoutTransformCopiesItsOwn() {
        val world = World()
        val group = world.create()
        val child = world.create()
        world.add(child, Transform, Transform(x = 2f, y = 3f))
        world.setParent(child, group)
        world.propagateTransforms()
        assertFalse(world.has(group, WorldTransform))
        assertWorld(world.get(child, WorldTransform), 2f, 3f, 0f, 1f, 1f)
    }

    @Test
    fun middleWithoutTransformBreaksTheChain() {
        val world = World()
        val root = world.create()
        val middle = world.create()
        val child = world.create()
        val grandchild = world.create()
        world.add(root, Transform, Transform(x = 100f))
        world.add(child, Transform, Transform(x = 10f, scaleX = 2f, scaleY = 2f))
        world.add(grandchild, Transform, Transform(x = 1f))
        world.setParent(middle, root)
        world.setParent(child, middle)
        world.setParent(grandchild, child)
        world.propagateTransforms()
        assertFalse(world.has(middle, WorldTransform))
        assertWorld(world.get(child, WorldTransform), 10f, 0f, 0f, 2f, 2f)
        assertWorld(world.get(grandchild, WorldTransform), 12f, 0f, 0f, 2f, 2f)
    }

    @Test
    fun childWithoutTransformGetsNoWorldTransform() {
        val world = World()
        val parent = world.create()
        val plain = world.create()
        world.add(parent, Transform, Transform())
        world.add(plain, Health, Health(1))
        world.setParent(plain, parent)
        val other = world.create()
        world.add(other, Transform, Transform())
        world.setParent(other, parent)
        world.propagateTransforms()
        assertNull(world.getOrNull(plain, WorldTransform))
        assertEquals(1, world.count(WorldTransform))
    }

    @Test
    fun removingTransformRemovesWorldTransform() {
        val world = World()
        val parent = world.create()
        val e = world.create()
        world.add(parent, Transform, Transform())
        world.add(e, Transform, Transform())
        world.setParent(e, parent)
        world.propagateTransforms()
        assertTrue(world.has(e, WorldTransform))
        world.remove(e, Transform)
        world.propagateTransforms()
        assertFalse(world.has(e, WorldTransform))
    }

    @Test
    fun deepAndWideTreesGrowTheWorkStack() {
        val world = World()
        val root = world.create()
        world.add(root, Transform, Transform(x = 1f))
        val leaves = List(100) { i ->
            val branch = world.create()
            world.add(branch, Transform, Transform(y = i.toFloat()))
            world.setParent(branch, root)
            val leaf = world.create()
            world.add(leaf, Transform, Transform(x = 1f))
            world.setParent(leaf, branch)
            leaf
        }
        var previous = leaves.last()
        repeat(200) {
            val next = world.create()
            world.add(next, Transform, Transform(x = 1f))
            world.setParent(next, previous)
            previous = next
        }
        world.propagateTransforms()
        leaves.forEachIndexed { i, leaf -> assertWorld(world.get(leaf, WorldTransform), 2f, i.toFloat(), 0f, 1f, 1f) }
        assertWorld(world.get(previous, WorldTransform), 202f, 99f, 0f, 1f, 1f)
    }

    @Test
    fun worldTransformToString() {
        val world = World()
        val parent = world.create()
        val e = world.create()
        world.add(e, Transform, Transform(x = 1f, y = 2f))
        world.setParent(e, parent)
        world.propagateTransforms()
        assertEquals("WorldTransform(x=1.0, y=2.0, rotation=0.0, scale=1.0x1.0)", world.get(e, WorldTransform).toString())
    }
}
