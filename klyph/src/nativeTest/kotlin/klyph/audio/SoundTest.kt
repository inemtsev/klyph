package klyph.audio

import klyph.platform.AudioSamples
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class SoundTest {
    @Test
    fun copiesStereoSamplesInterleaved() {
        val samples = AudioSamples(floatArrayOf(0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f), channels = 2, sampleRate = 100)
        val sound = Sound(samples)
        assertEquals(2, sound.channels)
        assertEquals(100, sound.sampleRate)
        assertEquals(3, sound.frameCount)
        assertEquals(30.milliseconds, sound.duration)
        assertContentEquals(floatArrayOf(0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f), sound.samples)
    }

    @Test
    fun copiesMonoSamples() {
        val sound = Sound(AudioSamples(floatArrayOf(0.5f, 0.25f), channels = 1, sampleRate = 48000))
        assertContentEquals(floatArrayOf(0.5f, 0.25f), sound.samples)
        assertEquals("Sound(frames=2, channels=1, sampleRate=48000, duration=41.666us)", sound.toString())
    }

    @Test
    fun copiesEmptySamples() {
        val sound = Sound(AudioSamples(FloatArray(0), channels = 2, sampleRate = 44100))
        assertEquals(0, sound.frameCount)
        assertEquals(0, sound.samples.size)
    }

    @Test
    fun decodedWavPlaysThroughTheRenderer() {
        // A mono 16-bit WAV at 48 kHz holding four frames of half amplitude.
        val wav = wav(sampleRate = 48000, values = shortArrayOf(16384, 16384, 16384, 16384))
        val sound = Sound(AudioSamples.decode(wav))
        assertEquals(4, sound.frameCount)
        val audio = Audio(AudioConfig(sampleRate = 48000))
        audio.play(sound, pan = -1f)
        val output = FloatArray(8 * 2)
        audio.renderer.render(output, 8)
        assertEquals(0.5f, output[0], 1e-4f)
        assertEquals(0f, output[1])
        assertEquals(0f, output[8 * 2 - 2])
    }

    /** Returns a mono 16-bit PCM WAV file at [sampleRate] holding [values]. */
    private fun wav(sampleRate: Int, values: ShortArray): ByteArray {
        val dataSize = values.size * 2
        val bytes = ArrayList<Byte>()
        fun text(value: String) = value.forEach { bytes.add(it.code.toByte()) }
        fun int(value: Int, size: Int) = repeat(size) { bytes.add((value ushr (8 * it)).toByte()) }
        text("RIFF")
        int(36 + dataSize, 4)
        text("WAVE")
        text("fmt ")
        int(16, 4)
        int(1, 2) // PCM
        int(1, 2) // mono
        int(sampleRate, 4)
        int(sampleRate * 2, 4)
        int(2, 2)
        int(16, 2)
        text("data")
        int(dataSize, 4)
        for (value in values) int(value.toInt(), 2)
        return bytes.toByteArray()
    }
}
