package klyph.platform

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

class AudioSamplesTest {
    @Test
    fun toFloatArrayCopiesInterleavedSamples() {
        val source = floatArrayOf(0.1f, -0.1f, 0.5f, -0.5f)
        val samples = AudioSamples(source, channels = 2, sampleRate = 48000)
        val copy = samples.toFloatArray()
        assertEquals(source.toList(), copy.toList())
        copy[0] = 9f
        assertEquals(0.1f, samples[0, 0])
        assertEquals(0.1f, samples.toFloatArray()[0])
    }

    @Test
    fun decodesMono16BitWav() {
        val pcm = shorts(0, 16384, -16384, 32767, -32768)
        val samples = AudioSamples.decode(wav(channels = 1, sampleRate = 8000, bitsPerSample = 16, pcm))
        assertEquals(1, samples.channels)
        assertEquals(8000, samples.sampleRate)
        assertEquals(5, samples.frameCount)
        assertEquals(625.microseconds, samples.duration)
        assertNear(listOf(0f, 0.5f, -0.5f, 32767f / 32768f, -1f), (0..<5).map { samples[it, 0] })
    }

    @Test
    fun decodesStereo8BitWav() {
        // Unsigned 8-bit: 128 is silence. Left, right for each of three frames.
        val pcm = byteArrayOf(128.toByte(), 192.toByte(), 64, 0, 255.toByte(), 128.toByte())
        val samples = AudioSamples.decode(wav(channels = 2, sampleRate = 11025, bitsPerSample = 8, pcm))
        assertEquals(2, samples.channels)
        assertEquals(11025, samples.sampleRate)
        assertEquals(3, samples.frameCount)
        // 3 / 11025 s, rounded down to the nanosecond.
        assertEquals(272108L, samples.duration.inWholeNanoseconds)
        assertNear(listOf(0f, 0.5f), listOf(samples[0, 0], samples[0, 1]))
        assertNear(listOf(-0.5f, -1f), listOf(samples[1, 0], samples[1, 1]))
        assertNear(listOf(127f / 128f, 0f), listOf(samples[2, 0], samples[2, 1]))
    }

    @Test
    fun mixesMoreThanTwoChannelsDownToStereo() {
        val pcm = shorts(1000, 1000, 1000, 1000, 2000, 2000, 2000, 2000)
        val samples = AudioSamples.decode(wav(channels = 4, sampleRate = 22050, bitsPerSample = 16, pcm))
        assertEquals(2, samples.channels)
        assertEquals(2, samples.frameCount)
    }

    @Test
    fun decodesEmptyWav() {
        val samples = AudioSamples.decode(wav(channels = 1, sampleRate = 8000, bitsPerSample = 16, ByteArray(0)))
        assertEquals(0, samples.frameCount)
        assertEquals(0.milliseconds, samples.duration)
    }

    @Test
    fun decodesOgg() {
        val samples = AudioSamples.decode(TestSounds.MONO_TONE_OGG)
        assertEquals(1, samples.channels)
        assertEquals(22050, samples.sampleRate)
        assertEquals(5512, samples.frameCount)
        assertEquals(5512L * 1_000_000_000L / 22050, samples.duration.inWholeNanoseconds)
        val peak = (0..<samples.frameCount).maxOf { abs(samples[it, 0]) }
        assertTrue(peak in 0.4f..0.6f, "Peak $peak is not near the 0.5 the tone was made with.")
    }

