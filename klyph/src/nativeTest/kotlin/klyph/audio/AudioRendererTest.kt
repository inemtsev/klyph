package klyph.audio

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

/** [Audio.renderer] mixing headless, through the public constructor only. */
class AudioRendererTest {
    private val centre = (1.0 / sqrt(2.0)).toFloat()

    @Test
    fun publicConstructorStartsEnabledWithTheDefaults() {
        val audio = Audio()
        assertTrue(audio.isEnabled)
        assertEquals("Audio(isEnabled=true, voices=32, masterVolume=1.0, effectsVolume=1.0, musicVolume=1.0)", audio.toString())
        audio.close()
        assertFalse(audio.isEnabled)
    }

    @Test
    fun rendererMixesEffectsAndMusic() {
        val audio = Audio(AudioConfig(sampleRate = 8000))
        audio.play(Sound(FloatArray(100) { 0.5f }, channels = 1, sampleRate = 8000))
        audio.music.play(MusicTrack(Duration.INFINITE, channels = 2, sampleRate = 8000) { ConstantStream(0.25f) })
        val output = FloatArray(64 * 2)
        audio.renderer.render(output, 64)
        for (frame in 0 until 64) {
            assertEquals(0.5f * centre + 0.25f, output[frame * 2], 1e-6f)
            assertEquals(0.5f * centre + 0.25f, output[frame * 2 + 1], 1e-6f)
        }
        audio.close()
    }

    @Test
    fun rendererWritesSilenceWhenNothingPlays() {
        val audio = Audio()
        val output = FloatArray(32) { 9f }
        audio.renderer.render(output, 16)
        assertTrue(output.all { it == 0f })
    }

    @Test
    fun endFrameRefreshesStats() {
        val audio = Audio(AudioConfig(sampleRate = 8000))
        audio.play(Sound(FloatArray(100) { 0.5f }, channels = 1, sampleRate = 8000))
        audio.renderer.render(FloatArray(20), 10)
        assertEquals(0, audio.stats.activeVoiceCount)
        audio.endFrame()
        assertEquals(1, audio.stats.activeVoiceCount)
        audio.renderer.render(FloatArray(400), 200)
        audio.endFrame()
        assertEquals(0, audio.stats.activeVoiceCount)
    }

    @Test
    fun closeClosesThePlayingMusicStream() {
        val audio = Audio(AudioConfig(sampleRate = 8000))
        val stream = ConstantStream(0.25f)
        audio.music.play(MusicTrack(Duration.INFINITE, channels = 2, sampleRate = 8000) { stream })
        audio.renderer.render(FloatArray(20), 10)
        audio.close()
        assertTrue(stream.isClosed)
        assertFalse(audio.music.isPlaying)
    }

    /** An endless stereo stream of [value] at 8000 Hz. */
    private class ConstantStream(private val value: Float) : PcmStream {
        override val channels: Int = 2
        override val sampleRate: Int = 8000
        var isClosed = false

        override fun read(output: FloatArray, frames: Int): Int {
            output.fill(value, 0, frames * 2)
            return frames
        }

        override fun rewind() {}

        override fun close() {
            isClosed = true
        }
    }
}
