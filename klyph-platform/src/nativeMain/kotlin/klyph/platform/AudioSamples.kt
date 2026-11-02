package klyph.platform

import klyph.KlyphException
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memcpy
import sdl3.SDL_AUDIO_F32
import sdl3.SDL_AudioSpec
import sdl3.SDL_ConvertAudioSamples
import sdl3.SDL_IOFromConstMem
import sdl3.SDL_LoadWAV_IO
import sdl3.SDL_free
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Decoded audio: [frameCount] frames of 32-bit float samples, each frame holding one value per
 * channel, played at [sampleRate] frames per second. Samples are nominally in -1..1.
 *
 * This constructor copies [samples], which must be interleaved by channel: for stereo, left then
 * right for each frame. Decode a WAV or Ogg Vorbis file with [AudioSamples.decode] instead.
 *
 * @throws IllegalArgumentException if [channels] is not 1 or 2, [sampleRate] is not positive, or
 *   the size of [samples] is not a multiple of [channels].
 *
 * @sample samples.platform.Audio.get
 */
public class AudioSamples private constructor(
    /** Returns the number of interleaved channels in each frame, 1 or 2. */
    public val channels: Int,
    /** Returns the number of frames per second the samples are meant to play at. */
    public val sampleRate: Int,
    /**
     * Interleaved samples, `frameCount * channels` values. klyph's mixer reads them through [get],
     * which inlines to a plain array read; nothing may write to them after construction.
     */
    @PublishedApi internal val samples: FloatArray,
) {
    // The public constructor takes its parameters in a different order so it can copy the array;
    // decoders hand over a freshly built array through the private one without the copy.
    public constructor(samples: FloatArray, channels: Int, sampleRate: Int) :
        this(channels, sampleRate, samples.copyOf())

    init {
        require(channels in 1..2) { "Channel count $channels is not 1 or 2." }
        require(sampleRate > 0) { "Sample rate $sampleRate is not positive." }
        require(samples.size % channels == 0) {
            "Sample count ${samples.size} is not a multiple of the channel count $channels."
        }
    }

    /**
     * Returns a copy of the samples, interleaved by channel: [frameCount] frames of [channels]
     * values each, meant to play at [sampleRate]. Changing the copy does not change the receiver.
     *
     * @sample samples.platform.Audio.toFloatArray
     */
    public fun toFloatArray(): FloatArray = samples.copyOf()

    /** Returns the number of frames, each holding one sample per channel. */
    public val frameCount: Int = samples.size / channels

    /** Returns how long the samples last when played at [sampleRate], rounded down to the nanosecond. */
    public val duration: Duration = (frameCount.toLong() * 1_000_000_000L / sampleRate).nanoseconds

    /**
     * Returns the sample of [channel] in frame [frame]. Channel 0 is left, or the only channel of
     * mono audio; channel 1 is right.
     *
     * @throws IndexOutOfBoundsException if [frame] is outside `0 until frameCount` or [channel] is
     *   outside `0 until channels`.
     *
     * @sample samples.platform.Audio.get
     */
    @Suppress("NOTHING_TO_INLINE")
    public inline operator fun get(frame: Int, channel: Int): Float {
        if (frame !in 0..<frameCount || channel !in 0..<channels) throwSampleOutOfBounds(frame, channel)
        return samples[frame * channels + channel]
    }

    @PublishedApi
    internal fun throwSampleOutOfBounds(frame: Int, channel: Int): Nothing =
        throw IndexOutOfBoundsException(
            "Sample at frame $frame, channel $channel is out of bounds for $frameCount frames of $channels channels.",
        )

    /** Returns the format as `AudioSamples(<channels> ch, <rate> Hz, <frames> frames)`. */
    override fun toString(): String = "AudioSamples($channels ch, $sampleRate Hz, $frameCount frames)"

    public companion object {
        private const val BYTES_PER_FLOAT = 4

        /** Frames read at a time while checking for audio past the length an Ogg file records. */
        private const val VORBIS_BLOCK_FRAMES = 4096

        /**
         * Decodes a WAV or Ogg Vorbis file held in [bytes], recognised by its header, into float
         * samples at the file's own sample rate.
         *
         * WAV files in any format SDL reads are converted to float. Files with more than two
         * channels are mixed down to stereo; Ogg Vorbis files with more than two channels are
         * rejected. An Ogg file cut short decodes up to the cut. [bytes] is only read during the
         * call and never retained.
         *
         * Has no sample because it needs a sound file.
         *
         * @throws AudioException if [bytes] is empty, is neither a WAV nor an Ogg Vorbis file, is
         *   corrupt, or is too long to hold in one array.
         */
        public fun decode(bytes: ByteArray): AudioSamples = decodeOrElse(
            bytes,
            onSuccess = { it },
            onFailure = { reason -> throw AudioException("Audio data could not be decoded: $reason.") },
        )

        /**
         * Decodes a WAV or Ogg Vorbis file held in [bytes], recognised by its header, into float
         * samples at the file's own sample rate, or returns `null` when [bytes] is empty, is neither
         * a WAV nor an Ogg Vorbis file, is corrupt, or is too long to hold in one array.
         *
         * Conversion follows [decode]. [bytes] is only read during the call and never retained.
         *
         * @sample samples.platform.Audio.decodeOrNull
         */
        public fun decodeOrNull(bytes: ByteArray): AudioSamples? = decodeOrElse(
            bytes,
            onSuccess = { it },
            onFailure = { null },
        )

        private inline fun <R> decodeOrElse(
            bytes: ByteArray,
            onSuccess: (AudioSamples) -> R,
            onFailure: (reason: String) -> R,
        ): R {
            if (bytes.isEmpty()) return onFailure("data is empty")
            val samples = when {
                bytes.hasTag(0, "RIFF") && bytes.hasTag(8, "WAVE") -> decodeWav(bytes)
                bytes.hasTag(0, "OggS") -> decodeVorbis(bytes)
                else -> "it is neither a WAV nor an Ogg Vorbis file"
            }
            return if (samples is AudioSamples) onSuccess(samples) else onFailure(samples as String)
        }

        private fun ByteArray.hasTag(offset: Int, tag: String): Boolean {
            if (size < offset + tag.length) return false
            for (i in tag.indices) if (this[offset + i] != tag[i].code.toByte()) return false
            return true
        }

        /** Returns the decoded [AudioSamples], or the reason for the failure as a [String]. */
        private fun decodeWav(bytes: ByteArray): Any = memScoped {
            val source = alloc<SDL_AudioSpec>()
            val sourceData = alloc<CPointerVar<UByteVar>>()
            val sourceLength = alloc<UIntVar>()
            val loaded = bytes.usePinned { pinned ->
                // closeio = true: SDL closes the stream on success and on failure.
                val io = SDL_IOFromConstMem(pinned.addressOf(0), bytes.size.convert()) ?: return sdlErrorText()
                SDL_LoadWAV_IO(io, true, source.ptr, sourceData.ptr, sourceLength.ptr)
            }
            if (!loaded) return sdlErrorText()
            val sourceBuffer = sourceData.value
            try {
                val channels = minOf(source.channels, 2)
                if (sourceLength.value == 0u || sourceBuffer == null) {
                    return AudioSamples(channels, source.freq, FloatArray(0))
                }
                if (sourceLength.value > Int.MAX_VALUE.toUInt()) {
                    return "it holds more than ${Int.MAX_VALUE} bytes of audio"
                }
                val target = alloc<SDL_AudioSpec>()
                target.format = SDL_AUDIO_F32
                target.channels = channels
                target.freq = source.freq
                val targetData = alloc<CPointerVar<UByteVar>>()
                val targetLength = alloc<IntVar>()
                val converted = SDL_ConvertAudioSamples(
                    source.ptr,
                    sourceBuffer,
                    sourceLength.value.toInt(),
                    target.ptr,
                    targetData.ptr,
                    targetLength.ptr,
                )
                if (!converted) return sdlErrorText()
                val targetBuffer = targetData.value ?: return AudioSamples(channels, source.freq, FloatArray(0))
                try {
                    // Whole frames only, in case the converter ever hands back a partial one.
                    val floats = targetLength.value / BYTES_PER_FLOAT / channels * channels
                    val samples = FloatArray(floats)
                    if (floats > 0) {
                        samples.usePinned { pinned ->
                            memcpy(pinned.addressOf(0), targetBuffer, (floats.toLong() * BYTES_PER_FLOAT).convert())
                        }
                    }
                    AudioSamples(channels, source.freq, samples)
                } finally {
                    SDL_free(targetBuffer)
                }
            } finally {
                SDL_free(sourceBuffer)
            }
        }

        /** Returns the decoded [AudioSamples], or the reason for the failure as a [String]. */
        private fun decodeVorbis(bytes: ByteArray): Any = VorbisStream.openOrElse(
            bytes,
            onSuccess = { stream -> stream.use { readAll(it) } },
            onFailure = { reason -> reason },
        )

        /** Reads [stream] to the end. Returns [AudioSamples], or the reason for the failure as a [String]. */
        private fun readAll(stream: VorbisStream): Any {
            val channels = stream.channels
            val maxFrames = Int.MAX_VALUE / channels
            if (stream.frameCount > maxFrames) return "it holds more than $maxFrames frames"
            // The length the file records is usually exact, so decode straight into an array of
            // that size and only grow it when a probe read finds audio past the end.
            var capacity = stream.frameCount.toInt().coerceAtLeast(VORBIS_BLOCK_FRAMES)
            var samples = FloatArray(capacity * channels)
            var frames = 0
            var probe: FloatArray? = null
            while (true) {
                if (frames < capacity) {
                    val read = stream.readInto(samples, destinationOffset = frames * channels, capacity - frames)
                    if (read == 0) break
                    frames += read
                    continue
                }
                val block = probe ?: FloatArray(VORBIS_BLOCK_FRAMES * channels).also { probe = it }
                val extra = stream.read(block, VORBIS_BLOCK_FRAMES)
                if (extra == 0) break
                if (frames > maxFrames - extra) return "it holds more than $maxFrames frames"
                capacity = maxOf(frames + extra, minOf(capacity.toLong() * 2, maxFrames.toLong()).toInt())
                samples = samples.copyOf(capacity * channels)
                block.copyInto(samples, destinationOffset = frames * channels, endIndex = extra * channels)
                frames += extra
            }
            if (frames < capacity) samples = samples.copyOf(frames * channels)
            return AudioSamples(channels, stream.sampleRate, samples)
        }
    }
}

/**
 * Thrown when audio data cannot be decoded or a sound file cannot be opened. The message gives the
 * decoder's reason, or names the file when a loader wraps an earlier [AudioException] as [cause].
 */
public class AudioException(message: String, cause: Throwable? = null) : KlyphException(message, cause)
