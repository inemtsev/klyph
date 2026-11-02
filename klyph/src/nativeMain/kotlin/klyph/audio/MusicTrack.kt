package klyph.audio

import klyph.platform.AudioException
import klyph.platform.VorbisStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A music track that is decoded a block at a time while it plays, ready for [Music.play].
 *
 * The track opens a new [PcmStream] through [open] each time it starts playing, so it can be played
 * again after it stops, and a long track costs little memory. Load an Ogg Vorbis file with
 * [MusicTrack.fromOgg] or `Assets.music`; for procedural music or another decoder, pass a function
 * that opens your own [PcmStream]. Every stream [open] returns must have [channels] channels at
 * [sampleRate].
 *
 * [channels] must be 1 or 2, [sampleRate] positive and [duration] not negative.
 *
 * [toString] gives the duration, channels and rate, for example
 * `MusicTrack(duration=1m 32s, channels=2, sampleRate=44100)`.
 *
 * @throws IllegalArgumentException if [channels], [sampleRate] or [duration] is out of range.
 *
 * @sample samples.audio.MusicTracks.musicTrack
 */
public class MusicTrack(
    /**
     * How long the track plays from start to end, [Duration.INFINITE] for an endless stream, or
     * [Duration.ZERO] when the length is unknown. Only shown to callers; the stream decides when
     * the track ends.
     */
    public val duration: Duration,
    /** Number of interleaved channels: 1 for mono, 2 for stereo. */
    public val channels: Int,
    /** Frames per second of the track's data. */
    public val sampleRate: Int,
    private val open: () -> PcmStream,
) {
    init {
        require(channels == 1 || channels == 2) { "Channel count $channels is not 1 or 2." }
        require(sampleRate > 0) { "Sample rate $sampleRate is not positive." }
        require(!duration.isNegative()) { "Duration $duration is negative." }
    }

    /**
     * Opens a new stream at the start of the track. Main thread only; the caller closes it.
     *
     * @throws IllegalStateException if the stream's channels or sample rate differ from the track's.
     */
    internal fun openStream(): PcmStream {
        val stream = open()
        if (stream.channels != channels || stream.sampleRate != sampleRate) {
            stream.close()
            throw IllegalStateException(
                "Stream of $this has ${stream.channels} channels at ${stream.sampleRate} Hz, not $channels at $sampleRate Hz.",
            )
        }
        return stream
    }

    override fun toString(): String = "MusicTrack(duration=$duration, channels=$channels, sampleRate=$sampleRate)"

    public companion object {
        /**
         * Returns a track that streams the Ogg Vorbis file held in [bytes], mono or stereo.
         *
         * The track keeps its own copy of [bytes], so the caller may reuse the array, and decodes
         * it a block at a time on the audio thread while it plays. Its [duration] is
         * [Duration.ZERO] when the file does not record its length.
         *
         * Has no sample because it needs an Ogg Vorbis file.
         *
         * @throws AudioException if [bytes] is empty, is not an Ogg Vorbis file, is corrupt, or has
         *   more than two channels.
         */
        public fun fromOgg(bytes: ByteArray): MusicTrack {
            val data = bytes.copyOf()
            // Opened once here to check the file and read its format; each play opens its own.
            return VorbisStream.open(data).use { trackOf(data, it) }
        }

        /**
         * Returns a track that streams the Ogg Vorbis file held in [bytes] as [fromOgg] does, or
         * `null` if [bytes] is empty, is not an Ogg Vorbis file, is corrupt, or has more than two
         * channels.
         *
         * @sample samples.audio.MusicTracks.fromOggOrNull
         */
        public fun fromOggOrNull(bytes: ByteArray): MusicTrack? {
            val data = bytes.copyOf()
            return VorbisStream.openOrNull(data)?.use { trackOf(data, it) }
        }

        /** Returns a track over [data], which [probe] was opened on, with [probe]'s format and length. */
        private fun trackOf(data: ByteArray, probe: VorbisStream): MusicTrack {
            val duration = (probe.frameCount * NANOS_PER_SECOND / probe.sampleRate).nanoseconds
            return MusicTrack(duration, probe.channels, probe.sampleRate) { VorbisPcmStream(VorbisStream.open(data)) }
        }
    }
}

/** Adapts a [VorbisStream] to the [PcmStream] the mixer reads. */
internal class VorbisPcmStream(private val stream: VorbisStream) : PcmStream {
    override val channels: Int get() = stream.channels

    override val sampleRate: Int get() = stream.sampleRate

    override fun read(output: FloatArray, frames: Int): Int = stream.read(output, frames)

    override fun rewind() {
        stream.rewind()
    }

    override fun close() {
        stream.close()
    }

    override fun toString(): String = "VorbisPcmStream($stream)"
}
