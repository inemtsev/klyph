@file:OptIn(KlyphPlatformApi::class) // The samples mix offline through Audio.renderer, which a game never calls.

package samples.audio

import klyph.audio.Audio
import klyph.audio.AudioConfig
import klyph.audio.MusicTrack
import klyph.audio.PcmStream
import klyph.audio.PlaybackMode
import klyph.platform.KlyphPlatformApi
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class Musics {
    @Sample
    fun play() {
        // A game uses app.audio.music; this Audio plays to no device, so the sample mixes it by hand.
        val audio = Audio(AudioConfig(sampleRate = 8000))
        // One second of a quiet mono hum, made up in code.
        val hum = MusicTrack(duration = 1.seconds, channels = 1, sampleRate = 8000) {
            object : PcmStream {
                private var position = 0
                override val channels: Int = 1
                override val sampleRate: Int = 8000

                override fun read(output: FloatArray, frames: Int): Int {
                    val count = minOf(frames, 8000 - position)
                    output.fill(0.1f, 0, count)
                    position += count
                    return count
                }

                override fun rewind() {
                    position = 0
                }

                override fun close() {}
            }
        }
        audio.music.play(hum, mode = PlaybackMode.ONCE, fadeIn = 100.milliseconds)
        assertTrue(audio.music.isPlaying)
        assertPrints(audio.music.currentOrNull, "MusicTrack(duration=1s, channels=1, sampleRate=8000)")

        audio.music.volume = 0.5f
        audio.renderer.render(FloatArray(2 * 12000), 12000)
        assertFalse(audio.music.isPlaying)
        audio.close()
    }

    @Sample
    fun stop() {
        val audio = Audio(AudioConfig(sampleRate = 8000))
        val hum = MusicTrack(duration = 1.seconds, channels = 1, sampleRate = 8000) {
            object : PcmStream {
                override val channels: Int = 1
                override val sampleRate: Int = 8000
                override fun read(output: FloatArray, frames: Int): Int {
                    output.fill(0.1f, 0, frames)
                    return frames
                }

                override fun rewind() {}
                override fun close() {}
            }
        }
        audio.music.play(hum, mode = PlaybackMode.LOOP)
        assertTrue(audio.music.isPlaying)
        audio.music.stop(fadeOut = 50.milliseconds)
        // A fade of 50 ms is 400 frames; 800 frames of mix finish it.
        audio.renderer.render(FloatArray(2 * 800), 800)
        assertFalse(audio.music.isPlaying)
        audio.close()
    }
}
