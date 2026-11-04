package klyph.audio

import klyph.platform.AudioException
import klyph.platform.VorbisStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

class MusicTrackTest {
    private val toneFrames = 5512
    private val toneRate = 22050

    @Test
    fun fromOggReadsTheFormatAndLength() {
        val track = MusicTrack.fromOgg(TestOgg.MONO_TONE_OGG)
        assertEquals(1, track.channels)
        assertEquals(toneRate, track.sampleRate)
        assertEquals((toneFrames * 1_000_000_000L / toneRate).nanoseconds, track.duration)
    }

    @Test
    fun oggStreamReadsTheSameFramesAsVorbisStream() {
        val expected = FloatArray(toneFrames + 100)
        val read = VorbisStream.open(TestOgg.MONO_TONE_OGG).use { it.read(expected, toneFrames + 100) }
        assertEquals(toneFrames, read)

        val stream = MusicTrack.fromOgg(TestOgg.MONO_TONE_OGG).openStream()
        try {
            assertEquals(1, stream.channels)
            assertEquals(toneRate, stream.sampleRate)
            // Blocks of 1000 frames, as the mixer reads them, joined back together.
            val actual = FloatArray(toneFrames)
            val block = FloatArray(1000)
            var frames = 0
            while (true) {
                val count = stream.read(block, 1000)
                if (count == 0) break
                block.copyInto(actual, frames, 0, count)
                frames += count
            }
            assertEquals(toneFrames, frames)
            assertContentEquals(expected.copyOf(toneFrames), actual)

            stream.rewind()
            val again = FloatArray(16)
            assertEquals(16, stream.read(again, 16))
            assertContentEquals(expected.copyOf(16), again)
        } finally {
            stream.close()
        }
    }

    @Test
    fun fromOggKeepsItsOwnCopyOfTheBytes() {
        val bytes = TestOgg.MONO_TONE_OGG.copyOf()
        val track = MusicTrack.fromOgg(bytes)
        bytes.fill(0)
        val stream = track.openStream()
        try {
            assertEquals(100, stream.read(FloatArray(100), 100))
        } finally {
            stream.close()
        }
    }

    @Test
    fun fromOggRejectsDataThatIsNotOggVorbis() {
        assertFailsWith<AudioException> { MusicTrack.fromOgg(byteArrayOf(1, 2, 3)) }
        assertFailsWith<AudioException> { MusicTrack.fromOgg(ByteArray(0)) }
        assertNull(MusicTrack.fromOggOrNull(byteArrayOf(1, 2, 3)))
        assertNull(MusicTrack.fromOggOrNull(TestOgg.MONO_TONE_OGG.copyOf(40)))
        assertEquals(toneRate, MusicTrack.fromOggOrNull(TestOgg.MONO_TONE_OGG)?.sampleRate)
    }

    @Test
    fun oggTrackPlaysThroughTheRendererAndItsStreamIsClosedAtTheEnd() {
        val audio = Audio(AudioConfig(sampleRate = toneRate, bufferFrames = 256))
        audio.music.play(MusicTrack.fromOgg(TestOgg.MONO_TONE_OGG), mode = PlaybackMode.ONCE)
        val output = FloatArray(512 * 2)
        var peak = 0f
        repeat(20) {
            audio.renderer.render(output, 512)
            for (value in output) peak = maxOf(peak, value)
        }
        assertTrue(peak > 0.3f, "The tone peaks at $peak.")
        assertFalse(audio.music.isPlaying)
        audio.endFrame()
        assertEquals(1L, audio.stats.closedStreamCount)
        audio.close()
    }

    @Test
    fun streamWithAnotherFormatThanTheTrackIsRejectedAndClosed() {
        val audio = Audio(AudioConfig(sampleRate = toneRate))
        var closed = false
        val track = MusicTrack(Duration.INFINITE, channels = 2, sampleRate = toneRate) {
            object : PcmStream {
                override val channels: Int = 1
                override val sampleRate: Int = toneRate

                override fun read(output: FloatArray, frames: Int): Int = 0

                override fun rewind() {}

                override fun close() {
                    closed = true
                }
            }
        }
        assertFailsWith<IllegalArgumentException> { audio.music.play(track) }
        assertTrue(closed)
        assertFalse(audio.music.isPlaying)
    }

    @Test
    fun constructorRejectsInvalidFormats() {
        val open = { error("Not opened.") }
        assertFailsWith<IllegalArgumentException> { MusicTrack(Duration.ZERO, channels = 3, sampleRate = 48000, open = open) }
        assertFailsWith<IllegalArgumentException> { MusicTrack(Duration.ZERO, channels = 1, sampleRate = 0, open = open) }
        assertFailsWith<IllegalArgumentException> { MusicTrack((-1).nanoseconds, channels = 1, sampleRate = 48000, open = open) }
    }
}
