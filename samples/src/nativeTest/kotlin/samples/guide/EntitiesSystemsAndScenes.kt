// Snippets of docs/entities-systems-and-scenes.md. They compile with the samples but never run,
// except where noted: most need a running app. Functions on App hold code for runApp's setup block.
package samples.guide

import klyph.app.App
import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.runApp
import klyph.entity.ComponentType
import klyph.entity.Interpolated
import klyph.entity.Lifetime
import klyph.entity.Transform
import klyph.entity.Velocity
import klyph.entity.World
import klyph.entity.WorldTransform
import klyph.event.KeyPressed
import klyph.event.WindowFocusLost
import klyph.platform.Color
import klyph.platform.Scancode
import klyph.render.Fade
import klyph.render.FadeEnd
import klyph.render.Sprite
import klyph.render.asRegion
import klyph.scene.buildScene
import klyph.text.TextAlign
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// snippet: component
/** How fast an entity turns, in radians per second. */
class Spin(var rate: Float) {
    companion object : ComponentType<Spin>("Spin")
}
// end snippet

// snippet: event
/** Published when the player scores, for the systems that update the score text and play a sound. */
class Scored(val points: Int)
// end snippet

// snippet: resource
/** The state of one game that no entity owns. */
class Score(var points: Int = 0, var lives: Int = 3)
// end snippet

class EntitiesSystemsAndScenes {
    // Runs without an app, but is not a test.
    fun worldBasics() {
        // snippet: world
        val world = World()
        val rock = world.create()
        world.add(rock, Transform, Transform(x = 10f, y = 20f))
        world.add(rock, Spin, Spin(rate = 2f))

        world.get(rock, Spin).rate += 0.5f
        println(world.getOrNull(rock, Sprite)) // null: the rock has no sprite
        println(world.has(rock, Spin)) // true

        world.forEach(Transform, Spin) { entity, transform, spin ->
            transform.rotation += spin.rate / 60f
            println("$entity is turned ${transform.rotation} radians")
        }
        world.destroy(rock)
        println(world.isAlive(rock)) // false
        // end snippet
    }

    fun App.spinBodies() {
        // snippet: system
        addSystem(Phase.FIXED_UPDATE) { app ->
            val dt = app.time.deltaSeconds
            app.world.forEach(Transform, Spin) { _, transform, spin ->
                transform.rotation += spin.rate * dt
            }
        }
        // end snippet
    }

    fun App.spawnAndDestroy() {
        // snippet: commands
        addSystem(Phase.FIXED_UPDATE) { app ->
            app.world.forEach(Transform, Velocity) { entity, transform, _ ->
                // Destroying inside the loop would change the stores it walks, so queue it.
                if (transform.y < -50f) app.commands.destroy(entity)
            }
        }
        addSystem(Phase.UPDATE) { app ->
            if (app.input.keyboard.isJustPressed(Scancode.SPACE)) {
                app.commands.spawn {
                    add(Transform, Transform(x = 640f, y = 600f))
                    add(Velocity, Velocity(dy = -400f))
                    add(Sprite, Sprite(app.assets.disc(8)))
                }
            }
        }
        // end snippet
    }

    fun App.buildShip() {
        // snippet: hierarchy
        val ship = commands.spawn {
            add(Transform, Transform(x = 640f, y = 500f))
            add(Sprite, Sprite(assets.texture("ship.png").asRegion()))
        }
        val gun = commands.spawn {
            setParent(ship)
            // In the ship's space: 40 units ahead of its center, turning and scaling with it.
            add(Transform, Transform(y = -40f))
            add(Sprite, Sprite(assets.disc(10), layer = 1))
        }
        addSystem(Phase.LATE_UPDATE) { app ->
            app.world.get(ship, Transform).rotation += 1f * app.time.deltaSeconds
            val muzzle = app.world.getOrNull(gun, WorldTransform)
            if (muzzle != null && app.time.frameCount % 60L == 0L) println("Muzzle at ${muzzle.x}, ${muzzle.y}")
        }
        // end snippet
    }

    // Needs a window: never called.
    fun fixedRate() {
        // snippet: fixed-rate
        val config = AppConfig(title = "My game", fixedRate = 50, maxFixedStepsPerFrame = 5)
        runApp(config) {
            val ball = world.create()
            world.add(ball, Transform, Transform(x = 100f, y = 100f))
            // The engine moves it 120 units a second, in fixed steps.
            world.add(ball, Velocity, Velocity(dx = 120f))
            world.add(ball, Sprite, Sprite(assets.disc(16)))
            // Drawn between its last two fixed steps, so it moves smoothly at any frame rate.
            world.add(ball, Interpolated, Interpolated)
        }
        // end snippet
    }

