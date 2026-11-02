package samples.audio

import klyph.audio.Audio
import klyph.audio.AudioConfig
import klyph.audio.Sound
import klyph.platform.AudioSamples
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Audios {
    @Sample
    fun play() {
        // A game uses app.audio; this one plays to no device, so the sample mixes it by hand.
        val audio = Audio(AudioConfig(sampleRate = 8000))
        val beep = Sound(AudioSamples(FloatArray(100) { 0.5f }, channels = 1, sampleRate = 8000))
        val voice = audio.play(beep, volume = 0.8f, pan = -0.5f)
        assertTrue(audio.isPlaying(voice))
        // The beep lasts 100 frames, so 200 frames of mix play it to its end.
        audio.renderer.render(FloatArray(400), 200)
        assertFalse(audio.isPlaying(voice))
        audio.close()
    }

    @Sample
    fun stop() {
        val audio = Audio(AudioConfig(sampleRate = 8000))
        val hum = Sound(AudioSamples(FloatArray(8000) { 0.1f }, channels = 1, sampleRate = 8000))
        val voice = audio.play(hum)
        audio.stop(voice)
        // The stop takes effect when the mixer runs next.
        audio.renderer.render(FloatArray(20), 10)
        assertFalse(audio.isPlaying(voice))
        audio.close()
    }

    @Sample
    fun setVolume() {
        val audio = Audio(AudioConfig(sampleRate = 8000))
        val tone = Sound(AudioSamples(FloatArray(8000) { 1f }, channels = 1, sampleRate = 8000))
        val voice = audio.play(tone, pan = -1f)
        audio.setVolume(voice, 0.25f)
        val output = FloatArray(2 * 64)
        audio.renderer.render(output, 64)
        // The change ramps across the first block, then holds.
        audio.renderer.render(output, 64)
        assertPrints(output[0], "0.25")
        audio.close()
    }

    @Sample
    fun setPitch() {
        val audio = Audio(AudioConfig(sampleRate = 8000))
        val beep = Sound(AudioSamples(FloatArray(100) { 0.5f }, channels = 1, sampleRate = 8000))
        val voice = audio.play(beep)
        // At double speed the 100-frame beep lasts 50 frames, so 80 frames of mix end it.
        audio.setPitch(voice, 2f)
        audio.renderer.render(FloatArray(160), 80)
        assertFalse(audio.isPlaying(voice))
        audio.close()
    }

    @Sample
    fun setPan() {
        val audio = Audio(AudioConfig(sampleRate = 8000))
        val tone = Sound(AudioSamples(FloatArray(8000) { 1f }, channels = 1, sampleRate = 8000))
        val voice = audio.play(tone, pan = -1f)
        audio.setPan(voice, 1f)
        val output = FloatArray(2 * 64)
        audio.renderer.render(output, 64)
        // The change ramps across the first block, then holds: fully right.
        audio.renderer.render(output, 64)
        assertPrints(output[0], "0.0")
        assertPrints(output[1], "1.0")
        audio.close()
    }

    @Sample
    fun stopAll() {
        val audio = Audio(AudioConfig(sampleRate = 8000))
        val hum = Sound(AudioSamples(FloatArray(8000) { 0.1f }, channels = 1, sampleRate = 8000))
        val first = audio.play(hum)
        val second = audio.play(hum)
        audio.stopAll()
        audio.renderer.render(FloatArray(20), 10)
        assertFalse(audio.isPlaying(first))
        assertFalse(audio.isPlaying(second))
        audio.close()
    }

    @Sample
    fun renderer() {
        val audio = Audio(AudioConfig(sampleRate = 8000))
        val click = Sound(AudioSamples(floatArrayOf(1f, 1f), channels = 1, sampleRate = 8000))
        audio.play(click, pan = 1f)
        // Two stereo frames, left then right: the click is fully right.
        val output = FloatArray(4)
        audio.renderer.render(output, 2)
        assertPrints(output.toList(), "[0.0, 1.0, 0.0, 1.0]")
        audio.close()
    }
}
