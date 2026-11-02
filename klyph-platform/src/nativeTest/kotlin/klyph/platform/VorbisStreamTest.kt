package klyph.platform

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VorbisStreamTest {
    @Test
    fun opensMonoTone() {
        VorbisStream.open(TestSounds.MONO_TONE_OGG).use { stream ->
            assertEquals(1, stream.channels)
            assertEquals(22050, stream.sampleRate)
            assertEquals(5512L, stream.frameCount)
            assertEquals("VorbisStream(1 ch, 22050 Hz, 5512 frames)", stream.toString())
        }
    }

    @Test
    fun blocksMatchFullDecode() {
        for (bytes in listOf(TestSounds.MONO_TONE_OGG, TestSounds.STEREO_TONE_OGG)) {
            val decoded = AudioSamples.decode(bytes)
            VorbisStream.open(bytes).use { stream ->
                assertEquals(decoded.channels, stream.channels)
                val streamed = readAll(stream, blockFrames = 100)
                assertEquals(decoded.frameCount * decoded.channels, streamed.size)
                for (frame in 0..<decoded.frameCount) {
                    for (channel in 0..<decoded.channels) {
                        val expected = decoded[frame, channel]
                        val actual = streamed[frame * decoded.channels + channel]
                        assertTrue(abs(expected - actual) < 1e-6f, "Frame $frame channel $channel: $actual, expected $expected.")
                    }
                }
            }
        }
    }

    @Test
    fun stereoIsInterleaved() {
        VorbisStream.open(TestSounds.STEREO_TONE_OGG).use { stream ->
            assertEquals(2, stream.channels)
            assertEquals(2205L, stream.frameCount)
            val output = FloatArray(200)
            assertEquals(100, stream.read(output, 100))
            // 440 Hz on the left and 660 Hz on the right, so the channels differ.
            assertTrue((0..<100).any { abs(output[it * 2] - output[it * 2 + 1]) > 0.01f })
        }
    }

    @Test
    fun rewindRestarts() {
        VorbisStream.open(TestSounds.MONO_TONE_OGG).use { stream ->
            val first = FloatArray(300)
            assertEquals(300, stream.read(first, 300))
            val second = FloatArray(300)
            stream.read(second, 300)
            stream.rewind()
            val again = FloatArray(300)
            assertEquals(300, stream.read(again, 300))
            assertContentEquals(first, again)
        }
    }

    @Test
    fun readAfterEndReturnsZero() {
        VorbisStream.open(TestSounds.MONO_TONE_OGG).use { stream ->
            val output = FloatArray(10000)
            assertEquals(5512, stream.read(output, 10000))
            assertEquals(0, stream.read(output, 100))
            assertEquals(0, stream.read(output, 100))
            stream.rewind()
            assertEquals(100, stream.read(output, 100))
        }
    }

    @Test
    fun readValidatesArguments() {
        VorbisStream.open(TestSounds.STEREO_TONE_OGG).use { stream ->
            assertFailsWith<IllegalArgumentException> { stream.read(FloatArray(10), 6) }
            assertFailsWith<IllegalArgumentException> { stream.read(FloatArray(10), -1) }
            assertEquals(0, stream.read(FloatArray(10), 0))
        }
    }

    @Test
    fun closedStreamRejectsUse() {
        val stream = VorbisStream.open(TestSounds.MONO_TONE_OGG)
        stream.close()
        stream.close()
        assertFailsWith<IllegalStateException> { stream.read(FloatArray(10), 10) }
        assertFailsWith<IllegalStateException> { stream.rewind() }
    }

    @Test
    fun openFailsOnBadInput() {
        val e = assertFailsWith<AudioException> { VorbisStream.open(byteArrayOf(1, 2, 3, 4)) }
        val message = e.message.orEmpty()
        assertTrue(message.startsWith("Vorbis data could not be opened: "), message)
        assertTrue(message.endsWith("."), message)
        assertFailsWith<AudioException> { VorbisStream.open(byteArrayOf()) }
        assertNull(VorbisStream.openOrNull(byteArrayOf()))
        assertNull(VorbisStream.openOrNull(TestSounds.MONO_TONE_OGG.copyOf(40)))
    }

    private fun readAll(stream: VorbisStream, blockFrames: Int): FloatArray {
        val block = FloatArray(blockFrames * stream.channels)
        val all = ArrayList<Float>()
        while (true) {
            val frames = stream.read(block, blockFrames)
            if (frames == 0) break
            for (i in 0..<frames * stream.channels) all += block[i]
        }
        return all.toFloatArray()
    }
}