    fun App.addScenes() {
        // snippet: scenes
        val pause = buildScene("pause") {
            onEnter { app -> app.audio.music.volume = 0.3f }
            onExit { app -> app.audio.music.volume = 1f }
            addSystem(Phase.INPUT) { app ->
                if (app.input.keyboard.isJustPressed(Scancode.ESCAPE)) app.scenes.pop()
            }
            addSystem(Phase.RENDER) { app ->
                app.canvas.drawText(app.assets.defaultFont, "Paused", x = 640f, y = 340f, scale = 4f, align = TextAlign.CENTER)
            }
        }
        val play = buildScene("play") {
            // The player belongs to this scene, so it goes away when the scene leaves the stack.
            onEnter { app ->
                app.commands.spawn {
                    add(Transform, Transform(x = 640f, y = 600f))
                    add(Sprite, Sprite(app.assets.texture("ship.png").asRegion()))
                }
            }
            addSystem(Phase.INPUT) { app ->
                if (app.input.keyboard.isJustPressed(Scancode.ESCAPE)) app.scenes.push(pause)
            }
        }
        val title = buildScene("title") {
            addSystem(Phase.INPUT) { app ->
                if (app.input.keyboard.isJustPressed(Scancode.SPACE)) app.scenes.replace(play)
            }
            addSystem(Phase.RENDER) { app ->
                app.canvas.drawText(app.assets.defaultFont, "Press Space", x = 640f, y = 340f, scale = 4f, align = TextAlign.CENTER)
            }
        }
        scenes.replace(title)
        // end snippet
    }

    fun App.fallWithVelocity(x: Float, y: Float) {
        // snippet: velocity
        val rock = commands.spawn {
            add(Transform, Transform(x = x, y = y))
            add(Sprite, Sprite(assets.disc(12)))
            // Thrown up and to the right; the engine moves it in every fixed step.
            add(Velocity, Velocity(dx = 150f, dy = -300f))
            add(Lifetime, Lifetime(3.seconds))
        }
        addSystem(Phase.FIXED_UPDATE) { app ->
            // Runs before the engine's move in the same step, so gravity counts at once.
            app.world.getOrNull(rock, Velocity)?.let { it.dy += 600f * app.time.deltaSeconds }
        }
        // end snippet
    }

    fun App.lifetimeAndFade(x: Float, y: Float) {
        // snippet: lifetime-fade
        // A spark that lives for half a second.
        commands.spawn {
            add(Transform, Transform(x = x, y = y))
            add(Sprite, Sprite(assets.disc(6), color = Color(1f, 0.9f, 0.4f)))
            add(Lifetime, Lifetime(500.milliseconds))
        }
        // A "+100" that waits a second, fades out over half a second and is then destroyed.
        commands.spawn {
            add(Transform, Transform(x = x, y = y - 20f))
            add(Sprite, Sprite(assets.texture("plus100.png").asRegion()))
            add(Fade, Fade(500.milliseconds, end = FadeEnd.DESTROY, delay = 1.seconds))
        }
        // end snippet
    }

    fun App.shareScore() {
        // snippet: resources
        resources.insert(Score())
        addSystem(Phase.UPDATE) { app ->
            val score = app.resources.get<Score>()
            if (score.lives == 0) app.quit()
        }
        // end snippet
    }

    fun App.useEvents() {
        // snippet: events
        addSystem(Phase.UPDATE) { app ->
            if (app.input.keyboard.isJustPressed(Scancode.ENTER)) app.events.publish(Scored(points = 100))
        }
        // Added later in the same phase, so it sees what the system above published this frame.
        addSystem(Phase.UPDATE) { app ->
            app.events.forEach<Scored> { scored -> app.resources.get<Score>().points += scored.points }
        }
        // The engine publishes its own events before the first INPUT system runs.
        addSystem(Phase.INPUT) { app ->
            app.events.forEach<KeyPressed> { key -> if (!key.isRepeat) println("Pressed ${key.scancode}") }
            if (app.events.any<WindowFocusLost>()) println("The window went to the background.")
        }
        // end snippet
    }
}
