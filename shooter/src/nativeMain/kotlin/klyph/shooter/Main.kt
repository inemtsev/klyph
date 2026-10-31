package klyph.shooter

import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.platform.GpuValidation
import klyph.platform.Sdl

/**
 * The reference top-down shooter: fly with WASD or the arrow keys, fire with Space, quit with
 * Escape. Enemies drift down from the top of an arena larger than the window, and the camera
 * follows the ship. Score and lives are printed to the terminal until the engine draws text.
 *
 * Environment: `KLYPH_FRAMES=<n>` quits after n frames, `KLYPH_VALIDATION=1` turns on the GPU
 * validation layer, `KLYPH_AUTOPILOT=1` lets the game play itself (for demos and smoke runs) and
 * fails at exit if it destroyed no enemy.
 */
fun main() {
    val validation = if (Sdl.environmentVariable("KLYPH_VALIDATION") == "1") GpuValidation.ENABLED else GpuValidation.DISABLED
    val frameLimit = Sdl.environmentVariable("KLYPH_FRAMES")?.toIntOrNull()
    val isAutopilot = Sdl.environmentVariable("KLYPH_AUTOPILOT") == "1"
    val config = AppConfig(
        title = "klyph shooter",
        width = Tuning.WINDOW_WIDTH,
        height = Tuning.WINDOW_HEIGHT,
        clearColor = Tuning.SPACE_COLOR,
        validation = validation,
    )

    runApp(config) {
        val game = Game(Art.load(assets), isAutopilot)

        addSystem(Phase.STARTUP) { app -> game.start(app) }

        addSystem(Phase.INPUT) { app -> game.readInput(app) }

        addSystem(Phase.FIXED_UPDATE) { app -> game.updatePlayer(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> game.spawnEnemies(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> moveBodies(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> game.collide(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> game.expire(app) }

        addSystem(Phase.UPDATE) { app -> game.removeFinishedExplosions(app) }
        addSystem(Phase.UPDATE) { app -> game.blinkPlayer(app) }
        addSystem(Phase.UPDATE) { app ->
            // time.frame counts completed frames, so this frame is number frame + 1.
            if (frameLimit != null && app.time.frame + 1 >= frameLimit) app.quit()
        }

        addSystem(Phase.LATE_UPDATE) { app -> game.followCamera(app) }

        addSystem(Phase.SHUTDOWN) { app ->
            println(
                "Shooter ran ${app.time.frame} frames: score ${game.score}, ${game.kills} enemies destroyed, " +
                    "${game.deaths} ships lost, autopilot ${if (isAutopilot) "on" else "off"}.",
            )
            if (isAutopilot) check(game.kills > 0) { "Autopilot destroyed no enemy in ${app.time.frame} frames." }
        }
    }
}
