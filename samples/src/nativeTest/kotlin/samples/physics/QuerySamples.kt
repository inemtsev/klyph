package samples.physics

import klyph.physics.BodyKind
import klyph.physics.HitKind
import klyph.physics.Overlaps
import klyph.physics.PhysicsWorld
import klyph.physics.QueryTargets
import klyph.physics.RayHit
import klyph.physics.Shape
import klyph.physics.rigidBody
import samples.Sample
import samples.assertPrints

class QuerySamples {
    @Sample
    fun raycast() {
        val world = PhysicsWorld()
        val wall = world.rigidBody(Shape.box(10f, 100f), x = 100f, y = 0f, kind = BodyKind.STATIC)
        // Make one RayHit and keep it: a raycast then allocates nothing.
        val hit = RayHit()
        val found = world.raycast(fromX = 0f, fromY = 0f, toX = 200f, toY = 0f, hit = hit, targets = QueryTargets.RIGID_BODIES)
        assertPrints(found, "true")
        assertPrints(hit.kind, "RIGID_BODY")
        assertPrints(hit.rigidBodyOrNull == wall, "true")
        // It hit the wall's left face, at x = 95, facing back towards the ray's start.
        assertPrints(hit.x, "95.0")
        assertPrints(hit.normalX, "-1.0")
        assertPrints(world.raycast(0f, 0f, 50f, 0f, hit), "false")
        assertPrints(hit.kind == HitKind.NOTHING, "true")
    }

    @Sample
    fun queryPoint() {
        val world = PhysicsWorld()
        val crate = world.rigidBody(Shape.box(20f, 20f), x = 0f, y = 0f)
        val found = Overlaps()
        assertPrints(world.queryPoint(5f, 5f, found), "1")
        assertPrints(found.rigidBodyAt(0) == crate, "true")
    }

    @Sample
    fun queryBox() {
        val world = PhysicsWorld()
        repeat(5) { world.rigidBody(Shape.circle(4f), x = it * 20f, y = 0f) }
        // Make one Overlaps and keep it: it grows when it has to, then allocates nothing.
        val found = Overlaps()
        val count = world.queryBox(left = 10f, top = -10f, right = 60f, bottom = 10f, found = found)
        assertPrints(count, "3")
        assertPrints(found, "Overlaps(rigidBodies=3, softBodies=0, solids=0)")
    }
}