    @Test
    fun decodeThrowsOnBadInput() {
        val wav = wav(channels = 1, sampleRate = 8000, bitsPerSample = 16, shorts(1, 2, 3))
        val garbage = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val e = assertFailsWith<AudioException> { AudioSamples.decode(garbage) }
        val message = e.message.orEmpty()
        assertTrue(message.startsWith("Audio data could not be decoded: "), message)
        assertTrue(message.endsWith(".") && !message.endsWith(".."), message)
        assertFailsWith<AudioException> { AudioSamples.decode(byteArrayOf()) }
        // Cut inside the format chunk.
        assertFailsWith<AudioException> { AudioSamples.decode(wav.copyOf(30)) }
        // Only the RIFF header.
        assertFailsWith<AudioException> { AudioSamples.decode(wav.copyOf(12)) }
        // An Ogg page header followed by noise.
        assertFailsWith<AudioException> { AudioSamples.decode("OggS".encodeToByteArray() + garbage) }
    }

    @Test
    fun decodeOrNullReturnsNullOnBadInput() {
        val wav = wav(channels = 1, sampleRate = 8000, bitsPerSample = 16, shorts(1, 2, 3))
        assertNull(AudioSamples.decodeOrNull(byteArrayOf()))
        assertNull(AudioSamples.decodeOrNull(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)))
        assertNull(AudioSamples.decodeOrNull(wav.copyOf(30)))
        assertNull(AudioSamples.decodeOrNull(TestSounds.MONO_TONE_OGG.copyOf(40)))
    }

    @Test
    fun constructorCopiesAndValidates() {
        val source = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val samples = AudioSamples(source, channels = 2, sampleRate = 48000)
        source[0] = 9f
        assertEquals(0.1f, samples[0, 0])
        assertEquals(0.4f, samples[1, 1])
        assertEquals(2, samples.frameCount)
        assertFailsWith<IllegalArgumentException> { AudioSamples(FloatArray(3), channels = 2, sampleRate = 48000) }
        assertFailsWith<IllegalArgumentException> { AudioSamples(FloatArray(3), channels = 3, sampleRate = 48000) }
        assertFailsWith<IllegalArgumentException> { AudioSamples(FloatArray(3), channels = 1, sampleRate = 0) }
    }

    @Test
    fun getThrowsOutOfBounds() {
        val samples = AudioSamples(FloatArray(4), channels = 2, sampleRate = 48000)
        assertFailsWith<IndexOutOfBoundsException> { samples[2, 0] }
        assertFailsWith<IndexOutOfBoundsException> { samples[-1, 0] }
        val e = assertFailsWith<IndexOutOfBoundsException> { samples[0, 2] }
        assertEquals("Sample at frame 0, channel 2 is out of bounds for 2 frames of 2 channels.", e.message)
    }

    @Test
    fun toStringShowsFormat() {
        assertEquals("AudioSamples(1 ch, 44100 Hz, 3 frames)", AudioSamples(FloatArray(3), 1, 44100).toString())
    }

    private fun assertNear(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertTrue(abs(expected[i] - actual[i]) < 1e-6f, "Sample $i is ${actual[i]}, expected ${expected[i]}.")
        }
    }

    private fun shorts(vararg values: Int): ByteArray {
        val bytes = ByteArray(values.size * 2)
        for ((i, value) in values.withIndex()) {
            bytes[i * 2] = value.toByte()
            bytes[i * 2 + 1] = (value shr 8).toByte()
        }
        return bytes
    }

    /** Builds a PCM WAV file around [data]. */
    private fun wav(channels: Int, sampleRate: Int, bitsPerSample: Int, data: ByteArray): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        val header = ArrayList<Byte>()
        fun tag(text: String) = text.forEach { header += it.code.toByte() }
        fun u16(value: Int) = repeat(2) { header += (value shr (8 * it)).toByte() }
        fun u32(value: Int) = repeat(4) { header += (value shr (8 * it)).toByte() }
        tag("RIFF")
        u32(36 + data.size)
        tag("WAVE")
        tag("fmt ")
        u32(16)
        u16(1)
        u16(channels)
        u32(sampleRate)
        u32(sampleRate * blockAlign)
        u16(blockAlign)
        u16(bitsPerSample)
        tag("data")
        u32(data.size)
        return header.toByteArray() + data
    }
}
