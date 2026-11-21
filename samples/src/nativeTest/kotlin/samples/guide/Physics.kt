// Snippets of docs/physics.md. They compile with the samples but never run, because they need a
// running app. Each function body is code for the setup block of runApp.
package samples.guide

import klyph.app.App
import klyph.app.Phase
import klyph.collision.CollisionLayers
import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.math.Rect
import klyph.physics.BodyKind
import klyph.physics.ContactResponse
import klyph.physics.DebugDraw
import klyph.physics.JointEnd
import klyph.physics.Material
import klyph.physics.Motion
import klyph.physics.Overlaps
import klyph.physics.ParticleCollision
import klyph.physics.PhysicsBody
import klyph.physics.PhysicsConfig
import klyph.physics.PhysicsLayers
import klyph.physics.PhysicsWorld
import klyph.physics.QueryTargets
import klyph.physics.RayHit
import klyph.physics.Shape
import klyph.physics.SoftBodyShape
import klyph.physics.SolidGrid
import klyph.physics.SolidShape
import klyph.physics.SolidTags
import klyph.physics.TouchSide
import klyph.physics.addPhysics
import klyph.physics.attach
import klyph.physics.cloth
import klyph.physics.floorTagsOf
import klyph.physics.isOnFloor
import klyph.physics.physics
import klyph.physics.rigidBody
import klyph.physics.rope
import klyph.physics.softBody
import klyph.platform.Color
import klyph.platform.Scancode
import klyph.render.Sprite
import kotlin.time.Duration

class Physics {
    fun App.addTheWorld() {
        // snippet: add-physics
        val physics = addPhysics(PhysicsWorld(PhysicsConfig(substeps = 4, timeToSleep = Duration.INFINITE)))
        val world = physics.world
        // end snippet
        world.particles.add(x = 0f, y = 0f)
    }

    fun App.crates() {
        // snippet: crates
        val world = physics.world
        world.rigidBody(Shape.box(width = 1200f, height = 40f), x = 640f, y = 700f, kind = BodyKind.STATIC)
        repeat(5) { i ->
            val crate = world.rigidBody(Shape.box(40f, 40f), x = 640f, y = 600f - i * 45f)
            commands.spawn {
                add(Transform, Transform())
                add(Interpolated, Interpolated)
                add(Sprite, Sprite(assets.whitePixel, width = 40f, height = 40f, color = Color(0.9f, 0.6f, 0.3f)))
                // The entity's Transform follows the crate, its angle too, after every step.
                add(PhysicsBody, PhysicsBody.of(crate))
            }
        }
        // end snippet
    }

    fun App.shapes() {
        // snippet: shapes
        val world = physics.world
        val wheel = world.rigidBody(Shape.circle(radius = 12f), x = 100f, y = 0f, material = Material.RUBBER)
        val pill = world.rigidBody(Shape.capsule(length = 30f, radius = 6f), x = 200f, y = 0f)
        // A hammer: two boxes as one body, heavier at the head.
        val hammer = world.rigidBodies.add(
            listOf(Shape.box(6f, 40f, y = 10f), Shape.box(24f, 10f, y = -15f)),
            x = 300f,
            y = 0f,
        )
        world.rigidBodies.setVelocity(hammer, vx = 0f, vy = -400f)
        world.rigidBodies.setAngularVelocity(wheel, 6f)
        // end snippet
        world.rigidBodies.wake(pill)
    }

    fun App.level() {
        // snippet: level
        val grid = SolidGrid(columns = 80, rows = 45, tileSize = 16f)
        for (column in 0 until 80) grid.fill(column, 44)
        // A slope up to a ledge of ice.
        grid.fill(20, 43, shape = SolidShape.RISING)
        for (column in 21 until 30) grid.fill(column, 43, tags = LevelTags.ICE, material = Material.ICE)
        physics.world.solids = grid
        // end snippet
    }

    fun App.slime() {
        // snippet: slime
        val world = physics.world
        val dome = SoftBodyShape.semiDisc(radius = 13f, arcSegments = 14, baseSegments = 8, coreMass = 34f, heightScale = 1.3f)
        val slime = world.softBody(dome, centerX = 400f, baseY = 600f)
        addSystem(Phase.FIXED_UPDATE) { app ->
            val keys = app.input.keyboard
            if (keys.isJustPressed(Scancode.SPACE) && world.softBodies.isOnFloor(slime)) {
                world.softBodies.addVelocity(slime, vx = 0f, vy = -600f)
            }
            if (world.softBodies.floorTagsOf(slime).intersects(LevelTags.ICE)) {
                world.softBodies.addVelocity(slime, vx = 5f, vy = 0f)
            }
        }
        // end snippet
    }

