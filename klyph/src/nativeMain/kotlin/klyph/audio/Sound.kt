package klyph.audio

import klyph.platform.AudioSamples
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A sound effect decoded fully into memory, ready for [Audio.play].
 *
 * Holds interleaved float samples in -1..1 with one or two channels at the sound's own
 * [sampleRate]; the mixer converts the rate as it plays. One sound can play on many voices at
 * once. A sound never changes after it is loaded.
 *
 * Load one from a WAV or Ogg Vorbis file with `Assets.sound`, or build one from decoded or
 * generated [AudioSamples].
 *
 * [toString] gives the frame count, channels, rate and duration, for example
 * `Sound(frames=22050, channels=1, sampleRate=44100, duration=500ms)`.
 *
 * @sample samples.audio.Sounds.sound
 */
public class Sound internal constructor(
    internal val samples: FloatArray,
    /** Number of interleaved channels: 1 for mono, 2 for stereo. */
    public val channels: Int,
    /** Frames per second of the sound's data. */
    public val sampleRate: Int,
) {
    init {
        require(channels == 1 || channels == 2) { "Channel count $channels is not 1 or 2." }
        require(sampleRate > 0) { "Sample rate $sampleRate is not positive." }
        require(samples.size % channels == 0) {
            "Sample count ${samples.size} is not a multiple of the channel count $channels."
        }
    }

    /**
     * Creates a sound from [samples], copied once into the sound, so later use of [samples] does
     * not affect it.
     *
     * @sample samples.audio.Sounds.sound
     */
    public constructor(samples: AudioSamples) : this(samples.toFloatArray(), samples.channels, samples.sampleRate)

    /** Number of frames, where a frame holds one sample per channel. */
    public val frameCount: Int = samples.size / channels

    /** How long the sound plays at its own rate, that is at pitch 1. */
    public val duration: Duration = (frameCount.toLong() * NANOS_PER_SECOND / sampleRate).nanoseconds

    override fun toString(): String =
        "Sound(frames=$frameCount, channels=$channels, sampleRate=$sampleRate, duration=$duration)"
}

internal const val NANOS_PER_SECOND: Long = 1_000_000_000L
