package klyph.audio

/**
 * Holds counters that show how the mixer keeps up, as read from [Audio.stats].
 *
 * [Audio.endFrame] takes a new snapshot, which the engine does once a frame, before
 * `Phase.INPUT`; the values do not change after that. The counts since start never decrease.
 *
 * [toString] gives every counter, for example
 * `AudioStats(activeVoiceCount=3, stolenVoiceCount=0, droppedCommandCount=0, closedStreamCount=1)`.
 */
public class AudioStats internal constructor(
    /** Number of voices playing a sound effect when the snapshot was taken. */
    public val activeVoiceCount: Int,
    /** Number of sounds since start that replaced a playing one because every voice was busy. */
    public val stolenVoiceCount: Long,
    /**
     * Number of commands since start that were dropped because the queue to the audio thread was
     * full. A dropped [Audio.play] returns [Voice.NONE].
     */
    public val droppedCommandCount: Long,
    /** Number of music decoders since start that the mixer finished with and the main thread closed. */
    public val closedStreamCount: Long,
) {
    override fun toString(): String =
        "AudioStats(activeVoiceCount=$activeVoiceCount, stolenVoiceCount=$stolenVoiceCount, droppedCommandCount=$droppedCommandCount, closedStreamCount=$closedStreamCount)"

    internal companion object {
        val EMPTY: AudioStats = AudioStats(activeVoiceCount = 0, stolenVoiceCount = 0L, droppedCommandCount = 0L, closedStreamCount = 0L)
    }
}
