package klyph.audio

/**
 * Holds the settings the engine opens the audio device and the mixer with.
 *
 * Every parameter has a default, so callers pass only what they change, by name:
 * `AudioConfig(maxVoices = 64)`. [maxVoices] must be between 1 and 256, [sampleRate] between
 * 8000 and 192000, and [bufferFrames] between 64 and 16384.
 *
 * Two configs are equal when every setting is equal.
 *
 * @throws IllegalArgumentException if a value is outside its range.
 *
 * @sample samples.audio.AudioConfigs.audioConfig
 */
public class AudioConfig(
    /**
     * Where the mix goes. [AudioOutput.DEVICE], the default, opens the default playback device.
     * With [AudioOutput.NONE] the engine opens no device, `Audio.isEnabled` is `false` and every
     * audio call does nothing, which suits tests and headless runs.
     */
    public val output: AudioOutput = AudioOutput.DEVICE,
    /**
     * The most sound effects that play at once. When all are busy, a new sound replaces the one
     * that started earliest.
     */
    public val maxVoices: Int = 32,
    /** Frames per second the mixer produces. The device converts them to its own rate. */
    public val sampleRate: Int = 48_000,
    /**
     * Frames the device asks for at a time. A smaller buffer lowers latency; a larger one rides
     * out longer pauses of the main thread's garbage collector without a click.
     */
    public val bufferFrames: Int = 1024,
) {
    init {
        require(maxVoices in 1..Voice.MAX_SLOTS) { "Max voices $maxVoices is not between 1 and ${Voice.MAX_SLOTS}." }
        require(sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) {
            "Sample rate $sampleRate is not between $MIN_SAMPLE_RATE and $MAX_SAMPLE_RATE."
        }
        require(bufferFrames in MIN_BUFFER_FRAMES..MAX_BUFFER_FRAMES) {
            "Buffer frames $bufferFrames is not between $MIN_BUFFER_FRAMES and $MAX_BUFFER_FRAMES."
        }
    }

    override fun equals(other: Any?): Boolean =
        other is AudioConfig &&
            output == other.output &&
            maxVoices == other.maxVoices &&
            sampleRate == other.sampleRate &&
            bufferFrames == other.bufferFrames

    override fun hashCode(): Int {
        var result = output.hashCode()
        result = 31 * result + maxVoices
        result = 31 * result + sampleRate
        result = 31 * result + bufferFrames
        return result
    }

    /**
     * Returns every setting as `AudioConfig(output=DEVICE, maxVoices=32, ...)`, in constructor
     * order. The format may change.
     */
    override fun toString(): String =
        "AudioConfig(output=$output, maxVoices=$maxVoices, sampleRate=$sampleRate, bufferFrames=$bufferFrames)"

    private companion object {
        const val MIN_SAMPLE_RATE: Int = 8_000
        const val MAX_SAMPLE_RATE: Int = 192_000
        const val MIN_BUFFER_FRAMES: Int = 64
        const val MAX_BUFFER_FRAMES: Int = 16_384
    }
}

/** Names where [AudioConfig] sends the mix. */
public enum class AudioOutput {
    /** Opens the default playback device and plays the mix on it. */
    DEVICE,

    /** Opens no device: `Audio.isEnabled` is `false` and every audio call does nothing. */
    NONE,
}
