package samples.physics

import klyph.physics.BodyKind
import klyph.physics.JointEnd
import klyph.physics.PhysicsWorld
import klyph.physics.Shape
import klyph.physics.SolidGrid
import klyph.physics.SolidTags
import klyph.physics.TouchSide
import samples.Sample
import samples.assertPrints
import kotlin.time.Duration.Companion.seconds

class EventSamples {
    @Sample
    fun contactsBegun() {
        val world = PhysicsWorld()
        val ground = world.rigidBody(Shape.box(200f, 10f), x = 0f, y = 30f, kind = BodyKind.STATIC)
        val ball = world.rigidBody(Shape.circle(5f), x = 0f, y = 0f)
        var landed = false
        repeat(60) {
            world.step(1.seconds / 60)
            if (world.events.contactsBegun.has(TouchSide.of(ball), TouchSide.of(ground))) landed = true
        }
        assertPrints(landed, "true")
    }

    @Sample
    fun isOnFloor() {
        val pad = SolidTags.of(2)
        val world = PhysicsWorld()
        val grid = SolidGrid(columns = 10, rows = 10, tileSize = 16f)
        grid.fill(column = 3, row = 9, tags = pad)
        world.solids = grid
        val drop = world.particles.add(x = 56f, y = 100f)
        repeat(60) { world.step(1.seconds / 60) }
        assertPrints(world.events.isOnFloor(drop), "true")
        assertPrints(world.events.floorTagsOf(drop).intersects(pad), "true")
    }

    @Sample
    fun brokenJointAt() {
        val world = PhysicsWorld()
        val sign = world.rigidBody(Shape.box(20f, 20f), x = 0f, y = 10f, density = 10f)
        // A weak weld to the world: the sign's weight breaks it at once.
        val nail = world.joints.weld(sign, JointEnd.WORLD, x = 0f, y = 0f, breakForce = 1f)
        world.step(1.seconds / 60)
        assertPrints(world.events.brokenJointCount, "1")
        assertPrints(world.events.brokenJointAt(0) == nail, "true")
    }
}
