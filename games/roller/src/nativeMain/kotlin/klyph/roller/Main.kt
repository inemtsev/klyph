package klyph.roller

import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.platform.Sdl

/**
 * Rooftop Roller, a side-scrolling platformer of one level, Rooftop Run. A ninja on rollerblades
 * rolls across the roofs of a red brick city: he builds up speed and glides when the key is let
 * go, jumps higher the longer the button is held, and jumps the gaps between the buildings. Gold
 * rings are worth 10 points, stomping a blob or a helicopter 150; running into one costs one of
 * three hearts and so does falling into a gap, which puts him back on the last roof. The arrow
 * sign at the end completes the level with a bonus for the hearts kept and the time.
 *
 * A/D, the arrows, a left stick or the d-pad roll; Space, W, Up or the south button jump; Escape,
 * P or Start pauses, and so does the window going to the background. F11 switches to fullscreen
 * and back, F3 shows the engine's debug overlay, and Escape on the title screen quits.
 *
 * The screens are the scenes of [GameScenes]. Only what runs on every screen is added to the app
 * here: loading and the scenery, the controls, the camera's zoom, the sky and the line printed at
 * exit.
 *
 * Environment: the `KLYPH_*` variables `AppConfig.fromEnvironment` reads, among them
 * `KLYPH_FRAMES=<n>`, `KLYPH_SECONDS=<s>`, `KLYPH_VALIDATION=1` and
 * `KLYPH_SCREENSHOT=<frame>:<path>`, and `KLYPH_AUTOPILOT=1`, which lets the game play itself
 * from the title screen on (for demos and smoke runs) and fails at exit if it took no ring and
 * stomped no enemy.
 */
fun main() {
    val isAutopilot = Sdl.environmentVariable("KLYPH_AUTOPILOT") == "1"
    val config = AppConfig.fromEnvironment(
        AppConfig(
            title = "klyph roller",
            width = Tuning.WINDOW_WIDTH,
            height = Tuning.WINDOW_HEIGHT,
            clearColor = Tuning.SKY_BOTTOM,
        ),
    )
    val scenes = GameScenes()

    runApp(config) {
        addSystem(Phase.STARTUP) { app ->
            startGame(app, isAutopilot)
            app.scenes.replace(scenes.title)
        }

        addSystem(Phase.INPUT) { app -> readControls(app) }
        addSystem(Phase.INPUT) { app -> toggleFullscreen(app) }

        addSystem(Phase.LATE_UPDATE) { app -> fitCamera(app) }

        addSystem(Phase.RENDER) { app -> drawBackdrop(app) }

        addSystem(Phase.SHUTDOWN) { app ->
            val game = app.game
            // The last run's Run stays a resource after the play scene leaves the stack.
            val run = app.resources.getOrNull<Run>()
            println(
                "Roller ran ${app.time.frameCount} frames: score ${run?.score ?: 0}, best ${game.bestScore}, " +
                    "${game.totalRings} rings, ${game.totalStomps} stomps, ${game.levelsCompleted} levels completed, " +
                    "${game.gameOvers} game overs, autopilot ${if (isAutopilot) "on" else "off"}.",
            )
            if (isAutopilot) {
                check(game.totalRings > 0 || game.totalStomps > 0) {
                    "Autopilot took no ring and stomped no enemy in ${app.time.frameCount} frames."
                }
            }
        }
    }
}
