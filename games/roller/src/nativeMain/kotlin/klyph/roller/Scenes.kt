package klyph.roller

import klyph.app.Phase
import klyph.scene.Scene
import klyph.scene.buildScene

/**
 * The five screens of the game and the ways between them:
 *
 * - [title]: the name, the hero and how to start, over the level drifting past. A jump, Enter or
 *   Start replaces it with [play]; Escape quits.
 * - [play]: a run of the level. Escape, P, Start or the window losing focus pushes [pause]; the
 *   hero reaching the sign pushes [complete], losing the last heart pushes [gameOver].
 * - [pause]: over the frozen run. Escape, P or Start pops it.
 * - [complete]: over the frozen run, the tally. After a moment a jump replaces everything with
 *   a new [play], Escape with [title].
 * - [gameOver]: over the frozen run, the score. The same ways on as [complete].
 *
 * Only the top scene's systems run, and the engine freezes the particles, fades and colliders of
 * the entities [play] owns while a screen is over it, so the run stands still with no paused flag,
 * and its clock, which [play]'s systems run, stops too. The hero, enemies, pickups and effects
 * are spawned by [play] and its systems, so they belong to it and vanish when it leaves the
 * stack; the buildings and billboards belong to no scene and stay for the title screen. The
 * scenes refer to each other, so they are built together.
 */
class GameScenes {
    val title: Scene = buildScene("title") {
        onEnter { app ->
            app.game.startMusic(app)
            app.audio.music.volume = Tuning.MUSIC_VOLUME
        }
        addSystem(Phase.INPUT) { app ->
            when {
                isEscapePressed(app.input) -> app.quit()
                // The autopilot presses through to the game on its first frame here.
                app.game.isAutopilot || isStartPressed(app) -> app.scenes.replace(play)
            }
        }
        addSystem(Phase.LATE_UPDATE) { app -> panTitleCamera(app) }
        addSystem(Phase.RENDER) { app -> drawTitle(app) }
    }

    val play: Scene = buildScene("play") {
        onEnter { app -> startRun(app) }

        addSystem(Phase.INPUT) { app -> if (isPauseRequested(app)) app.scenes.push(pause) }

        addSystem(Phase.FIXED_UPDATE) { app -> if (app.game.isAutopilot) app.run.steerAutopilot(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> app.run.tickClock(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> app.run.movePlayer(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> moveEnemies(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> tumble(app) }

        addSystem(Phase.UPDATE) { app -> animatePickups(app) }
        addSystem(Phase.UPDATE) { app -> app.run.resolveOverlaps(app) }
        addSystem(Phase.UPDATE) { app -> app.run.checkGoal(app) }
        addSystem(Phase.UPDATE) { app -> app.run.animatePlayer(app) }
        addSystem(Phase.UPDATE) { app ->
            when (app.run.outcomeOrNull) {
                RunOutcome.COMPLETE -> app.scenes.push(complete)
                RunOutcome.GAME_OVER -> app.scenes.push(gameOver)
                null -> {}
            }
        }

        addSystem(Phase.LATE_UPDATE) { app -> app.run.followCamera(app) }

        addSystem(Phase.RENDER) { app -> app.run.drawHud(app) }
    }

    val pause: Scene = buildScene("pause") {
        onEnter { app ->
            // The music ducks instead of stopping, so resuming does not restart it.
            app.audio.music.volume = Tuning.PAUSED_MUSIC_VOLUME
            println("Paused.")
        }
        onExit { app ->
            app.audio.music.volume = Tuning.MUSIC_VOLUME
            println("Resumed.")
        }
        addSystem(Phase.INPUT) { app -> if (isPausePressed(app.input)) app.scenes.pop() }
        addSystem(Phase.RENDER) { app -> drawPause(app) }
    }

    val complete: Scene = buildScene("complete") {
        onEnter { app -> app.game.endedAt = app.time.elapsed }
        addSystem(Phase.INPUT) { app -> leaveEndScreen(app) }
        addSystem(Phase.RENDER) { app -> drawComplete(app) }
    }

    val gameOver: Scene = buildScene("gameOver") {
        onEnter { app ->
            val game = app.game
            game.endedAt = app.time.elapsed
            game.gameOvers++
            game.bestScore = maxOf(game.bestScore, app.run.score)
            app.audio.music.volume = Tuning.END_MUSIC_VOLUME
            app.audio.play(game.sounds.gameOver)
            println("Game over: score ${app.run.score}, ${app.run.rings} rings, ${app.run.stomps} stomps.")
        }
        addSystem(Phase.INPUT) { app -> leaveEndScreen(app) }
        addSystem(Phase.RENDER) { app -> drawGameOver(app) }
    }

    /** End screen INPUT: after a moment, a jump plays again and Escape goes back to the title. */
    private fun leaveEndScreen(app: klyph.app.App) {
        val game = app.game
        val waited = app.time.elapsed - game.endedAt
        when {
            waited < Tuning.END_INPUT_DELAY -> {}
            isEscapePressed(app.input) -> app.scenes.replace(title)
            isStartPressed(app) -> app.scenes.replace(play)
            game.isAutopilot && waited >= Tuning.AUTOPILOT_RETRY_DELAY -> app.scenes.replace(play)
        }
    }

    override fun toString(): String = "GameScenes(title=$title, play=$play, pause=$pause, complete=$complete, gameOver=$gameOver)"
}
