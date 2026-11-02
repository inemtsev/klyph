package samples.audio

import klyph.audio.Sound
import klyph.platform.AudioSamples
import samples.Sample
import samples.assertPrints

class Sounds {
    @Sample
    fun sound() {
        // A game loads sounds with app.assets.sound("shot.wav"); this one is generated in code.
        val samples = AudioSamples(FloatArray(4410) { if (it % 100 < 50) 0.2f else -0.2f }, channels = 1, sampleRate = 44100)
        val square = Sound(samples)
        assertPrints(square.frameCount, "4410")
        assertPrints(square.duration, "100ms")
        assertPrints(square, "Sound(frames=4410, channels=1, sampleRate=44100, duration=100ms)")
    }
}
