package klyph.benchmarks

import klyph.audio.Audio
import klyph.audio.AudioConfig
import klyph.audio.MusicTrack
import klyph.audio.PcmStream
import klyph.audio.PlaybackMode
import klyph.audio.Sound
import klyph.platform.AudioSamples
import klyph.platform.Files
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Mixes 32 looping sine voices at pitches from 0.5 to 2 plus a looping music track, all at
 * 44.1 kHz resampled to 48 kHz, in 1024-frame blocks, and prints the time per block. The music is
 * a synthetic stereo stream unless `KLYPH_MUSIC` names an Ogg Vorbis file, which is then decoded
 * as it plays. Uses only klyph's public API: an [Audio] with no device, mixed through
 * [Audio.renderer].
 */
internal val mixerBenchmark = Benchmark(
    name = "mixer",
    description = "Mixes 32 voices and a music stream headless; KLYPH_SECONDS sets the audio length (default 60), " +
        "KLYPH_MUSIC=<file.ogg> streams that file as the music.",
    run = ::runMixerBenchmark,
)

private const val VOICES = 32
private const val DEVICE_RATE = 48_000
private const val SOURCE_RATE = 44_100
private const val BLOCK_FRAMES = 1024

private fun runMixerBenchmark(settings: BenchmarkSettings) {
    val audioLength = settings.timeLimit ?: 60.seconds
    val musicPath = settings.musicPath
    val track = if (musicPath == null) {
        MusicTrack(Duration.INFINITE, channels = 2, sampleRate = SOURCE_RATE) { SineStream(SOURCE_RATE, 441.0) }
    } else {
        MusicTrack.fromOgg(Files.readBytes(musicPath))
    }
    val musicName = if (musicPath == null) "synthetic stereo music" else "Ogg Vorbis music $track from $musicPath"

    Audio(AudioConfig(maxVoices = VOICES, sampleRate = DEVICE_RATE, bufferFrames = BLOCK_FRAMES)).use { audio ->
        val sine = Sound(
            AudioSamples(FloatArray(SOURCE_RATE) { sin(2 * PI * 440.0 * it / SOURCE_RATE).toFloat() * 0.1f }, channels = 1, sampleRate = SOURCE_RATE),
        )
        for (i in 0 until VOICES) {
            val pitch = 0.5f + 1.5f * i / (VOICES - 1)
            val pan = -1f + 2f * i / (VOICES - 1)
            audio.play(sine, volume = 0.5f, pitch = pitch, pan = pan, mode = PlaybackMode.LOOP)
        }
        audio.music.play(track)

        val renderer = audio.renderer
        val blocks = (audioLength.inWholeMilliseconds * DEVICE_RATE / 1000 / BLOCK_FRAMES).toInt().coerceAtLeast(1)
        val output = FloatArray(BLOCK_FRAMES * 2)
        val clock = TimeSource.Monotonic
        // Warm up caches and the first music block outside the measurement.
        repeat(16) { renderer.render(output, BLOCK_FRAMES) }

        var totalNanos = 0L
        var maxNanos = 0L
        var checksum = 0.0
        for (block in 0 until blocks) {
            val start = clock.markNow()
            renderer.render(output, BLOCK_FRAMES)
            val nanos = start.elapsedNow().inWholeNanoseconds
            totalNanos += nanos
            if (nanos > maxNanos) maxNanos = nanos
            checksum += output[block % BLOCK_FRAMES * 2]
        }
        audio.endFrame()

        val meanMicros = totalNanos / 1000.0 / blocks
        val budgetMicros = BLOCK_FRAMES * 1_000_000.0 / DEVICE_RATE
        println("Mixer: $VOICES voices + $musicName, $blocks blocks of $BLOCK_FRAMES frames at $DEVICE_RATE Hz ($audioLength of audio).")
        println(
            "per block: mean=${fixed(meanMicros, 2)}us max=${fixed(maxNanos / 1000.0, 2)}us " +
                "budget=${fixed(budgetMicros, 0)}us (${fixed(meanMicros / budgetMicros * 100, 3)}% of the block's real time)",
        )
        println("per voice per block: ${fixed(meanMicros / VOICES, 3)}us (music included in the mean)")
        println("${audio.stats}, checksum=${fixed(checksum, 3)}")
        println("No GPU stats: this benchmark opens no window and no device.")
    }
}

/**
 * An endless stereo sine at [frequency] Hz, a stand-in for a music decoder. It copies from one
 * precomputed second, so the benchmark measures the mixer rather than `sin`; a real Vorbis decoder
 * costs more than this copy, which `KLYPH_MUSIC` measures.
 */
private class SineStream(override val sampleRate: Int, frequency: Double) : PcmStream {
    override val channels: Int = 2
    private val table = FloatArray(sampleRate * 2) { (sin(2 * PI * frequency * (it / 2) / sampleRate) * 0.2).toFloat() }
    private var frame = 0

    override fun read(output: FloatArray, frames: Int): Int {
        var written = 0
        while (written < frames) {
            val count = minOf(frames - written, sampleRate - frame)
            table.copyInto(output, written * 2, frame * 2, (frame + count) * 2)
            written += count
            frame = (frame + count) % sampleRate
        }
        return frames
    }

    override fun rewind() {
        frame = 0
    }

    override fun close() {}

    override fun toString(): String = "SineStream($sampleRate Hz, frame $frame)"
}
