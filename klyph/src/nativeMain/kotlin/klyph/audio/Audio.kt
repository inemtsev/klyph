@file:OptIn(ExperimentalAtomicApi::class)

package klyph.audio

import klyph.platform.AudioRenderer
import klyph.platform.KlyphPlatformApi
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.roundToLong
import kotlin.time.Duration

/**
 * Plays sound effects and music. Read it through `App.audio`.
 *
 * [play] starts a [Sound] on a free voice and returns a [Voice] handle to change or stop it. The
 * mixer has a fixed number of voices, `AudioConfig.maxVoices`; when every one is busy, the new
 * sound replaces the one that started earliest, and the replaced handle stops working. Music
 * plays on its own channel through [music].
 *
 * Calls return at once and never wait for the audio thread. Each call queues a command that the
 * audio thread applies before it mixes its next block, a few milliseconds later; [isPlaying]
 * changes at that point too. If the queue is full, the command is dropped and counted in
 * [stats]. Operations on a voice that has ended, was replaced, or is [Voice.NONE] do nothing.
 *
 * When there is no audio device, or `AudioConfig.output` is [AudioOutput.NONE], [isEnabled] is `false`:
 * every call does nothing, [play] returns [Voice.NONE] and [isPlaying] returns `false`, so a game
 * runs the same without sound.
 *
 * A game uses the one instance the engine creates, `App.audio`, which the engine feeds to the
 * audio device and closes at shutdown. An instance created with the public constructor plays to no
 * device: it mixes only when something calls [renderer], which is how to mix offline, in a test,
 * or into a file.
 *
 * [toString] gives the state and the volumes, for example
 * `Audio(isEnabled=true, voices=32, masterVolume=1.0, effectsVolume=1.0, musicVolume=0.5)`.
 *
 * Main thread only, except for [renderer].
 *
 * @sample samples.audio.Audios.play
 */
