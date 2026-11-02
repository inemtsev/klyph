package klyph.audio

/**
 * A source of interleaved float frames at a fixed [channels] and [sampleRate] that the mixer pulls
 * a block at a time while a [MusicTrack] plays.
 *
 * klyph implements it for Ogg Vorbis files, behind [MusicTrack.fromOgg]. Implement it yourself for
 * procedural music or a decoder of another format, and pass a function that opens one to the
 * [MusicTrack] constructor.
 *
 * Threading: the main thread opens a stream when [Music.play] starts a track and hands it to the
 * mixer. From then on only the audio thread calls [read] and [rewind]; they must not allocate,
 * block or wait for the main thread, because a late block is heard as a click. When the mixer is
 * done with the stream, it hands it back, and the main thread calls [close] once, at the start of
 * a later frame or at shutdown. An exception thrown by [read] or [rewind] silences the device, and
 * the engine turns audio off.
 *
 * @sample samples.audio.MusicTracks.musicTrack
 */
public interface PcmStream {
    /** Returns the number of interleaved channels in each frame, 1 or 2. It never changes. */
    public val channels: Int

    /** Returns the number of frames per second of the stream's own data. It never changes. */
    public val sampleRate: Int

    /**
     * Writes up to [frames] frames into [output] from index 0, interleaved by channel, and returns
     * how many it wrote. Returns 0 once the stream has ended; the mixer then calls [rewind] for a
     * looping track or stops the track. [output] holds at least `frames * channels` values.
     *
     * Runs on the audio thread and must not allocate.
     */
    public fun read(output: FloatArray, frames: Int): Int

    /**
     * Moves back to the first frame, so the next [read] starts the stream again.
     *
     * Runs on the audio thread and must not allocate.
     */
    public fun rewind()

    /** Frees the stream's resources. Called once, on the main thread, after the last [read]. */
    public fun close()
}
