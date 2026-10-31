package klyph.sandbox

import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.event.KeyPressed
import klyph.platform.GpuValidation
import klyph.platform.KeyCode
import klyph.platform.Sdl

/**
 * Sandbox entry point: a smoke test that runs an empty klyph app, which clears the window every
 * frame, until the window closes, escape is pressed, or `KLYPH_FRAMES` frames have run.
 *
 * Environment: `KLYPH_FRAMES` frames before exit, `KLYPH_VALIDATION=1` for the GPU validation layer.
 */
fun main() {
    val validation = if (Sdl.environmentVariable("KLYPH_VALIDATION") == "1") GpuValidation.ENABLED else GpuValidation.DISABLED
    val frameLimit = Sdl.environmentVariable("KLYPH_FRAMES")?.toIntOrNull()

    runApp(AppConfig(title = "klyph sandbox", validation = validation)) {
        addSystem(Phase.STARTUP) { app ->
            println("GPU driver: ${app.device.driverName}, validation: $validation")
        }
        addSystem(Phase.UPDATE) { app ->
            app.events.forEach<KeyPressed> { if (it.key == KeyCode.ESCAPE) app.quit() }
            // time.frame counts completed frames, so this frame is number frame + 1.
            if (frameLimit != null && app.time.frame + 1 >= frameLimit) app.quit()
        }
        addSystem(Phase.SHUTDOWN) { app ->
            println("Ran ${app.time.frame} frames.")
        }
    }
}
