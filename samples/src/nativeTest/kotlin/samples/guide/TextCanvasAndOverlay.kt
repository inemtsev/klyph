// Snippets of docs/text-canvas-and-overlay.md. They compile with the samples but never run,
// because fonts and the canvas need a window. Each function body is code for runApp's setup block.
package samples.guide

import klyph.app.App
import klyph.app.AppConfig
import klyph.app.OverlayStart
import klyph.app.Phase
import klyph.app.runApp
import klyph.collision.Collider
import klyph.collision.Shape
import klyph.entity.Transform
import klyph.platform.Color
import klyph.platform.SamplerFilter
import klyph.platform.Scancode
import klyph.platform.withAlpha
import klyph.render.DrawSpace
import klyph.text.Text
import klyph.text.TextAlign
import klyph.text.TextShadow
import klyph.text.measure

class TextCanvasAndOverlay {
    fun App.helloText() {
        // snippet: built-in
        addSystem(Phase.RENDER) { app ->
            app.canvas.drawText(app.assets.defaultFont, "Hello, klyph", x = 16f, y = 16f, scale = 3f)
        }
        // end snippet
    }

    fun App.loadFonts() {
        // snippet: bmfont
        // A BMFont text file; its page images sit next to it and load with it.
        val title = assets.font("fonts/title.fnt")
        // A pixel font drawn at whole scales stays crisp with nearest sampling.
        val pixel = assets.font("fonts/pixel.fnt", SamplerFilter.NEAREST)
        println("Line heights: ${title.lineHeight} and ${pixel.lineHeight}")
        // end snippet
    }

    fun App.signpost() {
        // snippet: text-component
        commands.spawn {
            add(Transform, Transform(x = 300f, y = 200f))
            add(
                Text,
                Text(
                    assets.defaultFont,
                    "Shop\nopen",
                    color = Color(1f, 0.9f, 0.5f),
                    scale = 2f,
                    align = TextAlign.CENTER,
                    layer = 5,
                    shadow = TextShadow(offsetX = 2f, offsetY = 2f),
                ),
            )
        }
        // end snippet
    }

    fun App.scoreHud() {
        // snippet: hud
        val font = assets.defaultFont
        val shadow = TextShadow()
        // Refilled every frame, so the changing score allocates no new String.
        val line = StringBuilder()
        var score = 0
        addSystem(Phase.UPDATE) { app -> if (app.input.keyboard.isJustPressed(Scancode.SPACE)) score += 10 }
        addSystem(Phase.RENDER) { app ->
            line.clear()
            line.append("Score ").append(score)
            app.canvas.drawRect(x = 8f, y = 8f, width = 220f, height = 40f, color = Color.BLACK.withAlpha(0.5f))
            app.canvas.drawText(font, line, x = 20f, y = 16f, scale = 3f, layer = 1, shadow = shadow)
        }
        // end snippet
    }

    fun App.gameOverBanner() {
        // snippet: measure
        addSystem(Phase.RENDER) { app ->
            val font = app.assets.defaultFont
            val size = font.measure("GAME OVER", scale = 6f)
            val x = (app.window.width - size.x) / 2f
            val y = (app.window.height - size.y) / 2f
            app.canvas.drawRect(x = x - 16f, y = y - 16f, width = size.x + 32f, height = size.y + 32f, color = Color(0.1f, 0f, 0f, 0.8f))
            app.canvas.drawText(font, "GAME OVER", x = x, y = y, scale = 6f, layer = 1)
        }
        // end snippet
    }

    fun App.drawLives() {
        // snippet: draw-sprite
        val heart = assets.disc(20)
        addSystem(Phase.RENDER) { app ->
            for (i in 0 until 3) {
                // Centered on (x, y) like an entity's sprite.
                app.canvas.drawSprite(heart, x = app.window.width - 24f - 28f * i, y = 24f, color = Color(1f, 0.2f, 0.3f))
            }
        }
        // end snippet
    }

    fun App.debugColliders() {
        // snippet: world-space
        val outline = Color(0f, 1f, 0f, 0.35f)
        addSystem(Phase.RENDER) { app ->
            app.world.forEach(Transform, Collider) { _, transform, collider ->
                val box = collider.shape as? Shape.Box ?: return@forEach
                // drawRect takes the top-left corner; a box collider is centered on its entity.
                app.canvas.drawRect(
                    x = transform.x + collider.offsetX - box.width / 2f,
                    y = transform.y + collider.offsetY - box.height / 2f,
                    width = box.width,
                    height = box.height,
                    color = outline,
                    layer = 100,
                    space = DrawSpace.WORLD,
                )
            }
        }
        // end snippet
    }

    // Needs a window: never called.
    fun overlay() {
        // snippet: overlay
        val config = AppConfig(title = "My game", overlayKey = Scancode.F1, overlay = OverlayStart.VISIBLE)
        runApp(config) {
            addSystem(Phase.UPDATE) { app ->
                // The same numbers the overlay shows, for the previous frame.
                if (app.stats.total.inWholeMilliseconds > 20) println("Slow frame: ${app.stats.durationOf(Phase.UPDATE)} in UPDATE")
                if (app.input.keyboard.isJustPressed(Scancode.F2)) app.overlay.isVisible = !app.overlay.isVisible
            }
        }
        // end snippet
    }
}
