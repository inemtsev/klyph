// Snippets of docs/platformers.md. They compile with the samples but never run, because they need
// a running app. Each function body is code for the setup block of runApp.
package samples.guide

import klyph.app.App
import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.audio.Sound
import klyph.collision.BoxSlide
import klyph.collision.Collider
import klyph.collision.Collisions
import klyph.collision.KinematicBody
import klyph.collision.Passable
import klyph.collision.Shape
import klyph.collision.Solid
import klyph.entity.ComponentType
import klyph.entity.Entity
import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.input.axis
import klyph.input.isJustPressed
import klyph.platform.Color
import klyph.platform.GamepadButton
import klyph.platform.Scancode
import klyph.render.Sprite

// snippet: spawn-solid
/** Spawns a roof, a wall or a ledge of the level from its edges. */
fun App.spawnSolid(left: Float, top: Float, right: Float, bottom: Float, passable: Passable = Passable.NEVER): Entity =
    commands.spawn {
        add(Transform, Transform(x = (left + right) / 2, y = (top + bottom) / 2))
        add(Sprite, Sprite(assets.whitePixel, width = right - left, height = bottom - top, color = Color(0.3f, 0.3f, 0.4f)))
        add(Collider, Collider(Shape.Box(width = right - left, height = bottom - top)))
        add(Solid, Solid(passable))
    }
// end snippet

// snippet: walker
/** An enemy that walks back and forth along a roof without falling off it. */
class Walker(var direction: Float = 1f) {
    override fun toString(): String = "Walker(direction=$direction)"

    companion object : ComponentType<Walker>("Walker")
}
// end snippet

// snippet: jump-landing
/**
 * Returns where [hero] comes down if he jumps now and keeps rolling at [dx], following his body's
 * move one fixed step at a time, or `null` if he is still in the air after two seconds.
 */
fun App.jumpLandingOrNull(hero: Entity, dx: Float, slide: BoxSlide): BoxSlide? {
    val transform = world.get(hero, Transform)
    val dt = time.deltaSeconds
    var x = transform.x
    var y = transform.y
    var dy = -700f
    repeat(120) {
        // The same gravity and fall limit as the system that moves him.
        dy = minOf(dy + 1800f * dt, 900f)
        collisions.slideCollider(hero, x = x, y = y, dx = dx * dt, dy = dy * dt, result = slide)
        if (slide.isOnFloor) return slide
        if (slide.isOnCeiling) dy = 0f
        x = slide.x
        y = slide.y
    }
    return null
}
// end snippet

class Platformers {
    fun App.buildLevel() {
        // snippet: level
        // Two roofs with a gap between them, and a steel ledge over the gap.
        spawnSolid(left = 0f, top = 400f, right = 900f, bottom = 1000f)
        spawnSolid(left = 1100f, top = 360f, right = 1800f, bottom = 1000f)
        spawnSolid(left = 860f, top = 300f, right = 1140f, bottom = 312f, passable = Passable.FROM_BELOW)
        // end snippet
    }

    fun App.spawnHero(): Entity {
        // snippet: hero
        val hero = commands.spawn {
            add(Transform, Transform(x = 100f, y = 380f - Collisions.CONTACT_GAP))
            add(Sprite, Sprite(assets.whitePixel, width = 24f, height = 40f, color = Color.YELLOW))
            add(Collider, Collider(Shape.Box(width = 24f, height = 40f)))
            add(KinematicBody, KinematicBody())
            // Drawn between its last two steps, so it moves smoothly at any frame rate.
            add(Interpolated, Interpolated)
        }
        // end snippet
        return hero
    }

    fun App.runAndJump(hero: Entity) {
        // snippet: run-and-jump
        var coyote = 0f
        var jumpBuffer = 0f
        addSystem(Phase.FIXED_UPDATE) { app ->
            val body = app.world.get(hero, KinematicBody)
            val keyboard = app.input.keyboard
            val dt = app.time.deltaSeconds
            body.dx = keyboard.axis(Scancode.A, Scancode.D) * 320f
            // Coyote time: a jump still works a tenth of a second after running off an edge.
            coyote = if (body.isOnFloor) 0.1f else coyote - dt
            // Jump buffer: a press a tenth of a second before landing still jumps when it lands.
            val pressed = keyboard.isJustPressed(Scancode.SPACE) || app.input.gamepads.isJustPressed(GamepadButton.SOUTH)
            jumpBuffer = if (pressed) 0.1f else jumpBuffer - dt
            if (jumpBuffer > 0f && coyote > 0f) {
                body.dy = -700f
                coyote = 0f
                jumpBuffer = 0f
            }
            // Letting go of the button early makes a short hop.
            val gravity = if (body.dy < 0f && !keyboard.isDown(Scancode.SPACE)) 5400f else 1800f
            body.dy = minOf(body.dy + gravity * dt, 900f)
        }
        // end snippet
    }

    fun App.landSound(hero: Entity, thud: Sound) {
        // snippet: land
        addSystem(Phase.FIXED_UPDATE) { app ->
            val body = app.world.get(hero, KinematicBody)
            if (body.hasLanded && body.landingSpeed > 400f) app.audio.play(thud)
        }
        // end snippet
    }

    fun App.dropDown(hero: Entity) {
        // snippet: drop
        addSystem(Phase.FIXED_UPDATE) { app ->
            val body = app.world.get(hero, KinematicBody)
            // Down while standing on a ledge: fall through it.
            if (body.isOnFloor && app.input.keyboard.isJustPressed(Scancode.S)) body.dropThrough()
        }
        // end snippet
    }

    fun App.patrol() {
        // snippet: patrol
        commands.spawn {
            add(Transform, Transform(x = 1400f, y = 344f - Collisions.CONTACT_GAP))
            add(Sprite, Sprite(assets.whitePixel, width = 24f, height = 32f, color = Color.RED))
            add(Collider, Collider(Shape.Box(width = 24f, height = 32f)))
            add(KinematicBody, KinematicBody())
            add(Walker, Walker())
        }
        addSystem(Phase.FIXED_UPDATE) { app ->
            app.world.forEach(Walker, KinematicBody, Transform) { _, walker, body, transform ->
                // The floor just ahead of the feet, at the bottom of the 32 tall box, or a gap.
                val ahead = transform.x + walker.direction * 16f
                val floorAhead = app.collisions.floorBelowOrNull(x = ahead, y = transform.y + 16f, width = 4f, depth = 8f)
                if (body.isOnWall || (body.isOnFloor && floorAhead == null)) walker.direction = -walker.direction
                body.dx = walker.direction * 80f
                body.dy += 1800f * app.time.deltaSeconds
            }
        }
        // end snippet
    }
}
