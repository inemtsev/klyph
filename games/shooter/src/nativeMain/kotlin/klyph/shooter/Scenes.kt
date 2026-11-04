package klyph.shooter

import klyph.app.Phase
import klyph.scene.Scene
import klyph.scene.buildScene

/**
 * The four screens of the game and the ways between them:
 *
 * - [title]: the name, how to start and the best score. Space, a click, Start or South replaces
 *   it with [play]; Escape quits.
 * - [play]: the game. Escape, Start or the window losing focus pushes [pause]; the last life
 *   lost replaces it with [gameOver].
 * - [pause]: over the frozen game. Escape or Start pops it.
 * - [gameOver]: the final and best scores. Space or Start replaces it with [play], Escape with
 *   [title].
 *
 * Only the top scene's systems run, and the engine freezes the exhaust, animations and colliders
 * of the entities [play] owns while [pause] is over it, so the game stands still with no paused
 * flag. The ship, its exhaust, enemies, the boss, bullets and explosions are spawned by [play] and its
 * systems, so they belong to it and vanish when it leaves the stack. The scenes refer to each other, so they are built together.
 */
class GameScenes {
    val title: Scene = buildScene("title") {
        onEnter { app ->
            showArena(app)
            app.game.startMusic(app)
        }
        addSystem(Phase.INPUT) { app ->
            when {
                isEscapePressed(app.input) -> app.quit()
                // The autopilot presses through to the game on its first frame here.
                app.game.isAutopilot || isStartPressed(app.input) -> app.scenes.replace(play)
            }
        }
        addSystem(Phase.RENDER) { app -> drawTitle(app) }
    }

    val play: Scene = buildScene("play") {
        onEnter { app -> startRound(app) }

        addSystem(Phase.INPUT) { app ->
            if (isPauseRequested(app)) app.scenes.push(pause)
            app.round.readControls(app)
        }

        addSystem(Phase.FIXED_UPDATE) { app -> app.round.updatePlayer(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> app.round.spawnEnemies(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> steerDivers(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> moveBodies(app) }
        addSystem(Phase.FIXED_UPDATE) { app -> app.round.removeStrays(app) }

        addSystem(Phase.UPDATE) { app -> app.round.resolveOverlaps(app) }
        addSystem(Phase.UPDATE) { app -> app.round.updateBoss(app) }
        addSystem(Phase.UPDATE) { app -> app.round.blinkPlayer(app) }
        addSystem(Phase.UPDATE) { app -> app.round.runThruster(app) }
        addSystem(Phase.UPDATE) { app -> app.wave.runBanner(app) }
        addSystem(Phase.UPDATE) { app -> if (app.round.isOver) app.scenes.replace(gameOver) }

        addSystem(Phase.LATE_UPDATE) { app -> app.round.followCamera(app) }

        addSystem(Phase.RENDER) { app -> drawBossBar(app) }
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
        addSystem(Phase.INPUT) { app -> if (isPausePressed(app.input)) app.scenes.pop() }
        addSystem(Phase.RENDER) { app -> drawPause(app) }
    }

    val gameOver: Scene = buildScene("gameOver") {
        onEnter { app -> app.game.gameOverAt = app.time.elapsed }
        addSystem(Phase.INPUT) { app ->
            val game = app.game
            when {
                isEscapePressed(app.input) -> app.scenes.replace(title)
                isRetryPressed(app.input) -> app.scenes.replace(play)
                game.isAutopilot && app.time.elapsed - game.gameOverAt >= Tuning.AUTOPILOT_RETRY_DELAY -> app.scenes.replace(play)
            }
        }
        addSystem(Phase.RENDER) { app -> drawGameOver(app) }
    }

    override fun toString(): String = "GameScenes(title=$title, play=$play, pause=$pause, gameOver=$gameOver)"
}
