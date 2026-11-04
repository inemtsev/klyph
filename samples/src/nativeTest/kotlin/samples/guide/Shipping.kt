// Snippets of docs/shipping.md. They compile with the samples but never run, because they open a
// window.
package samples.guide

import klyph.app.App
import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.Screenshot
import klyph.app.runApp
import klyph.platform.Color
import klyph.platform.CursorMode
import klyph.platform.Image
import klyph.platform.Scancode
import klyph.platform.WindowMode

class Shipping {
    // Needs a window: never called.
    fun main() {
        // snippet: from-environment
        val config = AppConfig.fromEnvironment(
            AppConfig(title = "My game", width = 1280, height = 720, cursor = CursorMode.HIDDEN),
        )
        runApp(config) {
            // The game itself.
        }
        // end snippet
    }

    // Needs a window: never called.
    fun screenshotFromCode() {
        // snippet: screenshot-config
        runApp(AppConfig(title = "My game", screenshot = Screenshot(frame = 60, path = "frame-60.png"), frameLimit = 61)) {
            // The game itself.
        }
        // end snippet
    }

    fun App.screenshotKey() {
        // snippet: capture
        addSystem(Phase.INPUT) { app ->
            if (app.input.keyboard.isJustPressed(Scancode.F12)) {
                // Written next to the executable once this frame is drawn.
                app.window.capture("screenshot-${app.time.frameCount}.png")
            }
        }
        // end snippet
    }

    fun App.windowKeys() {
        // snippet: window
        addSystem(Phase.INPUT) { app ->
            val window = app.window
            if (app.input.keyboard.isJustPressed(Scancode.F11)) {
                window.mode = if (window.mode == WindowMode.FULLSCREEN) WindowMode.WINDOWED else WindowMode.FULLSCREEN
            }
            if (app.input.keyboard.isJustPressed(Scancode.C)) {
                window.cursor = if (window.cursor == CursorMode.VISIBLE) CursorMode.HIDDEN else CursorMode.VISIBLE
            }
        }
        // end snippet
    }

    fun App.crosshair() {
        // snippet: cursor-image
        val image = Image(15, 15)
        for (i in 0 until 15) {
            image[i, 7] = Color.WHITE
            image[7, i] = Color.WHITE
        }
        // The middle pixel is where clicks land.
        window.setCursor(image, hotspotX = 7, hotspotY = 7)
        // end snippet
    }
}
