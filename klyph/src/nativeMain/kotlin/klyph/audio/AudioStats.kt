package klyph.audio

/**
 * Holds counters that show how the mixer keeps up, as read from [Audio.stats].
 *
 * [Audio.endFrame] takes a new snapshot, which the engine does once a frame, before
 * `Phase.INPUT`; the values do not change after that. The counts since start never decrease.
 *
 * [toString] gives every counter, for example
 * `AudioStats(activeVoices=3, stolenVoices=0, droppedCommands=0, closedStreams=1)`.
 */
public class AudioStats internal constructor(
    /** Number of voices playing a sound effect when the snapshot was taken. */
    public val activeVoices: Int,
    /** Number of sounds since start that replaced a playing one because every voice was busy. */
    public val stolenVoices: Long,
    /**
     * Number of commands since start that were dropped because the queue to the audio thread was
     * full. A dropped [Audio.play] returns [Voice.NONE].
     */
    public val droppedCommands: Long,
    /** Number of music decoders since start that the mixer finished with and the main thread closed. */
    public val closedStreams: Long,
) {
    override fun toString(): String =
        "AudioStats(activeVoices=$activeVoices, stolenVoices=$stolenVoices, droppedCommands=$droppedCommands, closedStreams=$closedStreams)"

    internal companion object {
        val EMPTY: AudioStats = AudioStats(activeVoices = 0, stolenVoices = 0L, droppedCommands = 0L, closedStreams = 0L)
    }
}
