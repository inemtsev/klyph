package klyph.platform

import cnames.structs.SDL_AudioStream
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import sdl3.SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK
import sdl3.SDL_AUDIO_F32
import sdl3.SDL_AudioSpec
import sdl3.SDL_AudioStreamDevicePaused
import sdl3.SDL_DestroyAudioStream
import sdl3.SDL_HINT_AUDIO_DEVICE_SAMPLE_FRAMES
import sdl3.SDL_OpenAudioDeviceStream
import sdl3.SDL_PauseAudioStreamDevice
import sdl3.SDL_PutAudioStreamData
import sdl3.SDL_ResumeAudioStreamDevice
import sdl3.SDL_SetHint
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Produces audio for an [AudioDevice]. klyph's mixer implements it.
 *
 * [render] runs on SDL's audio thread, not the thread that opened the device. It must not
 * allocate, block or wait on the main thread: a late buffer is heard as a click.
 */
@KlyphPlatformApi
public fun interface AudioRenderer {
    /**
     * Writes [frames] frames of stereo audio into the start of [output], interleaved left then
     * right, so `frames * 2` values in all. Every one of those values must be written; silence is
     * 0. [output] is reused between calls and holds at least [AudioDevice.bufferFrames] frames.
     */
    public fun render(output: FloatArray, frames: Int)
}

/**
 * The default playback device, fed with 32-bit float stereo frames at [sampleRate] by an
 * [AudioRenderer]. SDL converts to whatever the hardware wants and follows changes of the default
 * device by itself.
 *
 * Open one with [AudioDevice.open] after [Sdl.init] with [Subsystem.AUDIO]; it starts playing at
 * once. [close] it before [Sdl.quit].
 *
 * Threading: the renderer runs on SDL's audio thread. An exception it throws is caught there,
 * kept for [failureOrNull], and the device plays silence from then on, so a broken renderer
 * cannot crash the process. Every other function belongs to the thread that opened the device.
 *
 * Has no sample because it needs an audio device.
 */
