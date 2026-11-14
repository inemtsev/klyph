// Snippets of docs/sprites-and-animation.md. They compile with the samples but never run, because
// they need a window and a GPU device. Each function body is code for the setup block of runApp.
package samples.guide

import klyph.app.App
import klyph.app.Phase
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.input.axis
import klyph.math.Rect
import klyph.math.Vec2
import klyph.platform.Color
import klyph.platform.Image
import klyph.platform.SamplerFilter
import klyph.platform.Scancode
import klyph.platform.withAlpha
import klyph.render.AnimationEnd
import klyph.render.AnimationFinished
import klyph.render.AnimationMode
import klyph.render.Sprite
import klyph.render.SpriteAnimation
import klyph.render.SpriteSheet
import klyph.render.asRegion
import klyph.render.follow
import klyph.render.fromGrid
import klyph.render.lookAt
import klyph.render.region
import klyph.render.speed
import klyph.render.worldToScreen
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

class SpritesAndAnimation {
    fun App.spawnShip() {
        // snippet: sprite
        val ship = assets.texture("ship.png")
        commands.spawn {
            add(Transform, Transform(x = 640f, y = 360f))
            add(Sprite, Sprite(ship.asRegion()))
        }
        // end snippet
    }

    fun App.spawnShadow() {
        // snippet: sprite-options
        val ship = assets.texture("ship.png").asRegion()
        commands.spawn {
            add(Transform, Transform(x = 646f, y = 368f, rotation = 0.3f, scaleX = 0.5f, scaleY = 0.5f))
            add(Sprite, Sprite(ship, color = Color.BLACK.withAlpha(0.4f), layer = -1, isFlippedX = true))
        }
        // end snippet
    }

    fun App.spawnStandingHero() {
        // snippet: origin
        val hero = assets.texture("hero.png").asRegion()
        commands.spawn {
            // The origin (0.5, 1) is the middle of the bottom edge, so the Transform is at the hero's feet.
            add(Transform, Transform(x = 640f, y = 500f))
            add(Sprite, Sprite(hero, originX = 0.5f, originY = 1f))
        }
        // end snippet
    }

    fun App.cutRegions() {
        // snippet: region
        val tiles = assets.texture("tiles.png")
        val grass = tiles.region(x = 0, y = 0, width = 32, height = 32)
        val wall = tiles.region(x = 32, y = 0, width = 32, height = 32)
        commands.spawn {
            add(Transform, Transform(x = 16f, y = 16f))
            add(Sprite, Sprite(grass))
        }
        commands.spawn {
            add(Transform, Transform(x = 48f, y = 16f))
            add(Sprite, Sprite(wall, layer = 1))
        }
        // end snippet
    }

    fun App.loadSheets() {
        // snippet: sheet
        // Six frames of 128 by 128 texels side by side.
        val explosion = assets.spriteSheet("explosion.png", columns = 6, rows = 1)
        // A tile set with one texel of border and two texels between its cells.
        val tiles = SpriteSheet.fromGrid(assets.texture("tiles.png"), columns = 8, rows = 8, margin = 1, spacing = 2)
        println("${explosion.size} explosion frames; tile 9 is ${tiles[9]}")
        // end snippet
    }

    fun App.spawnCoin(coin: SpriteSheet) {
        // snippet: loop
        commands.spawn {
            add(Transform, Transform(x = 100f, y = 100f))
            add(Sprite, Sprite(coin[0]))
            add(SpriteAnimation, SpriteAnimation(coin, frameDuration = 80.milliseconds))
        }
        // end snippet
    }

    fun App.spawnExplosion(explosion: SpriteSheet, x: Float, y: Float) {
        // snippet: once
        commands.spawn {
            add(Transform, Transform(x = x, y = y))
            add(Sprite, Sprite(explosion[0]))
            add(SpriteAnimation, SpriteAnimation(explosion, 60.milliseconds, AnimationMode.ONCE, AnimationEnd.DESTROY))
        }
        // end snippet
    }

    fun App.countExplosions() {
        // snippet: finished
        var finished = 0
        addSystem(Phase.LATE_UPDATE) { app ->
            app.events.forEach<AnimationFinished> { event ->
                finished++
                // With AnimationEnd.DESTROY the entity is already gone.
                println("Explosion $finished done, alive: ${app.world.isAlive(event.entity)}")
            }
        }
        // end snippet
    }

    fun App.pauseWhenIdle(player: Entity) {
        // snippet: playhead
        addSystem(Phase.UPDATE) { app ->
            val walk = app.world.get(player, SpriteAnimation).playhead
            val isMoving = app.input.keyboard.axis(Scancode.A, Scancode.D) != 0f
            walk.isPaused = !isMoving
            if (!isMoving) walk.seek(0)
        }
        // end snippet
    }

    fun App.matchRunToPace(player: Entity) {
        // snippet: speed
        addSystem(Phase.UPDATE) { app ->
            // The legs keep up with the ground: half rate at a standstill, one and a half at full pace.
            val run = app.world.get(player, SpriteAnimation)
            val pace = abs(app.input.keyboard.axis(Scancode.A, Scancode.D))
            run.speed = 0.5f + pace
        }
        // end snippet
    }

    fun App.setUpCamera(player: Entity) {
        // snippet: camera
        // Show the world twice as large and never past the level's edges.
        camera.zoom = 2f
        camera.bounds = Rect(x = 0f, y = 0f, width = 4000f, height = 3000f)
        camera.lookAt(2000f, 1500f)

        addSystem(Phase.LATE_UPDATE) { app ->
            val target = app.world.get(player, Transform)
            app.camera.follow(target.x, target.y, smoothing = 5f, delta = app.time.delta)
        }
        // end snippet
    }

    fun App.labelPlayer(player: Entity) {
        // snippet: world-to-screen
        addSystem(Phase.RENDER) { app ->
            val target = app.world.get(player, Transform)
            val above = app.camera.worldToScreen(Vec2(target.x, target.y - 40f))
            app.canvas.drawText(app.assets.defaultFont, "P1", x = above.x, y = above.y, scale = 2f)
        }
        // end snippet
    }

    fun App.loadPixelArt() {
        // snippet: filter
        val hero = assets.spriteSheet("hero.png", columns = 4, rows = 1, filter = SamplerFilter.NEAREST)
        camera.zoom = 4f
        commands.spawn {
            add(Transform, Transform(x = 40f, y = 30f))
            add(Sprite, Sprite(hero[0]))
        }
        // end snippet
    }

    fun App.drawWithoutFiles() {
        // snippet: generated
        val checker = Image(2, 2).apply {
            set(0, 0, Color.WHITE)
            set(1, 1, Color.WHITE)
        }
        val board = assets.createTexture(checker, SamplerFilter.NEAREST).asRegion()
        commands.spawn {
            add(Transform, Transform(x = 200f, y = 200f))
            add(Sprite, Sprite(board, width = 64f, height = 64f))
        }
        commands.spawn {
            add(Transform, Transform(x = 400f, y = 200f))
            add(Sprite, Sprite(assets.disc(24), color = Color(1f, 0.8f, 0.2f)))
        }
        commands.spawn {
            add(Transform, Transform(x = 640f, y = 700f))
            add(Sprite, Sprite(assets.whitePixel, width = 1280f, height = 40f, color = Color(0.2f, 0.6f, 0.3f)))
        }
        // end snippet
    }
}
