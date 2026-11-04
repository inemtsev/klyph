// Snippets of docs/audio.md. They compile with the samples but never run, because App.audio exists
// only inside runApp. Each function body is code for the setup block of runApp.
package samples.guide

import klyph.app.App
import klyph.app.AppConfig
import klyph.app.Phase
import klyph.app.runApp
import klyph.audio.AudioConfig
import klyph.audio.AudioOutput
import klyph.audio.PlaybackMode
import klyph.audio.Voice
import klyph.entity.Entity
import klyph.entity.Transform
import klyph.platform.Scancode
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class AudioGuide {
    fun App.playEffects() {
        // snippet: load-play
        val shot = assets.sound("shot.wav")
        addSystem(Phase.UPDATE) { app ->
            if (app.input.keyboard.isJustPressed(Scancode.SPACE)) {
                // A slightly different pitch each time keeps a repeated sound from droning.
                app.audio.play(shot, volume = 0.6f, pitch = 0.9f + 0.2f * Random.nextFloat())
            }
        }
        // end snippet
    }

    fun App.panByPosition(enemy: Entity) {
        // snippet: pan
        val explosion = assets.sound("explosion.wav")
        addSystem(Phase.UPDATE) { app ->
            if (app.input.keyboard.isJustPressed(Scancode.X)) {
                val x = app.world.get(enemy, Transform).x
                // -1 at the window's left edge, 1 at its right edge.
                val pan = (x / app.window.width * 2f - 1f).coerceIn(-1f, 1f)
                app.audio.play(explosion, pan = pan)
            }
        }
        // end snippet
    }

    fun App.engineHum() {
        // snippet: voice
        val hum = assets.sound("engine.wav")
        var engine = Voice.NONE
        addSystem(Phase.UPDATE) { app ->
            val throttle = if (app.input.keyboard.isDown(Scancode.W)) 1f else 0f
            if (throttle > 0f && !app.audio.isPlaying(engine)) {
                engine = app.audio.play(hum, volume = 0.5f, mode = PlaybackMode.LOOP)
            }
            app.audio.setPitch(engine, 1f + throttle)
            if (throttle == 0f) app.audio.stop(engine)
        }
        // end snippet
    }

    fun App.playMusic() {
        // snippet: music
        val theme = assets.musicTrack("music.ogg")
        val boss = assets.musicTrack("boss.ogg")
        audio.music.play(theme, fadeIn = 2.seconds)
        addSystem(Phase.UPDATE) { app ->
            val keyboard = app.input.keyboard
            // Playing another track crosses the two over the same fade.
            if (keyboard.isJustPressed(Scancode.B)) app.audio.music.play(boss, fadeIn = 1.seconds)
            if (keyboard.isJustPressed(Scancode.M)) app.audio.music.stop(fadeOut = 1.seconds)
        }
        // end snippet
    }

    fun App.setVolumes() {
        // snippet: volumes
        audio.masterVolume = 0.8f
        audio.effectsVolume = 1f
        audio.musicVolume = 0.5f
        // On top of musicVolume, for ducking the current track under a pause menu.
        audio.music.volume = 0.3f
        // end snippet
    }

    // Needs an app: never called.
    fun quietRun() {
        // snippet: no-device
        val quiet = AudioConfig(output = AudioOutput.NONE)
        runApp(AppConfig(title = "Headless check", audio = quiet, frameLimit = 120)) {
            addSystem(Phase.STARTUP) { app ->
                // Every audio call does nothing, and play returns Voice.NONE.
                println("Audio on: ${app.audio.isEnabled}")
            }
        }
        // end snippet
    }

    // Needs an app: never called.
    fun moreVoices() {
        // snippet: config
        val audio = AudioConfig(maxVoices = 64, bufferFrames = 512)
        runApp(AppConfig(title = "Busy game", audio = audio)) {
            addSystem(Phase.UPDATE) { app ->
                val stats = app.audio.stats
                if (stats.stolenVoiceCount > 0) println("${stats.activeVoiceCount} voices playing, ${stats.stolenVoiceCount} stolen so far")
            }
        }
        // end snippet
    }
}
