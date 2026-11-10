package samples.collision

import klyph.collision.Collider
import klyph.collision.Collisions
import klyph.collision.KinematicBody
import klyph.collision.Passable
import klyph.collision.Shape
import klyph.collision.Solid
import klyph.entity.Transform
import klyph.entity.World
import samples.Sample
import samples.assertPrints
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class Bodies {
    @Sample
    fun kinematicBody() {
        val world = World()
        val floor = world.create()
        world.add(floor, Transform, Transform(x = 0f, y = 120f))
        world.add(floor, Collider, Collider(Shape.Box(width = 400f, height = 40f)))
        world.add(floor, Solid, Solid())

        val hero = world.create()
        world.add(hero, Transform, Transform(x = 0f, y = 0f))
        world.add(hero, Collider, Collider(Shape.Box(width = 20f, height = 40f)))
        world.add(hero, KinematicBody, KinematicBody(dx = 30f, dy = 400f))

        // In an app, the engine does this in every fixed step on app.collisions.
        val collisions = Collisions()
        collisions.moveBodies(world, 0.5.seconds)

        val body = world.get(hero, KinematicBody)
        assertPrints(body.isOnFloor, "true")
        assertPrints(body.dy, "0.0")
        // It moved 15 across and came down until its feet were a contact gap above the floor's top at 100.
        assertPrints(world.get(hero, Transform).x, "15.0")
        val feet = world.get(hero, Transform).y + 20f
        assertTrue(feet < 100f && feet > 99.9f)
    }

    @Sample
    fun dropThrough() {
        val world = World()
        val ledge = world.create()
        world.add(ledge, Transform, Transform(x = 0f, y = 105f))
        world.add(ledge, Collider, Collider(Shape.Box(width = 200f, height = 10f)))
        world.add(ledge, Solid, Solid(passable = Passable.FROM_BELOW))
        val hero = world.create()
        val body = KinematicBody(dy = 100f)
        world.add(hero, Transform, Transform(x = 0f, y = 80f))
        world.add(hero, Collider, Collider(Shape.Box(width = 20f, height = 40f)))
        world.add(hero, KinematicBody, body)
        val collisions = Collisions()
        collisions.moveBodies(world, 0.1.seconds)
        assertPrints(body.isOnFloor, "true")

        // Down pressed while standing on the ledge: fall through it.
        body.dropThrough()
        body.dy = 100f
        collisions.moveBodies(world, 0.1.seconds)
        assertPrints(body.isOnFloor, "false")
        assertTrue(world.get(hero, Transform).y + 20f > 100f)
    }
}
