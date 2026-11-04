package klyph.shooter

import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.platform.Sdl

/**
 * The reference top-down shooter. It opens on a title screen; Space, a click or the gamepad's
 * Start or south button starts a game. Fly with WASD, the arrow keys or the left stick; aim with
 * the mouse or the right stick; fire with Space, the left mouse button, the gamepad's south button
 * or the right trigger. Escape or Start pauses, and so does the window going to the background.
 * Enemies come in waves into an arena larger than the window, and the camera follows the ship:
 * drifters sway down from the top, divers drop in from the top of the view and swoop through
 * where the ship was, and every fifth wave is a boss with 12 hit points that sweeps the top of
 * the arena and fires back. Score, lives and the wave show in the top-left corner; losing the
 * last of three lives leads to the game over screen. Shots, explosions, hits and game over have sounds, and a music track loops,
 * turned down while paused. F3 shows the engine's debug overlay; Escape on the title screen quits.
 *
 * The screens are the scenes of [GameScenes]. Only what runs on every screen is added to the app
 * here: loading and the line printed at exit.
 *
 * Environment: the `KLYPH_*` variables `AppConfig.fromEnvironment` reads, among them
 * `KLYPH_FRAMES=<n>`, `KLYPH_SECONDS=<s>`, `KLYPH_VALIDATION=1` and
 * `KLYPH_SCREENSHOT=<frame>:<path>`, and `KLYPH_AUTOPILOT=1`, which lets the game play itself
 * from the title screen on (for demos and smoke runs) and fails at exit if it destroyed no enemy.
 */
fun main() {
    val isAutopilot = Sdl.environmentVariable("KLYPH_AUTOPILOT") == "1"
    val config = AppConfig.fromEnvironment(
        AppConfig(
            title = "klyph shooter",
            width = Tuning.WINDOW_WIDTH,
            height = Tuning.WINDOW_HEIGHT,
            clearColor = Tuning.SPACE_COLOR,
        ),
    )
    val scenes = GameScenes()

    runApp(config) {
        addSystem(Phase.STARTUP) { app ->
            startGame(app, isAutopilot)
            app.scenes.replace(scenes.title)
        }

        addSystem(Phase.SHUTDOWN) { app ->
            val game = app.game
            // The last game's Round stays a resource after the play scene leaves the stack.
            val score = app.resources.getOrNull<Round>()?.score ?: 0
            val wave = app.resources.getOrNull<Wave>()?.number ?: 0
            println(
                "Shooter ran ${app.time.frameCount} frames: score $score, wave $wave, best ${game.bestScore}, ${game.kills} enemies destroyed, " +
                    "${game.deaths} ships lost, autopilot ${if (isAutopilot) "on" else "off"}.",
            )
            if (isAutopilot) check(game.kills > 0) { "Autopilot destroyed no enemy in ${app.time.frameCount} frames." }
        }
    }
}
