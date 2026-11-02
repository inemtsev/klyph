package klyph.squish

import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.platform.GpuValidation
import klyph.platform.Sdl
import kotlin.time.Duration.Companion.seconds

/**
 * Bug Squish, a game for young children. Bugs crawl over a picnic blanket toward a cake and two
 * slices of watermelon; squish them with the fly swatter before they take a bite. The mouse moves
 * the swatter and a click (or a tap) swats; the arrow keys, WASD, a gamepad's left stick or d-pad
 * move it too, and Space, Enter or the south button swat. A round lasts a minute or until the
 * food is gone, and bugs come faster and more often as it goes on. Escape, P or Start pauses, and
 * so does the window going to the background. F3 shows the engine's debug overlay; Escape on the
 * title screen quits.
 *
 * The screens are the scenes of [GameScenes]. Only what runs on every screen is added to the app
 * here: loading, the blanket and the swatter, the camera, the run limits and the line printed at
 * exit.
 *
 * Environment: `KLYPH_FRAMES=<n>` quits after n frames, `KLYPH_SECONDS=<s>` once `Time.elapsed`
 * reaches s seconds, `KLYPH_VALIDATION=1` turns on the GPU validation layer, `KLYPH_AUTOPILOT=1`
 * lets the game play itself from the title screen on (for demos and smoke runs) and fails at exit
 * if it squished no bug.
 */
fun main() {
    val validation = if (Sdl.environmentVariable("KLYPH_VALIDATION") == "1") GpuValidation.ENABLED else GpuValidation.DISABLED
    val frameLimit = Sdl.environmentVariable("KLYPH_FRAMES")?.toIntOrNull()
    val timeLimit = Sdl.environmentVariable("KLYPH_SECONDS")?.toDoubleOrNull()?.seconds
    val isAutopilot = Sdl.environmentVariable("KLYPH_AUTOPILOT") == "1"
    val config = AppConfig(
        title = "klyph squish",
        width = Tuning.WINDOW_WIDTH,
        height = Tuning.WINDOW_HEIGHT,
        clearColor = Tuning.GRASS_COLOR,
        validation = validation,
    )
    val scenes = GameScenes()

    runApp(config) {
        addSystem(Phase.STARTUP) { app ->
            startGame(app, isAutopilot)
            app.scenes.replace(scenes.title)
        }

        addSystem(Phase.INPUT) { app -> readControls(app) }

        addSystem(Phase.UPDATE) { app ->
            settleSwatter(app)
            // time.frame counts completed frames, so this frame is number frame + 1.
            if (frameLimit != null && app.time.frame + 1 >= frameLimit) app.quit()
            if (timeLimit != null && app.time.elapsed >= timeLimit) app.quit()
        }

        addSystem(Phase.LATE_UPDATE) { app -> fitCamera(app) }

        addSystem(Phase.RENDER) { app -> drawBlanket(app) }
        addSystem(Phase.RENDER) { app -> drawSwatter(app) }

        addSystem(Phase.SHUTDOWN) { app ->
            val game = app.game
            // The last round's Round stays a resource after the play scene leaves the stack.
            val score = app.resources.getOrNull<Round>()?.score ?: 0
            println(
                "Squish ran ${app.time.frame} frames: score $score, best ${game.bestScore}, ${game.totalSquished} bugs squished, " +
                    "${game.roundsPlayed} rounds finished, autopilot ${if (isAutopilot) "on" else "off"}.",
            )
            if (isAutopilot) check(game.totalSquished > 0) { "Autopilot squished no bug in ${app.time.frame} frames." }
        }
    }
}
