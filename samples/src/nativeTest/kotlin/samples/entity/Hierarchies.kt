package samples.entity

import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.entity.propagateTransforms
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Hierarchies {
    @Sample
    fun setParent() {
        val world = World()
        val ship = world.create()
        val leftGun = world.create()
        val rightGun = world.create()
        world.setParent(leftGun, ship)
        world.setParent(rightGun, ship)
        assertPrints(world.parentOrNull(leftGun), "Entity(0:0)")
        assertPrints(world.childrenOf(ship), "[Entity(1:0), Entity(2:0)]")

        world.removeParent(leftGun)
        assertPrints(world.parentOrNull(leftGun), "null")
        assertPrints(world.childrenOf(ship), "[Entity(2:0)]")

        // A parent cannot become a child of its own descendant.
        assertFailsWith<IllegalArgumentException> { world.setParent(ship, rightGun) }
    }

    @Sample
    fun destroy() {
        val world = World()
        val fleet = world.create()
        val ship = world.create()
        val turret = world.create()
        val barrel = world.create()
        world.setParent(ship, fleet)
        world.setParent(turret, ship)
        world.setParent(barrel, turret)

        world.destroy(ship)
        assertFalse(world.isAlive(ship))
        assertFalse(world.isAlive(turret))
        assertFalse(world.isAlive(barrel))
        assertTrue(world.isAlive(fleet))
        assertPrints(world.childrenOf(fleet), "[]")
    }

    @Sample
    fun propagateTransforms() {
        val world = World()
        val ship = world.create()
        val gun = world.create()
        world.add(ship, Transform, Transform(x = 100f, y = 50f, scaleX = 2f, scaleY = 2f))
        world.add(gun, Transform, Transform(x = 10f, y = 0f))
        world.setParent(gun, ship)

        world.propagateTransforms()
        assertPrints(world.get(gun, WorldTransform), "WorldTransform(x=120.0, y=50.0, rotation=0.0, scale=2.0 x 2.0)")

        world.get(ship, Transform).x = 0f
        world.propagateTransforms()
        assertPrints(world.get(gun, WorldTransform).x, "20.0")

        // Only entities with a parent get a WorldTransform: the ship's Transform is already in world space.
        assertPrints(world.getOrNull(ship, WorldTransform), "null")
        world.removeParent(gun)
        assertFalse(world.has(gun, WorldTransform))
    }
}