    fun App.ropes() {
        // snippet: ropes
        val world = physics.world
        val lamp = world.rigidBody(Shape.circle(10f), x = 300f, y = 200f)
        // A rope from a fixed point in the ceiling down to the lamp, tied to its top.
        val rope = world.rope(fromX = 300f, fromY = 40f, toX = 300f, toY = 190f, segments = 15)
        world.attach(rope.last, lamp)
        // A curtain that tears when pulled too hard.
        world.cloth(left = 500f, top = 40f, columns = 20, rows = 14, spacing = 8f, tearAt = 1.6f)
        // end snippet
    }

    fun App.joints() {
        // snippet: joints
        val world = physics.world
        val body = world.rigidBody(Shape.box(80f, 20f), x = 400f, y = 300f)
        val front = world.rigidBody(Shape.circle(14f), x = 430f, y = 320f)
        val back = world.rigidBody(Shape.circle(14f), x = 370f, y = 320f)
        world.joints.hinge(front, body, x = 430f, y = 320f)
        val drive = world.joints.hinge(back, body, x = 370f, y = 320f)
        // A gate on a hinge to the world, which may open a quarter turn.
        val gate = world.rigidBody(Shape.box(8f, 60f), x = 600f, y = 270f)
        val hinge = world.joints.hinge(gate, JointEnd.WORLD, x = 600f, y = 240f)
        world.joints.setLimits(hinge, lower = -1.57f, upper = 0f)
        addSystem(Phase.FIXED_UPDATE) { app ->
            val speed = if (app.input.keyboard.isDown(Scancode.RIGHT)) 20f else 0f
            world.joints.setMotor(drive, speed = speed, maxTorque = 5_000_000f)
        }
        // end snippet
    }

    fun App.events() {
        // snippet: events
        val world = physics.world
        val coin = world.rigidBody(Shape.circle(6f), x = 500f, y = 600f, kind = BodyKind.STATIC)
        world.rigidBodies.setResponse(coin, ContactResponse.SENSE)
        val ball = world.rigidBody(Shape.circle(10f), x = 500f, y = 400f)
        // After addPhysics, so it reads what the step just did.
        addSystem(Phase.FIXED_UPDATE) { _ ->
            if (world.events.sensorsEntered.has(TouchSide.of(coin), TouchSide.of(ball))) {
                world.rigidBodies.remove(coin)
            }
            for (i in 0 until world.events.contactsBegun.size) {
                val touch = world.events.contactsBegun[i]
                if (touch.involves(TouchSide.SOLIDS)) println("Something landed: $touch")
            }
        }
        // end snippet
    }

    fun App.layers() {
        // snippet: layers
        val world = physics.world
        val ghost = world.rigidBody(Shape.circle(8f), x = 100f, y = 100f)
        // On layer 3, colliding with the level and nothing else.
        world.rigidBodies.setLayers(ghost, layers = CollisionLayers.of(3), mask = PhysicsLayers.SOLIDS)
        // Sand that falls through other sand would be no sand; grains collide with everything.
        world.particles.add(x = 120f, y = 100f, radius = 2f, collision = ParticleCollision.EVERYTHING)
        val shot = world.rigidBody(Shape.circle(2f), x = 0f, y = 100f)
        world.rigidBodies.setMotion(shot, Motion.BULLET)
        world.rigidBodies.setVelocity(shot, vx = 6_000f, vy = 0f)
        // end snippet
    }

    fun App.queries() {
        // snippet: queries
        val world = physics.world
        // Made once and kept: the queries write into them and allocate nothing.
        val hit = RayHit()
        val found = Overlaps()
        addSystem(Phase.FIXED_UPDATE) { app ->
            val mouse = app.input.mouse.worldPosition
            if (world.raycast(fromX = 100f, fromY = 100f, toX = mouse.x, toY = mouse.y, hit = hit)) {
                println("The laser stops at (${hit.x}, ${hit.y}) on ${hit.kind}")
            }
            val under = world.queryPoint(mouse.x, mouse.y, found, targets = QueryTargets.RIGID_BODIES + QueryTargets.SOFT_BODIES)
            if (under > 0) println("Under the mouse: $found")
        }
        // end snippet
    }

    fun App.debug() {
        // snippet: debug
        val debug = DebugDraw(assets.whitePixel, assets.defaultFont)
        addSystem(Phase.UPDATE) { app ->
            if (app.input.keyboard.isJustPressed(Scancode.F1)) debug.view = debug.view.next()
        }
        addSystem(Phase.RENDER) { app ->
            val world = app.physics.world
            world.solids?.let { debug.solids(app.canvas, it, Rect(0f, 0f, 1280f, 720f)) }
            world.rigidBodies.forEach { body -> debug.rigidBody(app.canvas, world, body, Color(0.9f, 0.6f, 0.3f)) }
            world.softBodies.forEach { body -> debug.softBody(app.canvas, world, body, Color(0.5f, 0.8f, 0.4f)) }
            debug.overlay(app.canvas, world)
        }
        // end snippet
    }
}

/** The tags of the level's tiles. */
object LevelTags {
    val ICE: SolidTags = SolidTags.of(0)
}
