package klyph.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MusicTest {
    private fun audio(enabled: Boolean = true): Audio =
        Audio(AudioConfig(enabled = enabled, sampleRate = RATE, bufferFrames = 256))

    /** A track whose every [MusicTrack.openStream] makes a new [CountingStream] and records it in [opened]. */
    private class Track(frames: Int = Int.MAX_VALUE, value: Float = 1f, channels: Int = 2, rate: Int = RATE) {
        val opened = ArrayList<CountingStream>()
        val track = MusicTrack(Duration.INFINITE, channels, rate) {
            CountingStream(channels, rate, frames, value).also { opened.add(it) }
        }
    }

    private fun Audio.render(frames: Int): FloatArray {
        val output = FloatArray(frames * 2)
        mixer.render(output, frames)
        return output
    }

    @Test
    fun playsAtFullVolumeWithoutFade() {
        val audio = audio()
        val track = Track()
        audio.music.play(track.track)
        assertTrue(audio.music.isPlaying)
        assertSame(track.track, audio.music.currentOrNull)
        val out = audio.render(1000)
        assertTrue(out.all { it == 1f })
    }

    @Test
    fun fadeInRampsLinearlyAcrossTheFade() {
        val audio = audio()
        audio.music.play(Track().track, fadeIn = 10.milliseconds)
        val fade = 480
        val out = audio.render(fade)
        for (frame in 0 until fade) {
            assertEquals(frame.toFloat() / fade, out[frame * 2], 1e-4f)
            assertEquals(frame.toFloat() / fade, out[frame * 2 + 1], 1e-4f)
        }
        val after = audio.render(16)
        assertTrue(after.all { it == 1f })
    }

    @Test
    fun stopWithFadeReachesSilenceThenRetiresTheStream() {
        val audio = audio()
        val track = Track()
        audio.music.play(track.track)
        audio.render(100)
        audio.music.stop(fadeOut = 10.milliseconds)
        assertTrue(audio.music.isPlaying)

        val out = audio.render(480)
        assertEquals(1f, out[0], 1e-4f)
        assertEquals(1f / 480, out[479 * 2], 1e-4f)
        assertFalse(audio.music.isPlaying)
        assertNull(audio.music.currentOrNull)
        assertEquals(1, audio.mixer.retired.size)
        assertEquals(0, track.opened[0].closes)

        audio.endFrame()
        assertEquals(1, track.opened[0].closes)
        assertEquals(1L, audio.stats.closedStreams)
        assertEquals(0, audio.openStreams)
        assertTrue(audio.render(16).all { it == 0f })
    }

    @Test
    fun stopWithoutFadeRetiresAtOnce() {
        val audio = audio()
        val track = Track()
        audio.music.play(track.track)
        audio.render(10)
        audio.music.stop()
        assertTrue(audio.render(10).all { it == 0f })
        assertFalse(audio.music.isPlaying)
        audio.endFrame()
        assertEquals(1, track.opened[0].closes)
    }

    @Test
    fun swapCrossFadesAndRetiresTheOldStream() {
        val audio = audio()
        val first = Track()
        val second = Track()
        audio.music.play(first.track)
        audio.render(100)
        audio.music.play(second.track, fadeIn = 10.milliseconds)
        assertSame(second.track, audio.music.currentOrNull)

        val out = audio.render(480)
        // Both tracks are constant 1: a linear cross-fade sums to 1 throughout.
        for (frame in 0 until 480) assertEquals(1f, out[frame * 2], 1e-4f)
        assertTrue(audio.music.isPlaying)

        audio.endFrame()
        assertEquals(1, first.opened[0].closes)
        assertEquals(0, second.opened[0].closes)
        assertEquals(1, audio.openStreams)
    }

    @Test
    fun swapWithoutFadeCutsTheOldTrack() {
        val audio = audio()
        val first = Track(value = 1f)
        val second = Track(value = 0.5f)
        audio.music.play(first.track)
        audio.render(10)
        audio.music.play(second.track)
        val out = audio.render(10)
        assertTrue(out.all { it == 0.5f })
        audio.endFrame()
        assertEquals(1, first.opened[0].closes)
    }

    @Test
    fun onceTrackEndsAndIsClosed() {
        val audio = audio()
        val track = Track(frames = 300)
        audio.music.play(track.track, mode = PlaybackMode.ONCE)
        audio.render(1000)
        assertFalse(audio.music.isPlaying)
        audio.endFrame()
        assertEquals(1, track.opened[0].closes)
        assertEquals(0, track.opened[0].rewinds)
    }

    @Test
    fun loopTrackRewindsAtItsEnd() {
        val audio = audio()
        val track = Track(frames = 300)
        audio.music.play(track.track)
        val out = audio.render(1000)
        assertTrue(audio.music.isPlaying)
        assertTrue(track.opened[0].rewinds >= 3)
        assertTrue(out.all { it == 1f })
    }

    @Test
    fun monoTrackAtAnotherRateIsResampledToBothChannels() {
        val audio = audio()
        audio.music.play(Track(channels = 1, rate = RATE / 2, value = 0.25f).track)
        val out = audio.render(600)
        assertTrue(out.all { it == 0.25f })
    }

    @Test
    fun volumesScaleMusic() {
        val audio = audio()
        audio.music.volume = 0.5f
        audio.musicVolume = 0.5f
        audio.music.play(Track().track)
        audio.render(16)
        val out = audio.render(16)
        assertEquals(0.25f, out[0], 1e-6f)
        audio.music.volume = 5f
        assertEquals(1f, audio.music.volume)
    }

    @Test
    fun negativeFadeThrows() {
        val audio = audio()
        assertFailsWith<IllegalArgumentException> { audio.music.play(Track().track, fadeIn = (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { audio.music.stop(fadeOut = Duration.INFINITE) }
    }

    @Test
    fun closeReleasesEveryStream() {
        val audio = audio()
        val first = Track()
        val second = Track()
        audio.music.play(first.track)
        audio.render(10)
        audio.music.play(second.track, fadeIn = 1.seconds)
        audio.close()
        assertEquals(1, first.opened[0].closes)
        assertEquals(1, second.opened[0].closes)
        assertFalse(audio.isEnabled)
        assertFalse(audio.music.isPlaying)
    }

    @Test
    fun disabledMusicOpensNothing() {
        val audio = audio(enabled = false)
        val track = Track()
        audio.music.play(track.track)
        assertTrue(track.opened.isEmpty())
        assertFalse(audio.music.isPlaying)
        assertNull(audio.music.currentOrNull)
    }

    @Test
    fun manyPlaysInOneFrameNeverOverflowTheRetiredRing() {
        val audio = audio()
        val track = Track()
        repeat(200) { audio.music.play(track.track) }
        audio.render(16)
        audio.endFrame()
        val opened = track.opened.size
        assertTrue(opened <= audio.mixer.retired.capacity)
        assertEquals(opened - 1, track.opened.count { it.closes == 1 })
        assertTrue(audio.stats.droppedCommands > 0)
    }

    @Test
    fun musicHasAReadableString() {
        val audio = audio()
        assertEquals("Music(current=null, isPlaying=false, volume=1.0)", audio.music.toString())
    }

    private companion object {
        const val RATE = 48_000
    }
}
