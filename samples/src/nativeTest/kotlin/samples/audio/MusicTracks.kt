package samples.audio

import klyph.audio.Audio
import klyph.audio.AudioConfig
import klyph.audio.MusicTrack
import klyph.audio.PcmStream
import samples.Sample
import samples.assertPrints
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration

class MusicTracks {
    @Sample
    fun musicTrack() {
        // An endless 2 kHz sine, generated as it plays. Each play opens a new stream.
        val sine = MusicTrack(Duration.INFINITE, channels = 1, sampleRate = 8000) {
            object : PcmStream {
                private var frame = 0
                override val channels: Int = 1
                override val sampleRate: Int = 8000

                override fun read(output: FloatArray, frames: Int): Int {
                    for (i in 0 until frames) output[i] = sin(2 * PI * 2000 * (frame + i) / 8000).toFloat()
                    frame += frames
                    return frames
                }

                override fun rewind() {
                    frame = 0
                }

                override fun close() {}
            }
        }
        assertPrints(sine, "MusicTrack(duration=Infinity, channels=1, sampleRate=8000)")

        val audio = Audio(AudioConfig(sampleRate = 8000))
        audio.music.play(sine)
        // Two stereo frames, left then right: the sine's first two samples, 0 and 1, on both sides.
        val output = FloatArray(4)
        audio.renderer.render(output, 2)
        assertPrints(output.toList(), "[0.0, 0.0, 1.0, 1.0]")
        audio.close()
    }

    @Sample
    fun fromOggOrNull() {
        // A game reads the bytes of an .ogg file; these three bytes are not one.
        assertPrints(MusicTrack.fromOggOrNull(byteArrayOf(1, 2, 3)), "null")
    }
}
