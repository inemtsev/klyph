package klyph.shooter

import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.platform.GpuValidation
import klyph.platform.Sdl
import kotlin.time.Duration.Companion.seconds

/**
 * The reference top-down shooter. It opens on a title screen; Space, a click or the gamepad's
 * Start or south button starts a game. Fly with WASD, the arrow keys or the left stick; aim with
 * the mouse or the right stick; fire with Space, the left mouse button, the gamepad's south button
 * or the right trigger. Escape or Start pauses, and so does the window going to the background.
 * Enemies drift down from the top of an arena larger than the window, and the camera follows the
 * ship. Score and lives show in the top-left corner; losing the last of three lives leads to the
 * game over screen. Shots, explosions, hits and game over have sounds, and a music track loops,
 * turned down while paused. F3 shows the engine's debug overlay; Escape on the title screen quits.
 *
 * The screens are the scenes of [GameScenes]. Only what runs on every screen is added to the app
 * here: loading, the run limits and the line printed at exit.
 *
 * Environment: `KLYPH_FRAMES=<n>` quits after n frames, `KLYPH_SECONDS=<s>` once `Time.elapsed`
 * reaches s seconds, `KLYPH_VALIDATION=1` turns on the GPU validation layer, `KLYPH_AUTOPILOT=1`
 * lets the game play itself from the title screen on (for demos and smoke runs) and fails at exit
 * if it destroyed no enemy.
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
    val scenes = GameScenes()

    runApp(config) {
        addSystem(Phase.STARTUP) { app ->
            startGame(app, isAutopilot)
            app.scenes.replace(scenes.title)
        }

        addSystem(Phase.UPDATE) { app ->
            // time.frame counts completed frames, so this frame is number frame + 1.
            if (frameLimit != null && app.time.frame + 1 >= frameLimit) app.quit()
            if (timeLimit != null && app.time.elapsed >= timeLimit) app.quit()
        }

        addSystem(Phase.SHUTDOWN) { app ->
            val game = app.game
            // The last game's Round stays a resource after the play scene leaves the stack.
            val score = app.resources.getOrNull<Round>()?.score ?: 0
            println(
                "Shooter ran ${app.time.frame} frames: score $score, best ${game.bestScore}, ${game.kills} enemies destroyed, " +
                    "${game.deaths} ships lost, autopilot ${if (isAutopilot) "on" else "off"}.",
            )
            if (isAutopilot) check(game.kills > 0) { "Autopilot destroyed no enemy in ${app.time.frame} frames." }
        }
    }
}