@KlyphPlatformApi
@OptIn(ExperimentalAtomicApi::class)
public class AudioDevice private constructor(
    private val stream: CPointer<SDL_AudioStream>,
    private val stateRef: StableRef<CallbackState>,
    /** Returns the frame rate the renderer produces at. SDL resamples it to the device's own rate. */
    public val sampleRate: Int,
    /** Returns the largest number of frames the renderer is asked for in one call. */
    public val bufferFrames: Int,
) : AutoCloseable {
    private val state: CallbackState = stateRef.get()
    private var isClosed = false

    /**
     * Returns `true` while playback is paused by [pause], and after [close].
     */
    public val isPaused: Boolean
        get() = isClosed || SDL_AudioStreamDevicePaused(stream)

    /**
     * Returns the first exception the renderer threw on the audio thread, or `null` when it has
     * thrown none. Once it is set, the device plays silence until it is closed.
     */
    public fun failureOrNull(): Throwable? = state.failure.load()

    /**
     * Stops calling the renderer and plays silence until [resume]. Does nothing when already
     * paused.
     *
     * @throws IllegalStateException if the device is closed.
     * @throws SdlException if SDL cannot pause the device.
     */
    public fun pause() {
        check(!isClosed) { "Audio device is closed." }
        if (!SDL_PauseAudioStreamDevice(stream)) sdlFailure("SDL_PauseAudioStreamDevice")
    }

    /**
     * Starts calling the renderer again after [pause]. Does nothing when already playing.
     *
     * @throws IllegalStateException if the device is closed.
     * @throws SdlException if SDL cannot resume the device.
     */
    public fun resume() {
        check(!isClosed) { "Audio device is closed." }
        if (!SDL_ResumeAudioStreamDevice(stream)) sdlFailure("SDL_ResumeAudioStreamDevice")
    }

    /**
     * Stops playback and releases the device. When it returns, the renderer is no longer running
     * and will not be called again. Calling it again does nothing.
     */
    override fun close() {
        if (isClosed) return
        isClosed = true
        // SDL waits for a running callback before it destroys the stream, so nothing below can
        // race with the audio thread.
        SDL_DestroyAudioStream(stream)
        state.pinned.unpin()
        stateRef.dispose()
    }

    /** Returns the format as `AudioDevice(<rate> Hz, <frames> frames)`, with `closed` once closed. */
    override fun toString(): String =
        "AudioDevice($sampleRate Hz, $bufferFrames frames${if (isClosed) ", closed" else ""})"

    /** What the audio thread sees: the renderer, a reusable output buffer, and the first failure. */
    internal class CallbackState(private val renderer: AudioRenderer, private val bufferFrames: Int) {
        private val buffer = FloatArray(bufferFrames * CHANNELS)

        /** Pinned for the life of the device, so a callback allocates nothing to hand it to SDL. */
        val pinned: Pinned<FloatArray> = buffer.pin()
        val failure: AtomicReference<Throwable?> = AtomicReference(null)

        /** Feeds [stream] at least [bytes] bytes, rendered in chunks of at most [bufferFrames]. */
        fun fill(stream: CPointer<SDL_AudioStream>, bytes: Int) {
            var remaining = (bytes + BYTES_PER_FRAME - 1) / BYTES_PER_FRAME
            val address = pinned.addressOf(0)
            while (remaining > 0) {
                val frames = minOf(remaining, bufferFrames)
                render(frames)
                SDL_PutAudioStreamData(stream, address, frames * BYTES_PER_FRAME)
                remaining -= frames
            }
        }

        private fun render(frames: Int) {
            if (failure.load() == null) {
                try {
                    renderer.render(buffer, frames)
                    return
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                }
            }
            buffer.fill(0f, 0, frames * CHANNELS)
        }
    }

    /** Holds the factory that opens the playback device. */
    public companion object {
        private const val CHANNELS = 2
        private const val BYTES_PER_FRAME = CHANNELS * 4

        /** The largest [bufferFrames] accepted; about 1.4 s at 48 kHz. */
        private const val MAX_BUFFER_FRAMES = 65536

        /**
         * Opens the default playback device for float stereo at [sampleRate] frames per second and
         * starts playing at once, calling [renderer] on SDL's audio thread for at most
         * [bufferFrames] frames at a time. Pass the `Int` parameters by name.
         *
         * [bufferFrames] is also passed to SDL as the device buffer size it should use, which sets
         * the latency. SDL may pick a different size, and ignores the request when the hardware
         * device is already open. [sampleRate] must be positive and [bufferFrames] in 1..65536.
         *
         * Has no sample because it needs an audio device.
         *
         * @throws IllegalArgumentException if [sampleRate] is not positive or [bufferFrames] is
         *   outside 1..65536.
         * @throws SdlException if there is no playback device, the audio subsystem is not started,
         *   or SDL cannot open or start the device.
         */
        public fun open(sampleRate: Int, bufferFrames: Int, renderer: AudioRenderer): AudioDevice =
            when (val result = openOrReason(sampleRate, bufferFrames, renderer)) {
                is AudioDevice -> result
                else -> throw SdlException(result as String)
            }

        /**
         * Opens the default playback device like [open], or returns `null` when there is no
         * playback device, the audio subsystem is not started, or SDL cannot open or start the
         * device.
         *
         * Has no sample because it needs an audio device.
         *
         * @throws IllegalArgumentException if [sampleRate] is not positive or [bufferFrames] is
         *   outside 1..65536.
         */
        public fun openOrNull(sampleRate: Int, bufferFrames: Int, renderer: AudioRenderer): AudioDevice? =
            openOrReason(sampleRate, bufferFrames, renderer) as? AudioDevice

        /** Returns the open [AudioDevice], or the [SdlException] message for the failure as a [String]. */
        private fun openOrReason(sampleRate: Int, bufferFrames: Int, renderer: AudioRenderer): Any {
            require(sampleRate > 0) { "Sample rate $sampleRate is not positive." }
            require(bufferFrames in 1..MAX_BUFFER_FRAMES) {
                "Buffer size $bufferFrames frames is outside 1..$MAX_BUFFER_FRAMES."
            }
            SDL_SetHint(SDL_HINT_AUDIO_DEVICE_SAMPLE_FRAMES, bufferFrames.toString())
            val state = CallbackState(renderer, bufferFrames)
            val stateRef = StableRef.create(state)
            val stream = memScoped {
                val spec = alloc<SDL_AudioSpec>()
                spec.format = SDL_AUDIO_F32
                spec.channels = CHANNELS
                spec.freq = sampleRate
                SDL_OpenAudioDeviceStream(SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK, spec.ptr, audioCallback, stateRef.asCPointer())
            }
            if (stream == null) {
                val reason = sdlFailureMessage("SDL_OpenAudioDeviceStream")
                state.pinned.unpin()
                stateRef.dispose()
                return reason
            }
            val device = AudioDevice(stream, stateRef, sampleRate, bufferFrames)
            // Devices opened this way start paused.
            if (!SDL_ResumeAudioStreamDevice(stream)) {
                val reason = sdlFailureMessage("SDL_ResumeAudioStreamDevice")
                device.close()
                return reason
            }
            return device
        }

        /**
         * Called by SDL on its audio thread whenever the stream needs `additionalAmount` more bytes.
         * Nothing may escape into C, so every exception stops here.
         */
        private val audioCallback = staticCFunction {
                userdata: COpaquePointer?, stream: CPointer<SDL_AudioStream>?, additionalAmount: Int, _: Int ->
            if (userdata != null && stream != null && additionalAmount > 0) {
                try {
                    userdata.asStableRef<CallbackState>().get().fill(stream, additionalAmount)
                } catch (e: Throwable) {
                    // fill already turns renderer failures into silence; this guards SDL itself.
                }
            }
        }
    }
}
