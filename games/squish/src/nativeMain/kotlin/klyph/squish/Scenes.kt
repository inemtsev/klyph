package klyph.squish

import klyph.app.Phase
import klyph.scene.Scene
import klyph.scene.buildScene

/**
 * The four screens of the game and the ways between them:
 *
 * - [title]: the name, bugs walking round the picnic, how to start and the best score. A swat
 *   (click, Space, Enter, South) or Start replaces it with [play]; Escape quits.
 * - [play]: a round. Escape, P, Start or the window losing focus pushes [pause]; the time running
 *   out or the last bite of food pushes [end].
 * - [pause]: over the frozen round. Escape, P, Start or a swat pops it.
 * - [end]: over the frozen round, the score and the medals. After a moment a swat or Start
 *   replaces everything with a new [play], Escape with [title].
 *
 * Only the top scene's systems run, and the engine freezes the particles, fades and colliders of
 * the entities [play] owns while [pause] or [end] is over it, so the round stands still with no paused
 * flag, and its clock, which [play]'s systems run, stops too. The food, bugs, splats and bursts
 * are spawned by [play] and its systems, so they belong to it and vanish when it leaves the
 * stack. The scenes refer to each other, so they are built together.
 */
class GameScenes {
    val title: Scene = buildScene("title") {
        onEnter { app ->
            val game = app.game
            app.commands.spawnPicnic(game.art)
            repeat(Tuning.TITLE_BUGS) { app.commands.spawnTitleBug(game.art, game.random, it) }
            game.startMusic(app)
            app.audio.music.volume = 1f
        }
        addSystem(Phase.INPUT) { app ->
            when {
                isEscapePressed(app.input) -> app.quit()
                // The autopilot presses through to the game on its first frame here.
                app.game.isAutopilot || app.game.controls.isSwatPressed || isStartPressed(app.input) -> app.scenes.replace(play)
            }
        }
        addSystem(Phase.FIXED_UPDATE) { app -> orbitBugs(app) }
        addSystem(Phase.RENDER) { app -> drawTitle(app) }
    }

    val play: Scene = buildScene("play") {
        onEnter { app -> startRound(app) }

        addSystem(Phase.INPUT) { app ->
            if (isPauseRequested(app)) app.scenes.push(pause)
            if (app.game.isAutopilot) steerAutopilot(app)
        }

        addSystem(Phase.FIXED_UPDATE) { app -> app.round.tickClock(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> app.round.spawnBugs(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> app.round.walkBugs(app) }

        addSystem(Phase.UPDATE) { app -> app.round.swat(app) }
        addSystem(Phase.UPDATE) { app -> app.round.feedBugs(app) }
        addSystem(Phase.UPDATE) { app -> drift(app) }
        addSystem(Phase.UPDATE) { app -> if (app.round.finishIfOver()) app.scenes.push(end) }

        addSystem(Phase.RENDER) { app -> app.round.drawHud(app) }
    }

    val pause: Scene = buildScene("pause") {
        onEnter { app ->
            // The music ducks instead of stopping, so resuming does not restart it.
            app.audio.music.volume = Tuning.PAUSED_MUSIC_VOLUME
            println("Paused.")
        }
        onExit { app ->
            app.audio.music.volume = 1f
            println("Resumed.")
        }
        addSystem(Phase.INPUT) { app ->
            if (isPausePressed(app.input) || app.game.controls.isSwatPressed) app.scenes.pop()
        }
        addSystem(Phase.RENDER) { app -> drawPause(app) }
    }

    val end: Scene = buildScene("end") {
        onEnter { app ->
            val game = app.game
            game.endedAt = app.time.elapsed
            app.audio.music.volume = Tuning.END_MUSIC_VOLUME
            app.audio.play(game.sounds.end)
        }
        addSystem(Phase.INPUT) { app ->
            val game = app.game
            val waited = app.time.elapsed - game.endedAt
            when {
                waited < Tuning.END_INPUT_DELAY -> {}
                isEscapePressed(app.input) -> app.scenes.replace(title)
                game.controls.isSwatPressed || isStartPressed(app.input) -> app.scenes.replace(play)
                game.isAutopilot && waited >= Tuning.AUTOPILOT_RETRY_DELAY -> app.scenes.replace(play)
            }
        }
        addSystem(Phase.RENDER) { app -> drawEnd(app) }
    }

    override fun toString(): String = "GameScenes(title=$title, play=$play, pause=$pause, end=$end)"
}
