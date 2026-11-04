// Snippets of docs/getting-started.md. They compile with the samples but never run, because
// runApp opens a window. The template's src/nativeMain/kotlin/mygame/Main.kt is snippet "main".
package samples.guide

// snippet: imports
import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.deltaSeconds
import klyph.app.runApp
import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.input.axis
import klyph.platform.Scancode
import klyph.render.Sprite
import klyph.render.asRegion
import kotlin.math.sqrt
// end snippet

// snippet: speed
/** How fast the ship flies, in world units per second. At the camera's default zoom a unit is a point. */
private const val SHIP_SPEED = 200f
// end snippet

class GettingStarted {
    // Needs a window: never called.
    // snippet: first-window
    fun main() {
        runApp(AppConfig(title = "My game", width = 1280, height = 720)) {
            addSystem(Phase.INPUT) { app ->
                if (app.input.keyboard.isJustPressed(Scancode.ESCAPE)) app.quit()
            }
        }
    }
    // end snippet

    inner class Template {
        // Needs a window: never called.
        // snippet: main
        fun main() {
            val config = AppConfig.fromEnvironment(AppConfig(title = "My game", width = 1280, height = 720))
            runApp(config) {
                // World (0, 0) is the window's top-left corner, so this is the middle of the window.
                val ship = world.create()
                world.add(ship, Transform, Transform(x = window.width / 2f, y = window.height / 2f))
                world.add(ship, Sprite, Sprite(assets.texture("ship.png").asRegion()))
                world.add(ship, Interpolated, Interpolated)

                addSystem(Phase.INPUT) { app ->
                    if (app.input.keyboard.isJustPressed(Scancode.ESCAPE)) app.quit()
                }

                addSystem(Phase.FIXED_UPDATE) { app ->
                    val keyboard = app.input.keyboard
                    var dx = keyboard.axis(Scancode.LEFT, Scancode.RIGHT) + keyboard.axis(Scancode.A, Scancode.D)
                    var dy = keyboard.axis(Scancode.UP, Scancode.DOWN) + keyboard.axis(Scancode.W, Scancode.S)
                    val pad = app.input.gamepads.firstOrNull()
                    if (pad != null) {
                        dx += pad.leftStick.x
                        dy += pad.leftStick.y
                    }
                    // Two keys at once, or a key and the stick, must not fly faster than one.
                    val length = sqrt(dx * dx + dy * dy)
                    if (length > 1f) {
                        dx /= length
                        dy /= length
                    }
                    val transform = app.world.get(ship, Transform)
                    transform.x += dx * SHIP_SPEED * app.time.deltaSeconds
                    transform.y += dy * SHIP_SPEED * app.time.deltaSeconds
                }
            }
        }
        // end snippet
    }
}
