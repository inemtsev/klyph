package samples.audio

import klyph.audio.AudioConfig
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith

class AudioConfigs {
    @Sample
    fun audioConfig() {
        val config = AudioConfig(maxVoices = 64)
        assertPrints(config.maxVoices, "64")
        assertPrints(config.sampleRate, "48000")
        assertPrints(config, "AudioConfig(enabled=true, maxVoices=64, sampleRate=48000, bufferFrames=1024)")
        assertFailsWith<IllegalArgumentException> { AudioConfig(maxVoices = 0) }
    }
}
