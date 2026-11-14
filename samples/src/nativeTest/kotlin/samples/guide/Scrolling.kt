// Snippets of docs/scrolling.md. They compile with the samples but never run, because they need
// a running app. Each function body is code for the setup block of runApp.
package samples.guide

import klyph.app.App
import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.drawnPositionOf
import klyph.collision.Collider
import klyph.collision.Collisions
import klyph.collision.KinematicBody
import klyph.collision.Shape
import klyph.entity.Entity
import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.entity.snapInterpolation
import klyph.platform.SamplerAddress
import klyph.platform.SamplerFilter
import klyph.render.DrawSpace
import klyph.render.ParallaxLayer
import klyph.render.Sprite
import klyph.render.asRegion
import klyph.render.follow
import klyph.render.lookAt
import klyph.scene.Scene
import klyph.scene.buildScene
import klyph.scene.elapsedSeconds
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

// snippet: respawn
/** Puts [hero] back at ([x], [y]) at rest, drawn there at once, with the camera on him. */
fun App.respawn(hero: Entity, x: Float, y: Float) {
    val transform = world.get(hero, Transform)
    transform.x = x
    transform.y = y
    // After the move, never before: it copies the new place over the one the engine saved.
    world.snapInterpolation(hero)
    val body = world.get(hero, KinematicBody)
    body.dx = 0f
    body.dy = 0f
    // The camera cuts to him instead of gliding across the level.
    camera.lookAt(x, y)
}
// end snippet

class Scrolling {
    fun App.followHero(hero: Entity) {
        // snippet: follow-drawn
        addSystem(Phase.LATE_UPDATE) { app ->
            // Where the hero's picture is this frame, which is not where his Transform is.
            val drawn = app.drawnPositionOf(hero)
            // Aim a quarter of a second ahead of the way he runs.
            val lookAhead = app.world.get(hero, KinematicBody).dx * 0.25f
            app.camera.follow(drawn.x + lookAhead, drawn.y, smoothing = 5f, delta = app.time.delta)
        }
        // end snippet
    }

    fun App.shakeOnLanding(hero: Entity) {
        // snippet: shake
        var shake = 0f
        addSystem(Phase.FIXED_UPDATE) { app ->
            val body = app.world.get(hero, KinematicBody)
            // A hard landing starts a shake of 10 units.
            if (body.hasLanded && body.landingSpeed > 600f) shake = 10f
        }
        addSystem(Phase.LATE_UPDATE) { app ->
            // The shake fades out in a third of a second. At 0 both offsets are 0 again.
            shake = maxOf(shake - 30f * app.time.deltaSeconds, 0f)
            app.camera.offsetX = (Random.nextFloat() * 2f - 1f) * shake
            app.camera.offsetY = (Random.nextFloat() * 2f - 1f) * shake
        }
        // end snippet
    }

    fun App.paintSky() {
        // snippet: sky
        val sky = assets.texture("sky.png").asRegion()
        addSystem(Phase.RENDER) { app ->
            // The whole window in points, from its top-left corner, under everything the world draws.
            app.canvas.drawSprite(
                sky,
                x = 0f,
                y = 0f,
                width = app.window.width.toFloat(),
                height = app.window.height.toFloat(),
                space = DrawSpace.BACKGROUND,
                originX = 0f,
                originY = 0f,
            )
        }
        // end snippet
    }

    fun App.addHills() {
        // snippet: hills
        val hills = Sprite(assets.texture("hills.png").asRegion(), layer = -2, originY = 1f)
        commands.spawn {
            // The bottom edge of the strip stands on the horizon, at y = 400.
            add(Transform, Transform(x = 0f, y = 400f))
            add(Sprite, hills)
            // Half the world's speed along x, the world's own height along y, a copy every strip width.
            add(ParallaxLayer, ParallaxLayer(0.5f, factorY = 1f, repeatX = hills.width))
        }
        // end snippet
    }

    fun App.layFloor() {
        // snippet: tiled-floor
        // 16 by 16 pixel-art bricks, sampled sharply and wrapping around at the edges of the texture.
        val bricks = assets.texture("bricks.png", filter = SamplerFilter.NEAREST, address = SamplerAddress.REPEAT)
        commands.spawn {
            // A floor of 900 by 600 units with one repeat of the texture every 32 units, twice its size.
            add(Transform, Transform(x = 450f, y = 700f))
            add(Sprite, Sprite(bricks.asRegion(), width = 900f, height = 600f, tileWidth = 32f, tileHeight = 32f))
        }
        // end snippet
    }

    fun App.spawnFeetHero(): Entity {
        // snippet: feet-origin
        val art = assets.texture("hero.png").asRegion()
        // The origin (0.5, 1) is the middle of the bottom edge, so the Transform is at the hero's feet.
        val sprite = Sprite(art, originX = 0.5f, originY = 1f)
        val hero = commands.spawn {
            add(Transform, Transform(x = 100f, y = 400f - Collisions.CONTACT_GAP))
            add(Sprite, sprite)
            // The collider stays centered on the Transform plus its offset, so lift it by half its height.
            add(Collider, Collider(Shape.Box(sprite.width, sprite.height), offsetY = -sprite.height / 2f))
            add(KinematicBody, KinematicBody())
            add(Interpolated, Interpolated)
        }
        // end snippet
        return hero
    }

    fun App.bobLantern(lantern: Entity, gameOver: Scene) {
        // snippet: scene-clock
        lateinit var play: Scene
        play = buildScene("play") {
            addSystem(Phase.UPDATE) { app ->
                // The clock stops under a pause scene, so the bob goes on from where it stopped.
                app.world.get(lantern, Transform).y = 300f + sin(play.elapsedSeconds * 3f) * 6f
                // The round lasts a minute of play, not a minute of pause.
                if (play.elapsed >= 60.seconds) app.scenes.replace(gameOver)
            }
        }
        // end snippet
    }
}
