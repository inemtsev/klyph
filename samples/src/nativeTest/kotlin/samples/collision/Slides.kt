package samples.collision

import klyph.collision.BoxSlide
import klyph.collision.Collider
import klyph.collision.Collisions
import klyph.collision.Shape
import klyph.collision.Solid
import klyph.entity.Transform
import klyph.entity.World
import klyph.event.Events
import samples.Sample
import samples.assertPrints
import kotlin.test.assertTrue

class Slides {
    @Sample
    fun slideBox() {
        val world = World()
        val floor = world.create()
        world.add(floor, Transform, Transform(x = 0f, y = 120f))
        world.add(floor, Collider, Collider(Shape.Box(width = 400f, height = 40f)))
        world.add(floor, Solid, Solid())
        // In a game the engine keeps app.collisions up to date with the world.
        val collisions = Collisions()
        collisions.detect(world, Events())

        // Where would a 20 by 40 box at (0, 0) end up moving 15 across and 200 down in one step?
        val slide = BoxSlide()
        collisions.slideBox(x = 0f, y = 0f, width = 20f, height = 40f, dx = 15f, dy = 200f, result = slide)
        assertPrints(slide.x, "15.0")
        assertPrints(slide.isOnFloor, "true")
        // It stopped with its bottom a contact gap above the floor's top at 100.
        assertTrue(slide.y + 20f < 100f && slide.y + 20f > 99.9f)
    }

    @Sample
    fun slideCollider() {
        val world = World()
        val floor = world.create()
        world.add(floor, Transform, Transform(x = 0f, y = 120f))
        world.add(floor, Collider, Collider(Shape.Box(width = 400f, height = 40f)))
        world.add(floor, Solid, Solid())
        val hero = world.create()
        world.add(hero, Transform, Transform(x = 0f, y = 0f))
        // The box's bottom, the feet, is 32 below the hero's position.
        world.add(hero, Collider, Collider(Shape.Box(width = 20f, height = 40f), offsetY = 12f))
        // In a game the engine keeps app.collisions up to date with the world.
        val collisions = Collisions()
        collisions.detect(world, Events())

        // Follow a jump at 120 across and 300 up, with gravity, one fixed step at a time.
        val dt = 1f / 60f
        val slide = BoxSlide()
        var x = 0f
        var y = 0f
        var dy = -300f
        var steps = 0
        do {
            dy += 1800f * dt
            collisions.slideCollider(hero, x = x, y = y, dx = 120f * dt, dy = dy * dt, result = slide)
            x = slide.x
            y = slide.y
            steps++
        } while (!slide.isOnFloor && steps < 120)
        assertPrints(slide.isOnFloor, "true")
        // Half a second later the jump comes down with the feet a contact gap above the floor.
        assertTrue(y + 32f < 100f && y + 32f > 99.9f)
        assertTrue(steps in 25..35)
    }
}
