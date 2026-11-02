@file:OptIn(ExperimentalAtomicApi::class)

package klyph.audio

import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AudioTest {
    private val centre = (1.0 / sqrt(2.0)).toFloat()

    private fun audio(maxVoices: Int = 32, enabled: Boolean = true, commandCapacity: Int = 256): Audio =
        Audio(AudioConfig(enabled = enabled, maxVoices = maxVoices, sampleRate = RATE), commandCapacity)

    private fun constant(frames: Int, value: Float = 1f, channels: Int = 1, rate: Int = RATE): Sound =
        Sound(FloatArray(frames * channels) { value }, channels, rate)

    private fun Audio.render(frames: Int): FloatArray {
        val output = FloatArray(frames * 2)
        mixer.render(output, frames)
        return output
    }

    @Test
    fun centredMonoVoiceGetsConstantPowerGainOnBothSides() {
        val audio = audio()
        audio.play(constant(100), volume = 0.5f)
        val out = audio.render(64)
        for (frame in 0 until 64) {
            assertEquals(0.5f * centre, out[frame * 2], 1e-6f)
            assertEquals(0.5f * centre, out[frame * 2 + 1], 1e-6f)
        }
    }

    @Test
    fun panLeftSilencesTheRightChannel() {
        val audio = audio()
        audio.play(constant(100), pan = -1f)
        val out = audio.render(64)
        for (frame in 0 until 64) {
            assertEquals(1f, out[frame * 2], 1e-6f)
            assertEquals(0f, out[frame * 2 + 1])
        }
    }

    @Test
    fun stereoSoundIsBalancedNotPanned() {
        val audio = audio()
        audio.play(constant(100, channels = 2, value = 0.5f), pan = 0.5f)
        val out = audio.render(16)
        assertEquals(0.25f, out[0], 1e-6f)
        assertEquals(0.5f, out[1], 1e-6f)
    }

    @Test
    fun onceVoiceEndsAfterItsFramesAndThenIsSilent() {
        val audio = audio()
        val voice = audio.play(constant(100))
        val first = audio.render(100)
        assertTrue(first[198] > 0f)
        assertFalse(audio.isPlaying(voice))
        val after = audio.render(16)
        assertTrue(after.all { it == 0f })
    }

    @Test
    fun pitchTwoEndsInHalfTheFrames() {
        val audio = audio()
        val voice = audio.play(constant(1000), pitch = 2f)
        audio.render(499)
        assertTrue(audio.isPlaying(voice))
        audio.render(1)
        assertFalse(audio.isPlaying(voice))
    }

    @Test
    fun halfRateSoundIsResampledByLinearInterpolation() {
        val audio = audio()
        // Frames 0, 1, 2, ... at half the device rate: each output frame moves half a frame.
        audio.play(Sound(FloatArray(10) { it.toFloat() / 10f }, 1, RATE / 2), pan = -1f)
        val out = audio.render(8)
        for (frame in 0 until 8) assertEquals(frame * 0.05f, out[frame * 2], 1e-6f)
    }

    @Test
    fun loopWrapsToTheStart() {
        val audio = audio()
        val voice = audio.play(Sound(FloatArray(100) { it / 100f }, 1, RATE), pan = -1f, mode = PlaybackMode.LOOP)
        val out = audio.render(150)
        assertEquals(0.99f, out[99 * 2], 1e-6f)
        assertEquals(0f, out[100 * 2], 1e-6f)
        assertEquals(0.2f, out[120 * 2], 1e-6f)
        audio.render(10_000)
        assertTrue(audio.isPlaying(voice))
    }

    @Test
    fun playOnAFullPoolStealsTheEarliestVoice() {
        val audio = audio(maxVoices = 32)
        val sound = constant(100)
        val voices = List(32) { audio.play(sound, mode = PlaybackMode.LOOP) }
        audio.render(16)
        assertTrue(voices.all { audio.isPlaying(it) })

        val thirtyThird = audio.play(sound, mode = PlaybackMode.LOOP)
        assertNotEquals(Voice.NONE, thirtyThird)
        assertEquals(voices[0].slot, thirtyThird.slot)
        assertFalse(audio.isPlaying(voices[0]))
        assertTrue(audio.isPlaying(thirtyThird))
        assertTrue(voices.drop(1).all { audio.isPlaying(it) })

        // The next steal takes the second voice, not the slot just reused.
        val thirtyFourth = audio.play(sound)
        assertEquals(voices[1].slot, thirtyFourth.slot)

        audio.endFrame()
        assertEquals(2L, audio.stats.stolenVoices)
        assertEquals(32, audio.stats.activeVoices)
    }

    @Test
    fun finishedVoiceFreesItsSlotForTheNextPlay() {
        val audio = audio(maxVoices = 2)
        val first = audio.play(constant(10))
        audio.render(10)
        assertFalse(audio.isPlaying(first))
        val second = audio.play(constant(10))
        assertEquals(first.slot, second.slot)
        assertNotEquals(first, second)
        audio.endFrame()
        assertEquals(0L, audio.stats.stolenVoices)
    }

    @Test
    fun stopMarksTheVoiceFinishedAfterOneRender() {
        val audio = audio()
        val voice = audio.play(constant(100), mode = PlaybackMode.LOOP)
        audio.render(16)
        audio.stop(voice)
        assertTrue(audio.isPlaying(voice))
        val out = audio.render(16)
        assertFalse(audio.isPlaying(voice))
        assertTrue(out.all { it == 0f })
        assertEquals(voice.generation, audio.mixer.finished.loadAt(voice.slot))
    }

    @Test
    fun stopAllEndsEveryVoice() {
        val audio = audio()
        val voices = List(5) { audio.play(constant(100), mode = PlaybackMode.LOOP) }
        audio.render(1)
        audio.stopAll()
        audio.render(1)
        assertTrue(voices.none { audio.isPlaying(it) })
    }

    @Test
    fun noneAndStaleVoicesAreNoOps() {
        val audio = audio(maxVoices = 1)
        val sound = constant(100)
        val old = audio.play(sound, mode = PlaybackMode.LOOP)
        val current = audio.play(sound, pan = -1f, mode = PlaybackMode.LOOP)
        val queued = audio.mixer.commands.size

        audio.stop(old)
        audio.setVolume(old, 0f)
        audio.setPitch(old, 2f)
        audio.setPan(old, 1f)
        audio.stop(Voice.NONE)
        audio.setVolume(Voice.NONE, 0f)
        audio.setPitch(Voice.NONE, 2f)
        audio.setPan(Voice.NONE, 1f)
        assertEquals(queued, audio.mixer.commands.size)
        assertFalse(audio.isPlaying(Voice.NONE))
        assertFalse(audio.isPlaying(old))

        val out = audio.render(16)
        assertTrue(audio.isPlaying(current))
        assertEquals(1f, out[30], 1e-6f)
    }

    @Test
    fun setVolumeAndPanRampToTheirTargetsOverOneBlock() {
        val audio = audio()
        val voice = audio.play(constant(1000), pan = -1f, mode = PlaybackMode.LOOP)
        audio.render(16)
        audio.setVolume(voice, 0.5f)
        val ramp = audio.render(16)
        assertEquals(1f, ramp[0], 1e-6f)
        assertTrue(ramp[15 * 2] < 1f && ramp[15 * 2] > 0.5f)
        val settled = audio.render(16)
        assertEquals(0.5f, settled[0], 1e-6f)
        assertEquals(0.5f, settled[30], 1e-6f)
    }

    @Test
    fun setPitchChangesTheStep() {
        val audio = audio()
        val voice = audio.play(constant(1000))
        audio.render(100)
        audio.setPitch(voice, 4f)
        audio.render(224)
        assertTrue(audio.isPlaying(voice))
        audio.render(1)
        assertFalse(audio.isPlaying(voice))
    }

    @Test
    fun busVolumesScaleEffectsAndAreClamped() {
        val audio = audio()
        audio.masterVolume = 2f
        assertEquals(1f, audio.masterVolume)
        audio.masterVolume = 0.5f
        audio.effectsVolume = 0.5f
        audio.play(constant(1000), pan = -1f, mode = PlaybackMode.LOOP)
        audio.render(16)
        val out = audio.render(16)
        assertEquals(0.25f, out[0], 1e-6f)
        audio.effectsVolume = -1f
        assertEquals(0f, audio.effectsVolume)
        assertFailsWith<IllegalArgumentException> { audio.musicVolume = Float.NaN }
    }

    @Test
    fun outputIsClampedToUnitRange() {
        val audio = audio()
        repeat(3) { audio.play(constant(100), pan = -1f) }
        val out = audio.render(16)
        assertEquals(1f, out[0])
    }

    @Test
    fun pitchOutsideRangeThrowsAndVolumeAndPanAreClamped() {
        val audio = audio()
        assertFailsWith<IllegalArgumentException> { audio.play(constant(10), pitch = 0.2f) }
        assertFailsWith<IllegalArgumentException> { audio.play(constant(10), pitch = 4.5f) }
        assertFailsWith<IllegalArgumentException> { audio.setPitch(Voice.NONE, Float.NaN) }
        audio.play(constant(100), volume = 3f, pan = -7f)
        val out = audio.render(4)
        assertEquals(1f, out[0], 1e-6f)
        assertEquals(0f, out[1])
    }

    @Test
    fun fullRingReturnsNoneAndCountsTheDrop() {
        val audio = audio(commandCapacity = 4)
        val sound = constant(100)
        repeat(4) { assertNotEquals(Voice.NONE, audio.play(sound)) }
        assertEquals(Voice.NONE, audio.play(sound))
        audio.endFrame()
        assertEquals(1L, audio.stats.droppedCommands)
        // The dropped play claimed no slot: after a render, four voices are active.
        audio.render(1)
        audio.endFrame()
        assertEquals(4, audio.stats.activeVoices)
    }

    @Test
    fun emptySoundEndsAtOnce() {
        val audio = audio()
        val voice = audio.play(Sound(FloatArray(0), 1, RATE))
        audio.render(1)
        assertFalse(audio.isPlaying(voice))
    }

    @Test
    fun disabledAudioDoesNothing() {
        val audio = audio(enabled = false)
        val voice = audio.play(constant(100))
        assertEquals(Voice.NONE, voice)
        assertFalse(audio.isPlaying(voice))
        assertFalse(audio.isEnabled)
        audio.stopAll()
        assertEquals(0, audio.mixer.commands.size)
    }

    @Test
    fun voiceAndStatsHaveReadableStrings() {
        assertEquals("Voice.NONE", Voice.NONE.toString())
        assertEquals("Voice(3:7)", Voice.of(3, 7).toString())
        assertEquals(
            "AudioStats(activeVoices=0, stolenVoices=0, droppedCommands=0, closedStreams=0)",
            audio().stats.toString(),
        )
        assertEquals("Sound(frames=24000, channels=1, sampleRate=48000, duration=500ms)", constant(24_000).toString())
    }

    @Test
    fun soundValidatesItsShape() {
        assertFailsWith<IllegalArgumentException> { Sound(FloatArray(3), 2, RATE) }
        assertFailsWith<IllegalArgumentException> { Sound(FloatArray(4), 3, RATE) }
        assertFailsWith<IllegalArgumentException> { Sound(FloatArray(4), 1, 0) }
        assertEquals(2, Sound(FloatArray(4), 2, RATE).frameCount)
    }

    @Test
    fun generationsWrapWithoutReachingZero() {
        assertEquals(1, Voice.nextGeneration(Voice.GENERATION_MASK))
        assertEquals(2, Voice.nextGeneration(1))
        val voice = Voice.of(Voice.MAX_SLOTS - 1, Voice.GENERATION_MASK)
        assertEquals(Voice.MAX_SLOTS - 1, voice.slot)
        assertEquals(Voice.GENERATION_MASK, voice.generation)
    }

    private companion object {
        const val RATE = 48_000
    }
}
