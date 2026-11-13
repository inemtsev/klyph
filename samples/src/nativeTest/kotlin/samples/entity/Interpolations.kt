package samples.entity

import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.drawnPositionOf
import klyph.entity.propagateTransforms
import klyph.entity.snapInterpolation
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Interpolations {
    @Sample
    fun interpolated() {
        val world = World()
        val ship = world.create()
        world.add(ship, Transform, Transform(x = 10f, y = 20f))
        // The marker is its own component value.
        world.add(ship, Interpolated, Interpolated)
        assertTrue(world.has(ship, Interpolated))
    }

    @Sample
    fun drawnPositionOf() {
        val world = World()
        val hero = world.create()
        world.add(hero, Transform, Transform(x = 100f, y = 50f))
        world.add(hero, Interpolated, Interpolated)
        // In an app, the engine saves the hero's Transform at the start of each fixed step. From the
        // hero's second step on this returns the place between the last two steps. Before that there is
        // nothing to blend from, so the hero is drawn at its Transform.
        assertPrints(world.drawnPositionOf(hero, alpha = 0.5f), "Vec2(100.0, 50.0)")

        // A child is drawn at its world transform, which propagateTransforms computes.
        val sword = world.create()
        world.add(sword, Transform, Transform(x = 20f))
        world.setParent(sword, hero)
        world.propagateTransforms()
        assertPrints(world.drawnPositionOf(sword, alpha = 0.5f), "Vec2(120.0, 50.0)")

        assertFailsWith<IllegalArgumentException> { world.drawnPositionOf(hero, alpha = 2f) }
    }

    @Sample
    fun snapInterpolation() {
        val world = World()
        val hero = world.create()
        val transform = Transform(x = 400f, y = 100f)
        world.add(hero, Transform, transform)
        world.add(hero, Interpolated, Interpolated)
        // A respawn: move the Transform first, then snap, so the hero appears at the new place
        // instead of sliding there from the old one during the next step.
        transform.x = 40f
        world.snapInterpolation(hero)
        assertPrints(world.drawnPositionOf(hero, alpha = 0.5f), "Vec2(40.0, 100.0)")
    }
}
