package klyph.platform

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.posix.memcpy
import vorbis.stb_vorbis
import vorbis.stb_vorbis_close
import vorbis.stb_vorbis_get_info
import vorbis.stb_vorbis_get_samples_float_interleaved
import vorbis.stb_vorbis_open_memory
import vorbis.stb_vorbis_seek_start
import vorbis.stb_vorbis_stream_length_in_samples

/**
 * Decodes an Ogg Vorbis file held in memory a block at a time, for music that is too long to
 * decode up front. Open one with [VorbisStream.open], pull interleaved float frames with [read],
 * start over with [rewind], and [close] it to free the decoder.
 *
 * The stream keeps its own copy of the file in native memory, so the caller's array may be reused
 * once [open] returns.
 *
 * Threading: [open] and [close] run on the thread that owns the stream. Between them, [read] and
 * [rewind] may be called from one other thread at a time, such as the audio thread, and must not
 * be called from two threads at once. Call [close] only after that thread has stopped using the
 * stream.
 *
 * Has no sample because it needs an Ogg Vorbis file.
 */
public class VorbisStream private constructor(
    private val data: CPointer<UByteVar>,
    private val decoder: CPointer<stb_vorbis>,
    /** Returns the number of interleaved channels in each frame, 1 or 2. */
    public val channels: Int,
    /** Returns the number of frames per second the file was encoded at. */
    public val sampleRate: Int,
    /** Returns the number of frames in the whole file, or 0 when the file does not record it. */
    public val frameCount: Long,
) : AutoCloseable {
    private var isClosed = false

    /**
     * Decodes up to [frames] frames into the start of [output], interleaved by channel, and returns
     * how many frames were written. Returns fewer than [frames] only at the end of the file, and 0
     * once the end has been reached.
     *
     * Allocates nothing beyond pinning [output] for the call, so it is safe on the audio thread.
     *
     * @throws IllegalArgumentException if [frames] is negative or [output] holds fewer than
     *   `frames * channels` values.
     * @throws IllegalStateException if the stream is closed.
     */
    public fun read(output: FloatArray, frames: Int): Int = readInto(output, destinationOffset = 0, frames)

    internal fun readInto(output: FloatArray, destinationOffset: Int, frames: Int): Int {
        require(frames >= 0) { "Frame count $frames is less than zero." }
        require(destinationOffset + frames.toLong() * channels <= output.size) {
            "Output of size ${output.size} cannot hold $frames frames of $channels channels at offset $destinationOffset."
        }
        check(!isClosed) { "Vorbis stream is closed." }
        if (frames == 0) return 0
        return output.usePinned { pinned ->
            stb_vorbis_get_samples_float_interleaved(decoder, channels, pinned.addressOf(destinationOffset), frames * channels)
        }
    }

    /**
     * Moves back to the first frame, so the next [read] starts the file over.
     *
     * @throws IllegalStateException if the stream is closed.
     */
    public fun rewind() {
        check(!isClosed) { "Vorbis stream is closed." }
        stb_vorbis_seek_start(decoder)
    }

    /**
     * Frees the decoder and the copy of the file. Calling it again does nothing.
     */
    override fun close() {
        if (isClosed) return
        isClosed = true
        stb_vorbis_close(decoder)
        nativeHeap.free(data.rawValue)
    }

    /** Returns the format as `VorbisStream(<channels> ch, <rate> Hz, <frames> frames)`. */
    override fun toString(): String = "VorbisStream($channels ch, $sampleRate Hz, $frameCount frames)"

    public companion object {
        /**
         * Opens the Ogg Vorbis file held in [bytes] for streaming. The stream copies [bytes], so the
         * caller may reuse the array.
         *
         * Has no sample because it needs an Ogg Vorbis file.
         *
         * @throws AudioException if [bytes] is empty, is not an Ogg Vorbis file, is corrupt, or has
         *   more than two channels.
         */
        public fun open(bytes: ByteArray): VorbisStream = openOrElse(
            bytes,
            onSuccess = { it },
            onFailure = { reason -> throw AudioException("Vorbis data could not be opened: $reason.") },
        )

        /**
         * Opens the Ogg Vorbis file held in [bytes] for streaming, or returns `null` when [bytes] is
         * empty, is not an Ogg Vorbis file, is corrupt, or has more than two channels. The stream
         * copies [bytes], so the caller may reuse the array.
         *
         * @sample samples.platform.Audio.openOrNull
         */
        public fun openOrNull(bytes: ByteArray): VorbisStream? = openOrElse(
            bytes,
            onSuccess = { it },
            onFailure = { null },
        )

        internal fun <R> openOrElse(
            bytes: ByteArray,
            onSuccess: (VorbisStream) -> R,
            onFailure: (reason: String) -> R,
        ): R {
            if (bytes.isEmpty()) return onFailure("data is empty")
            val data = nativeHeap.allocArray<UByteVar>(bytes.size)
            bytes.usePinned { pinned -> memcpy(data, pinned.addressOf(0), bytes.size.convert()) }
            val stream = memScoped {
                val error = alloc<IntVar>()
                val decoder = stb_vorbis_open_memory(data, bytes.size, error.ptr, null)
                if (decoder == null) {
                    nativeHeap.free(data.rawValue)
                    return onFailure("it is not a valid Ogg Vorbis file (stb_vorbis error ${error.value})")
                }
                val channels = stb_vorbis_get_info(decoder).useContents { channels }
                val sampleRate = stb_vorbis_get_info(decoder).useContents { sample_rate.toInt() }
                if (channels !in 1..2 || sampleRate <= 0) {
                    stb_vorbis_close(decoder)
                    nativeHeap.free(data.rawValue)
                    return onFailure("it has $channels channels at $sampleRate Hz, and only mono and stereo are supported")
                }
                val frameCount = stb_vorbis_stream_length_in_samples(decoder).toLong()
                VorbisStream(data, decoder, channels, sampleRate, frameCount)
            }
            return onSuccess(stream)
        }
    }
}
