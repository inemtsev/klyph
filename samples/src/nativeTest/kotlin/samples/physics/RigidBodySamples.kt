package samples.physics

import klyph.collision.CollisionLayers
import klyph.math.Vec2
import klyph.physics.BodyKind
import klyph.physics.ContactResponse
import klyph.physics.Motion
import klyph.physics.PhysicsLayers
import klyph.physics.PhysicsWorld
import klyph.physics.Shape
import klyph.physics.rigidBody
import samples.Sample
import samples.assertPrints
import kotlin.math.PI
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RigidBodySamples {
    @Sample
    fun rigidBody() {
        val world = PhysicsWorld()
        val ground = world.rigidBody(Shape.box(400f, 20f), x = 0f, y = 100f, kind = BodyKind.STATIC)
        val crate = world.rigidBody(Shape.box(20f, 20f), x = 0f, y = 0f)

        repeat(120) { world.step(1.seconds / 60) }

        // It fell and came to rest on the ground's top, at y = 90, with its middle 10 above that.
        assertTrue(world.rigidBodies.yOf(crate) in 79f..81f)
        assertPrints(world.rigidBodies.kindOf(ground), "STATIC")
    }

    @Sample
    fun add() {
        val world = PhysicsWorld()
        // A hammer: a long handle and a heavy head, as one body that turns about its centre of mass.
        val handle = Shape.box(width = 6f, height = 40f, y = 10f)
        val head = Shape.box(width = 24f, height = 10f, y = -15f)
        val hammer = world.rigidBodies.add(listOf(handle, head), x = 0f, y = 0f)
        assertPrints(world.rigidBodies.shapesOf(hammer).size, "2")
        // The centre of mass is nearer the head, above the shapes' origin.
        assertTrue(world.rigidBodies.yOf(hammer) < 0f)
    }

    @Sample
    fun shapes() {
        val ball = Shape.circle(radius = 8f)
        val crate = Shape.box(width = 20f, height = 20f)
        val pill = Shape.capsule(length = 30f, radius = 5f)
        val wedge = Shape.polygon(listOf(Vec2(0f, 0f), Vec2(30f, 0f), Vec2(0f, -20f)))
        assertPrints(crate, "Shape(POLYGON, vertices=4, radius=0.0)")
        assertPrints(ball.contains(0f, 7f), "true")
        assertPrints(pill.vertexCount, "2")
        assertPrints(wedge.area, "300.0")
    }

    @Sample
    fun forEach() {
        val world = PhysicsWorld()
        repeat(3) { world.rigidBody(Shape.circle(5f), x = it * 20f, y = 0f) }
        var count = 0
        world.rigidBodies.forEach { body -> if (world.rigidBodies.kindOf(body) == BodyKind.DYNAMIC) count++ }
        assertPrints(count, "3")
    }

    @Sample
    fun setVelocity() {
        val world = PhysicsWorld()
        val puck = world.rigidBody(Shape.circle(5f), x = 0f, y = 0f)
        world.rigidBodies.setVelocity(puck, vx = 300f, vy = 0f)
        world.rigidBodies.setAngularVelocity(puck, PI.toFloat())
        assertPrints(world.rigidBodies.velocityXOf(puck), "300.0")
    }

    @Sample
    fun moveKinematic() {
        val world = PhysicsWorld()
        val lift = world.rigidBody(Shape.box(60f, 10f), x = 0f, y = 200f, kind = BodyKind.KINEMATIC)
        // Moved where it is told over the next step, pushing what stands on it on the way.
        world.rigidBodies.moveKinematic(lift, x = 0f, y = 190f)
        world.step(1.seconds / 60)
        assertPrints(world.rigidBodies.yOf(lift), "190.0")
    }

    @Sample
    fun setMotion() {
        val world = PhysicsWorld()
        val shot = world.rigidBody(Shape.circle(2f), x = 0f, y = 0f)
        // Swept each substep, so it can go as fast as it likes and never passes through a wall.
        world.rigidBodies.setMotion(shot, Motion.BULLET)
        world.rigidBodies.setVelocity(shot, 8_000f, 0f)
        assertPrints(world.rigidBodies.motionOf(shot), "BULLET")
    }

    @Sample
    fun setResponse() {
        val world = PhysicsWorld()
        val zone = world.rigidBody(Shape.box(100f, 100f), x = 0f, y = 0f, kind = BodyKind.STATIC)
        world.rigidBodies.setResponse(zone, ContactResponse.SENSE)
        val crate = world.rigidBody(Shape.box(10f, 10f), x = 0f, y = -20f)
        world.step(1.seconds / 60)
        // A sensor pushes nothing: the crate is inside it, and it reports that.
        assertPrints(world.events.sensorsEntered.size, "1")
        assertTrue(world.rigidBodies.yOf(crate) > -20f)
    }

    @Sample
    fun setLayers() {
        val world = PhysicsWorld()
        val ghosts = CollisionLayers.of(3)
        val ghost = world.rigidBody(Shape.circle(8f), x = 0f, y = 0f)
        // On its own layer, and colliding with nothing but the level.
        world.rigidBodies.setLayers(ghost, layers = ghosts, mask = PhysicsLayers.SOLIDS)
        assertPrints(world.rigidBodies.layersOf(ghost) == ghosts, "true")
    }

    @Sample
    fun wake() {
        val world = PhysicsWorld()
        world.rigidBody(Shape.box(400f, 20f), x = 0f, y = 20f, kind = BodyKind.STATIC)
        val crate = world.rigidBody(Shape.box(20f, 20f), x = 0f, y = 0f)
        repeat(120) { world.step(1.seconds / 60) }
        assertPrints(world.rigidBodies.isAsleep(crate), "true")
        world.rigidBodies.wake(crate)
        assertPrints(world.rigidBodies.isAsleep(crate), "false")
    }

    @Sample
    fun worldXOf() {
        val world = PhysicsWorld()
        val bar = world.rigidBody(Shape.box(40f, 4f), x = 100f, y = 50f, angle = (PI / 2).toFloat())
        // The bar's right end, in its own frame, after a quarter turn clockwise: straight below.
        assertTrue(world.rigidBodies.worldXOf(bar, 20f, 0f) in 99.9f..100.1f)
        assertTrue(world.rigidBodies.worldYOf(bar, 20f, 0f) in 69.9f..70.1f)
    }

    @Sample
    fun contains() {
        val world = PhysicsWorld()
        val ball = world.rigidBody(Shape.circle(10f), x = 0f, y = 0f)
        assertPrints(world.rigidBodies.contains(ball, 5f, 5f), "true")
        assertPrints(world.rigidBodies.contains(ball, 9f, 9f), "false")
    }
}

class LayerSamples {
    @Sample
    fun layers() {
        val world = PhysicsWorld()
        val player = CollisionLayers.of(0)
        val coins = CollisionLayers.of(1)
        val coin = world.rigidBody(Shape.circle(4f), x = 0f, y = 0f)
        // Coins collide with the player and the level, not with each other.
        world.rigidBodies.setLayers(coin, layers = coins, mask = player + PhysicsLayers.SOLIDS)
        assertPrints(PhysicsLayers.collide(coins, player + PhysicsLayers.SOLIDS, coins, player + PhysicsLayers.SOLIDS), "false")
        assertPrints(PhysicsLayers.collide(coins, player, player, CollisionLayers.ALL), "true")
    }
}
