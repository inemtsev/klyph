@file:OptIn(ExperimentalAtomicApi::class)

package klyph.audio

import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration

/**
 * Plays one [MusicTrack] at a time, with fades. Read it through `App.audio.music`.
 *
 * The track decodes a block at a time on the audio thread, so a long track costs little memory.
 * Its loudness is [volume] times `Audio.musicVolume` times `Audio.masterVolume`. Like every audio
 * call, [play] and [stop] return at once; the audio thread applies them before it mixes its next
 * block. When audio is disabled, every call does nothing and [isPlaying] is `false`.
 *
 * [toString] gives the current track, whether it plays and the volume, for example
 * `Music(current=MusicTrack(duration=1m 32s, channels=2, sampleRate=44100), isPlaying=true, volume=1.0)`.
 *
 * Main thread only.
 *
 * @sample samples.audio.Musics.play
 */
public class Music internal constructor(private val audio: Audio) {
    private var current: MusicTrack? = null
    private var currentId = 0
    private var lastId = 0

    /**
     * Starts [track] from its beginning. With [PlaybackMode.LOOP], the default, it repeats until
     * [stop]; with [PlaybackMode.ONCE] it stops at its end.
     *
     * The track fades in from silence over [fadeIn]. If a track is already playing, that track
     * fades out over the same [fadeIn] while the new one fades in, so the two cross; with no fade
     * the old track stops at once. Playing the same track again restarts it.
     *
     * Opens the track's stream on the calling thread, which for a track from
     * [MusicTrack.fromOgg] reads the file's headers.
     *
     * @throws IllegalArgumentException if [fadeIn] is negative or infinite.
     * @throws IllegalArgumentException if the stream the track opens has other channels or another
     *   sample rate than the track.
     *
     * @sample samples.audio.Musics.play
     */
    public fun play(track: MusicTrack, mode: PlaybackMode = PlaybackMode.LOOP, fadeIn: Duration = Duration.ZERO) {
        val fadeFrames = audio.framesOf(fadeIn)
        if (!audio.isEnabled) return
        audio.closeRetired()
        if (!audio.canOpenStream()) {
            audio.countDropped()
            return
        }
        val stream = track.openStream()
        val id = if (lastId == Int.MAX_VALUE) 1 else lastId + 1
        if (!audio.send(Mixer.PLAY_MUSIC, id, mode.ordinal, fadeFrames, reference = stream)) {
            stream.close()
            return
        }
        audio.streamOpened()
        lastId = id
        current = track
        currentId = id
    }

    /**
     * Fades the playing track out over [fadeOut] and then stops it; with no fade it stops at once.
     * [isPlaying] stays `true` until the fade has finished. Does nothing when no track plays.
     *
     * @throws IllegalArgumentException if [fadeOut] is negative or infinite.
     *
     * @sample samples.audio.Musics.stop
     */
    public fun stop(fadeOut: Duration = Duration.ZERO) {
        val fadeFrames = audio.framesOf(fadeOut)
        if (isPlaying) audio.send(Mixer.STOP_MUSIC, fadeFrames)
    }

    /**
     * The volume of the music track, from 0 to 1, applied on top of `Audio.musicVolume`. Starts at
     * 1. A value outside 0..1 is clamped into it. A change takes effect over the next mixed
     * block, so it does not click; for a slow change, set it a little each frame.
     *
     * @throws IllegalArgumentException if set to NaN.
     */
    public var volume: Float = 1f
        set(value) {
            field = clampVolume(value)
            audio.mixer.trackVolume.store(field)
        }

    /**
     * `true` from [play] until the track ends: a [PlaybackMode.ONCE] track reaching its end, or a
     * [stop] whose fade has finished. Playing another track makes this describe the new one.
     *
     * The value can change between two reads in one frame without a main-thread call, because the
     * audio thread ends the track.
     */
    public val isPlaying: Boolean
        get() = audio.isEnabled && current != null && audio.mixer.musicEnded.load() != currentId

    /**
     * The track that is playing, or `null` when [isPlaying] is `false`.
     *
     * The value can change between two reads in one frame without a main-thread call, because the
     * audio thread ends the track.
     */
    public val currentOrNull: MusicTrack?
        get() = if (isPlaying) current else null

    override fun toString(): String = "Music(current=$currentOrNull, isPlaying=$isPlaying, volume=$volume)"
}
