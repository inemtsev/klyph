package klyph.shooter

import klyph.app.App
import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.platform.GpuValidation
import klyph.platform.Sdl
import kotlin.time.Duration.Companion.seconds

/**
 * The reference top-down shooter. Fly with WASD, the arrow keys or the left stick; aim with the
 * mouse or the right stick; fire with Space, the left mouse button, the gamepad's south button or
 * the right trigger; quit with Escape. The game pauses while the window is in the background.
 * Enemies drift down from the top of an arena larger than the window, and the camera follows the
 * ship. Score and lives are printed to the terminal until the engine draws text.
 *
 * Environment: `KLYPH_FRAMES=<n>` quits after n frames, `KLYPH_SECONDS=<s>` once `Time.elapsed`
 * reaches s seconds, `KLYPH_VALIDATION=1` turns on the GPU validation layer, `KLYPH_AUTOPILOT=1`
 * lets the game play itself (for demos and smoke runs) and fails at exit if it destroyed no enemy.
 */
fun main() {
    val validation = if (Sdl.environmentVariable("KLYPH_VALIDATION") == "1") GpuValidation.ENABLED else GpuValidation.DISABLED
    val frameLimit = Sdl.environmentVariable("KLYPH_FRAMES")?.toIntOrNull()
    val timeLimit = Sdl.environmentVariable("KLYPH_SECONDS")?.toDoubleOrNull()?.seconds
    val isAutopilot = Sdl.environmentVariable("KLYPH_AUTOPILOT") == "1"
    val config = AppConfig(
        title = "klyph shooter",
        width = Tuning.WINDOW_WIDTH,
        height = Tuning.WINDOW_HEIGHT,
        clearColor = Tuning.SPACE_COLOR,
        validation = validation,
    )

    runApp(config) {
        addSystem(Phase.STARTUP) { app -> startGame(app, isAutopilot) }

        addSystem(Phase.INPUT) { app -> app.game.readInput(app) }

        addGameplaySystem(Phase.FIXED_UPDATE) { app -> updatePlayer(app) }
        addGameplaySystem(Phase.FIXED_UPDATE) { app -> spawnEnemies(app) }
        addGameplaySystem(Phase.FIXED_UPDATE) { app -> moveBodies(app) }
        addGameplaySystem(Phase.FIXED_UPDATE) { app -> collide(app) }
        addGameplaySystem(Phase.FIXED_UPDATE) { app -> expire(app) }

        addSystem(Phase.UPDATE) { app -> removeFinishedExplosions(app) }
        addSystem(Phase.UPDATE) { app -> app.game.blinkPlayer(app) }
        addSystem(Phase.UPDATE) { app ->
            // time.frame counts completed frames, so this frame is number frame + 1.
            if (frameLimit != null && app.time.frame + 1 >= frameLimit) app.quit()
            if (timeLimit != null && app.time.elapsed >= timeLimit) app.quit()
        }

        addSystem(Phase.LATE_UPDATE) { app -> app.game.followCamera(app) }

        addSystem(Phase.SHUTDOWN) { app ->
            val game = app.game
            println(
                "Shooter ran ${app.time.frame} frames: score ${game.score}, ${game.kills} enemies destroyed, " +
                    "${game.deaths} ships lost, autopilot ${if (isAutopilot) "on" else "off"}.",
            )
            if (isAutopilot) check(game.kills > 0) { "Autopilot destroyed no enemy in ${app.time.frame} frames." }
        }
    }
}

/** Adds [system] to [phase], skipped while the game is paused. */
private fun App.addGameplaySystem(phase: Phase, system: Game.(App) -> Unit) {
    addSystem(phase) { app ->
        val game = app.game
        if (!game.isPaused) game.system(app)
    }
}
