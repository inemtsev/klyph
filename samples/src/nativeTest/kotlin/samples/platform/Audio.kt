package samples.platform

import klyph.platform.AudioSamples
import klyph.platform.VorbisStream
import samples.Sample
import samples.assertPrints

class Audio {
    @Sample
    fun get() {
        // Two stereo frames: left then right for each.
        val samples = AudioSamples(floatArrayOf(0.1f, -0.1f, 0.5f, -0.5f), channels = 2, sampleRate = 48000)
        assertPrints(samples.frameCount, "2")
        assertPrints(samples[1, 0], "0.5")
        assertPrints(samples[1, 1], "-0.5")
    }

    @Sample
    fun toFloatArray() {
        val samples = AudioSamples(floatArrayOf(0.1f, -0.1f, 0.5f, -0.5f), channels = 2, sampleRate = 48000)
        val copy = samples.toFloatArray()
        copy[0] = 1f
        assertPrints(copy.toList(), "[1.0, -0.1, 0.5, -0.5]")
        assertPrints(samples[0, 0], "0.1")
    }

    @Sample
    fun decodeOrNull() {
        assertPrints(AudioSamples.decodeOrNull(byteArrayOf(1, 2, 3)), "null")
    }

    @Sample
    fun openOrNull() {
        assertPrints(VorbisStream.openOrNull(byteArrayOf(1, 2, 3)), "null")
    }
}