public class Audio internal constructor(
    config: AudioConfig,
    commandCapacity: Int,
) : AutoCloseable {
    /**
     * Creates a mixer with the voices, rate and block size of [config], attached to no device.
     * [isEnabled] starts `true` unless [AudioConfig.output] is [AudioOutput.NONE]. Mix it by
     * calling [renderer], call [endFrame]
     * from time to time to refresh [stats] and close finished music streams, and [close] it when
     * done.
     *
     * @sample samples.audio.Audios.renderer
     */
    public constructor(config: AudioConfig = AudioConfig()) : this(config, CommandRing.DEFAULT_CAPACITY)

    /** The mixer the audio device calls from its own thread. */
    internal val mixer: Mixer = Mixer(
        sampleRate = config.sampleRate,
        maxVoices = config.maxVoices,
        bufferFrames = config.bufferFrames,
        commands = CommandRing(commandCapacity),
        retired = RetiredRing(),
    )

    private val maxVoices = config.maxVoices
    private val commands = mixer.commands
    private val generation = IntArray(maxVoices)
    private val startedAt = LongArray(maxVoices)
    private var playCount = 0L
    private var stolenVoiceCount = 0L
    private var droppedCommandCount = 0L
    private var closedStreamCount = 0L

    /** Music decoders opened and not yet closed, wherever they are: in the queue, the mixer or the retired ring. */
    internal var openStreams: Int = 0
        private set

    /**
     * `true` while calls take effect. `false` when audio was turned off in `AudioConfig`, when the
     * engine could not open an audio device for `App.audio` or its mixer failed, and after
     * [close]; then every call does nothing.
     */
    public var isEnabled: Boolean = config.output != AudioOutput.NONE
        internal set

    /**
     * The volume of everything, from 0 (silent) to 1 (full), applied on top of [effectsVolume] and
     * [musicVolume]. Starts at 1. A value outside 0..1 is clamped into it.
     *
     * @throws IllegalArgumentException if set to NaN.
     */
    public var masterVolume: Float = 1f
        set(value) {
            field = clampVolume(value)
            mixer.masterVolume.store(field)
        }

    /**
     * The volume of every sound effect, from 0 to 1, applied on top of each voice's own volume.
     * Starts at 1. A value outside 0..1 is clamped into it.
     *
     * @throws IllegalArgumentException if set to NaN.
     */
    public var effectsVolume: Float = 1f
        set(value) {
            field = clampVolume(value)
            mixer.effectsVolume.store(field)
        }

    /**
     * The volume of music, from 0 to 1, applied on top of [Music.volume]. Starts at 1. A value
     * outside 0..1 is clamped into it.
     *
     * @throws IllegalArgumentException if set to NaN.
     */
    public var musicVolume: Float = 1f
        set(value) {
            field = clampVolume(value)
            mixer.musicVolume.store(field)
        }

    /** The music channel, which plays one [MusicTrack] at a time. */
    public val music: Music = Music(this)

    /**
     * Renders the mix into a caller's buffer: applies the queued calls, then writes the given
     * number of stereo frames, interleaved left then right, from index 0, at
     * [AudioConfig.sampleRate], clamped to -1..1. Each call advances every voice and the music by
     * that many frames. It writes at most half the buffer's size in frames.
     *
     * The engine hands it to the audio device, which calls it on its own thread; do not call it on
     * `App.audio`. On an instance from the public constructor, call it yourself to mix offline, in
     * a test, or into a file, from one thread at a time. It never allocates. Like [AudioRenderer],
     * it is engine plumbing behind [KlyphPlatformApi]: a game that plays through `App.audio` never
     * calls it.
     *
     * @sample samples.audio.Audios.renderer
     */
    @KlyphPlatformApi
    public val renderer: AudioRenderer = AudioRenderer { output, frames -> mixer.render(output, frames) }

    /**
     * Counters as of the start of this frame: voices playing, voices stolen, commands dropped and
     * music decoders closed. [endFrame] refreshes them, which the engine calls once a frame.
     */
    public var stats: AudioStats = AudioStats.EMPTY
        private set

    /**
     * Starts [sound] on a voice and returns the handle that controls it.
     *
     * [volume] is from 0 to 1 and [pan] from -1 (left) through 0 (center) to 1 (right); values
     * outside are clamped. A mono sound is panned so its loudness stays even across the field; a
     * stereo sound is balanced, so pan 0 plays both channels as recorded. [pitch] scales the
     * playback speed and so the pitch: 2 plays an octave up in half the time. It must be between
     * 0.25 and 4. With [PlaybackMode.LOOP] the sound repeats until [stop] or [stopAll].
     *
     * When every voice is busy, the sound replaces the one that started earliest. Returns
     * [Voice.NONE] when audio is disabled or the command queue is full. Pass the `Float` parameters
     * by name.
     *
     * @throws IllegalArgumentException if [pitch] is outside 0.25..4, or [volume] or [pan] is NaN.
     *
     * @sample samples.audio.Audios.play
     */
    public fun play(
        sound: Sound,
        volume: Float = 1f,
        pitch: Float = 1f,
        pan: Float = 0f,
        mode: PlaybackMode = PlaybackMode.ONCE,
    ): Voice {
        requirePitch(pitch)
        val clampedVolume = clampVolume(volume)
        val clampedPan = clampPan(pan)
        if (!isEnabled) return Voice.NONE
        val free = freeSlotOrNegative()
        val slot = if (free >= 0) free else oldestSlot()
        val next = Voice.nextGeneration(generation[slot])
        if (!send(Mixer.PLAY, slot, next, mode.ordinal, clampedVolume, pitch, clampedPan, sound)) return Voice.NONE
        if (free < 0) stolenVoiceCount++
        generation[slot] = next
        startedAt[slot] = ++playCount
        return Voice.of(slot, next)
    }

    /**
     * Stops [voice] at once. Does nothing if it has already ended.
     *
     * @sample samples.audio.Audios.stop
     */
    public fun stop(voice: Voice) {
        if (isLive(voice)) send(Mixer.STOP, voice.slot, voice.generation)
    }

    /**
     * Changes the volume of [voice] to [volume], from 0 to 1; a value outside is clamped. The
     * change ramps over a few milliseconds so it does not click. Does nothing if the voice has
     * ended.
     *
     * @throws IllegalArgumentException if [volume] is NaN.
     *
     * @sample samples.audio.Audios.setVolume
     */
    public fun setVolume(voice: Voice, volume: Float) {
        val clamped = clampVolume(volume)
        if (isLive(voice)) send(Mixer.SET_VOLUME, voice.slot, voice.generation, float0 = clamped)
    }

    /**
     * Changes the playback speed of [voice] to [pitch], between 0.25 and 4, where 1 is the
     * sound's own speed. Does nothing if the voice has ended.
     *
     * @throws IllegalArgumentException if [pitch] is outside 0.25..4.
     *
     * @sample samples.audio.Audios.setPitch
     */
    public fun setPitch(voice: Voice, pitch: Float) {
        requirePitch(pitch)
        if (isLive(voice)) send(Mixer.SET_PITCH, voice.slot, voice.generation, float0 = pitch)
    }

    /**
     * Moves [voice] to [pan], from -1 (left) to 1 (right); a value outside is clamped. Does nothing
     * if the voice has ended.
     *
     * @throws IllegalArgumentException if [pan] is NaN.
     *
     * @sample samples.audio.Audios.setPan
     */
    public fun setPan(voice: Voice, pan: Float) {
        val clamped = clampPan(pan)
        if (isLive(voice)) send(Mixer.SET_PAN, voice.slot, voice.generation, float0 = clamped)
    }

    /**
     * Returns `true` while [voice] plays. Returns `false` once a [PlaybackMode.ONCE] sound has
     * reached its end, once the voice was stopped, or once another sound took its voice. A stop
     * takes effect when the audio thread mixes its next block, so this returns `true` until then.
     *
     * @sample samples.audio.Audios.play
     */
    public fun isPlaying(voice: Voice): Boolean = isEnabled && isLive(voice)

    /**
     * Stops every sound effect at once. Music keeps playing; stop it through [Music.stop].
     *
     * @sample samples.audio.Audios.stopAll
     */
    public fun stopAll() {
        if (isEnabled) send(Mixer.STOP_ALL)
    }

    override fun toString(): String =
        "Audio(isEnabled=$isEnabled, voices=$maxVoices, masterVolume=$masterVolume, effectsVolume=$effectsVolume, musicVolume=$musicVolume)"

    /**
     * Closes the music streams the mixer has finished with and refreshes [stats].
     *
     * The engine calls it on `App.audio` once a frame, before `Phase.INPUT`. On an instance from
     * the public constructor, call it yourself between calls to [renderer], such as after each
     * block.
     *
     * @sample samples.audio.Audios.endFrame
     */
    public fun endFrame() {
        closeRetired()
        val active = activeVoiceCount()
        val previous = stats
        if (previous.activeVoiceCount != active ||
            previous.stolenVoiceCount != stolenVoiceCount ||
            previous.droppedCommandCount != droppedCommandCount ||
            previous.closedStreamCount != closedStreamCount
        ) {
            stats = AudioStats(active, stolenVoiceCount, droppedCommandCount, closedStreamCount)
        }
    }

    /**
     * Shuts this instance down: applies the calls still queued, closes every music stream and sets
     * [isEnabled] to `false`, so later calls do nothing. Call it only once nothing calls
     * [renderer] any more. Calling it again has no further effect.
     *
     * The engine closes `App.audio` after the audio device, at shutdown; do not close it yourself.
     */
    override fun close() {
        mixer.releaseStreams()
        closeRetired()
        isEnabled = false
    }

    /** Closes every stream the mixer has retired. */
    internal fun closeRetired() {
        while (true) {
            val stream = mixer.retired.pollOrNull() ?: return
            openStreams--
            closedStreamCount++
            stream.close()
        }
    }

    /** Returns `true` if one more music decoder may be opened without overflowing the retired ring. */
    internal fun canOpenStream(): Boolean = openStreams < mixer.retired.capacity

    internal fun streamOpened() {
        openStreams++
    }

    /** Queues a command and returns `true`, or counts it as dropped and returns `false`. */
    internal fun send(
        kind: Int,
        int0: Int = 0,
        int1: Int = 0,
        int2: Int = 0,
        float0: Float = 0f,
        float1: Float = 0f,
        float2: Float = 0f,
        reference: Any? = null,
    ): Boolean {
        if (commands.push(kind, int0, int1, int2, float0, float1, float2, reference)) return true
        droppedCommandCount++
        return false
    }

    internal fun countDropped() {
        droppedCommandCount++
    }

    /**
     * Returns the number of output frames [duration] lasts, for fades.
     *
     * @throws IllegalArgumentException if [duration] is negative or infinite.
     */
    internal fun framesOf(duration: Duration): Int {
        require(!duration.isNegative() && duration.isFinite()) { "Fade duration $duration is negative or infinite." }
        val frames = (duration.inWholeNanoseconds.toDouble() * mixer.sampleRate / NANOS_PER_SECOND).roundToLong()
        return frames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun isLive(voice: Voice): Boolean {
        if (voice == Voice.NONE) return false
        val slot = voice.slot
        return slot < maxVoices &&
            generation[slot] == voice.generation &&
            mixer.finished.loadAt(slot) != voice.generation
    }

    private fun freeSlotOrNegative(): Int {
        for (slot in 0 until maxVoices) {
            if (mixer.finished.loadAt(slot) == generation[slot]) return slot
        }
        return -1
    }

    private fun oldestSlot(): Int {
        var oldest = 0
        for (slot in 1 until maxVoices) {
            if (startedAt[slot] < startedAt[oldest]) oldest = slot
        }
        return oldest
    }

    private fun activeVoiceCount(): Int {
        var count = 0
        for (slot in 0 until maxVoices) {
            if (mixer.finished.loadAt(slot) != generation[slot]) count++
        }
        return count
    }

    private companion object {
        const val MIN_PITCH: Float = 0.25f
        const val MAX_PITCH: Float = 4f

        fun requirePitch(pitch: Float) {
            require(pitch in MIN_PITCH..MAX_PITCH) { "Pitch $pitch is not between $MIN_PITCH and $MAX_PITCH." }
        }

        fun clampPan(pan: Float): Float {
            require(!pan.isNaN()) { "Pan is NaN." }
            return pan.coerceIn(-1f, 1f)
        }
    }
}

/**
 * Returns [volume] clamped into 0..1.
 *
 * @throws IllegalArgumentException if [volume] is NaN.
 */
internal fun clampVolume(volume: Float): Float {
    require(!volume.isNaN()) { "Volume is NaN." }
    return volume.coerceIn(0f, 1f)
}
