@file:OptIn(ExperimentalAtomicApi::class)

package klyph.audio

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicIntArray
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Mixes sound effect voices and music into interleaved float stereo on the audio thread.
 *
 * All state below the atomics belongs to the thread that calls [render]. The main thread reaches
 * it only through [commands], and reads results back through [finished], [musicEnded] and
 * [retired]. Everything is allocated here, in the constructor; [render] allocates nothing, so a
 * garbage collection never starts from the audio thread, and a test can call [render] directly on
 * its own thread after queuing commands.
 *
 * Effects are resampled from their own rate to [sampleRate] by linear interpolation, at a step of
 * `sound rate / device rate * pitch` frames per output frame. A mono effect is panned with the
 * constant-power law, so its center gain is 1/sqrt(2) on each side; a stereo effect gets a
 * balance from the same pan value, unity at the center and the far channel fading to silence at
 * either end. Music is resampled the same way, has no pan, and fades with a linear ramp per
 * frame. Volume changes ramp across one [render] call so they do not click. The sum is clamped to
 * -1..1.
 */
internal class Mixer(
    /** Output frames per second. */
    val sampleRate: Int,
    /** Number of voice slots. */
    val maxVoices: Int,
    /** Frames per music decode block. */
    bufferFrames: Int,
    /** Commands from the main thread. */
    val commands: CommandRing = CommandRing(),
    /** Music decoders the mixer is done with, back to the main thread. */
    val retired: RetiredRing = RetiredRing(),
) : CommandConsumer {
    /** Per slot, the generation of the voice that last ended there. Written here, read by the main thread. */
    val finished: AtomicIntArray = AtomicIntArray(maxVoices)

    /** Id of the last current music track that ended. Written here, read by the main thread. */
    val musicEnded: AtomicInt = AtomicInt(0)

    val masterVolume: AtomicFloat = AtomicFloat(1f)
    val effectsVolume: AtomicFloat = AtomicFloat(1f)
    val musicVolume: AtomicFloat = AtomicFloat(1f)

    /** The volume of the music track itself, `Music.volume`, on top of [musicVolume]. */
    val trackVolume: AtomicFloat = AtomicFloat(1f)

    // Voices: one entry per slot.
    private val sounds = arrayOfNulls<Sound>(maxVoices)
    private val active = BooleanArray(maxVoices)
    private val loops = BooleanArray(maxVoices)
    private val generations = IntArray(maxVoices)
    private val positions = DoubleArray(maxVoices)
    private val steps = DoubleArray(maxVoices)
    private val volumes = FloatArray(maxVoices)
    private val pans = FloatArray(maxVoices)
    private val gainsLeft = FloatArray(maxVoices)
    private val gainsRight = FloatArray(maxVoices)
    private val targetsLeft = FloatArray(maxVoices)
    private val targetsRight = FloatArray(maxVoices)

    // Music: the track playing now, and the one fading out under it after a swap.
    private var current = MusicLane(bufferFrames)
    private var outgoing = MusicLane(bufferFrames)

    // Bus gains reached at the end of the previous render, where this render's ramps start.
    private var lastEffectsGain = 1f
    private var lastMusicGain = 1f

    /**
     * Applies the queued commands, then writes [frames] frames of interleaved stereo into [output]
     * from index 0, overwriting what is there. Writes at most `output.size / 2` frames.
     */
    fun render(output: FloatArray, frames: Int) {
        commands.drain(this)
        val count = min(frames, output.size / 2)
        if (count <= 0) return
        output.fill(0f, 0, count * 2)

        val master = masterVolume.load()
        val effectsGain = master * effectsVolume.load()
        val musicGain = master * musicVolume.load() * trackVolume.load()
        val effectsStart = lastEffectsGain
        val musicStart = lastMusicGain
        lastEffectsGain = effectsGain
        lastMusicGain = musicGain

        for (slot in 0 until maxVoices) {
            if (active[slot]) mixVoice(slot, output, count, effectsStart, effectsGain)
        }
        if (outgoing.stream != null) mixMusic(outgoing, output, count, musicStart, musicGain)
        if (current.stream != null) mixMusic(current, output, count, musicStart, musicGain)

        for (i in 0 until count * 2) {
            val sample = output[i]
            if (sample > 1f) output[i] = 1f else if (sample < -1f) output[i] = -1f
        }
    }

    /**
     * Applies the queued commands and hands every music decoder to [retired], for shutdown after
     * the audio thread has stopped calling [render].
     */
    fun releaseStreams() {
        commands.drain(this)
        retire(outgoing, report = false)
        retire(current, report = true)
    }

    override fun consume(ring: CommandRing, row: Int) {
        when (ring.kindAt(row)) {
            PLAY -> play(
                slot = ring.int0At(row),
                generation = ring.int1At(row),
                loop = ring.int2At(row) == PlaybackMode.LOOP.ordinal,
                volume = ring.float0At(row),
                pitch = ring.float1At(row),
                pan = ring.float2At(row),
                sound = ring.referenceAt(row) as Sound,
            )
            STOP -> {
                val slot = ring.int0At(row)
                if (isCurrent(slot, ring.int1At(row))) finish(slot)
            }
            SET_VOLUME -> {
                val slot = ring.int0At(row)
                if (isCurrent(slot, ring.int1At(row))) {
                    volumes[slot] = ring.float0At(row)
                    updateTargets(slot)
                }
            }
            SET_PITCH -> {
                val slot = ring.int0At(row)
                if (isCurrent(slot, ring.int1At(row))) steps[slot] = stepOf(sounds[slot]!!.sampleRate, ring.float0At(row))
            }
            SET_PAN -> {
                val slot = ring.int0At(row)
                if (isCurrent(slot, ring.int1At(row))) {
                    pans[slot] = ring.float0At(row)
                    updateTargets(slot)
                }
            }
            STOP_ALL -> for (slot in 0 until maxVoices) if (active[slot]) finish(slot)
            PLAY_MUSIC -> playMusic(
                id = ring.int0At(row),
                loop = ring.int1At(row) == PlaybackMode.LOOP.ordinal,
                fadeFrames = ring.int2At(row),
                stream = ring.referenceAt(row) as PcmStream,
            )
            STOP_MUSIC -> stopMusic(fadeFrames = ring.int0At(row))
        }
    }

    private fun isCurrent(slot: Int, generation: Int): Boolean =
        slot in 0 until maxVoices && active[slot] && generations[slot] == generation

    private fun play(slot: Int, generation: Int, loop: Boolean, volume: Float, pitch: Float, pan: Float, sound: Sound) {
        if (slot !in 0 until maxVoices) return
        generations[slot] = generation
        if (sound.frameCount == 0) {
            // Nothing to play: report the end right away. A voice stolen for it stops.
            active[slot] = false
            sounds[slot] = null
            finished.storeAt(slot, generation)
            return
        }
        sounds[slot] = sound
        active[slot] = true
        loops[slot] = loop
        positions[slot] = 0.0
        steps[slot] = stepOf(sound.sampleRate, pitch)
        volumes[slot] = volume
        pans[slot] = pan
        updateTargets(slot)
        // A new voice starts at its gain instead of ramping up from the previous sound's.
        gainsLeft[slot] = targetsLeft[slot]
        gainsRight[slot] = targetsRight[slot]
    }

    private fun stepOf(soundRate: Int, pitch: Float): Double = soundRate.toDouble() / sampleRate * pitch

    private fun updateTargets(slot: Int) {
        val volume = volumes[slot]
        val pan = pans[slot]
        if (sounds[slot]!!.channels == 1) {
            // Constant power: left = cos, right = sin of an angle from 0 (left) to pi/2 (right).
            val angle = (pan + 1.0) * (PI / 4)
            targetsLeft[slot] = if (pan >= 1f) 0f else (cos(angle) * volume).toFloat()
            targetsRight[slot] = if (pan <= -1f) 0f else (sin(angle) * volume).toFloat()
        } else {
            targetsLeft[slot] = volume * min(1f, 1f - pan)
            targetsRight[slot] = volume * min(1f, 1f + pan)
        }
    }

    private fun finish(slot: Int) {
        active[slot] = false
        sounds[slot] = null
        finished.storeAt(slot, generations[slot])
    }

    private fun mixVoice(slot: Int, output: FloatArray, frames: Int, busStart: Float, busEnd: Float) {
        val sound = sounds[slot] ?: return
        val samples = sound.samples
        val frameCount = sound.frameCount
        val last = frameCount - 1
        val stereo = sound.channels == 2
        val loop = loops[slot]
        val step = steps[slot]

        val startLeft = gainsLeft[slot] * busStart
        val startRight = gainsRight[slot] * busStart
        val deltaLeft = (targetsLeft[slot] * busEnd - startLeft) / frames
        val deltaRight = (targetsRight[slot] * busEnd - startRight) / frames
        gainsLeft[slot] = targetsLeft[slot]
        gainsRight[slot] = targetsRight[slot]

        var position = positions[slot]
        var out = 0
        for (i in 0 until frames) {
            val index = position.toInt()
            val fraction = (position - index).toFloat()
            val next = if (index < last) index + 1 else if (loop) 0 else index
            val gainLeft = startLeft + deltaLeft * i
            val gainRight = startRight + deltaRight * i
            if (stereo) {
                val a = index * 2
                val b = next * 2
                val left = samples[a] + (samples[b] - samples[a]) * fraction
                val right = samples[a + 1] + (samples[b + 1] - samples[a + 1]) * fraction
                output[out] += left * gainLeft
                output[out + 1] += right * gainRight
            } else {
                val a = samples[index]
                val sample = a + (samples[next] - a) * fraction
                output[out] += sample * gainLeft
                output[out + 1] += sample * gainRight
            }
            out += 2
            position += step
            if (position >= frameCount) {
                if (loop) {
                    position %= frameCount
                } else {
                    finish(slot)
                    return
                }
            }
        }
        positions[slot] = position
    }

    private fun playMusic(id: Int, loop: Boolean, fadeFrames: Int, stream: PcmStream) {
        // At most two tracks sound at once: a third cuts the one already fading out.
        retire(outgoing, report = false)
        if (current.stream != null) {
            val previous = current
            current = outgoing
            outgoing = previous
            if (fadeFrames > 0) outgoing.fadeTo(0f, fadeFrames, stop = true) else retire(outgoing, report = false)
        }
        current.start(stream, id, loop, stream.sampleRate.toDouble() / sampleRate)
        if (fadeFrames > 0) {
            current.fade = 0f
            current.fadeTo(1f, fadeFrames, stop = false)
        }
    }

    private fun stopMusic(fadeFrames: Int) {
        if (current.stream == null) return
        if (fadeFrames > 0) current.fadeTo(0f, fadeFrames, stop = true) else retire(current, report = true)
    }

    /**
     * Hands [lane]'s stream back to the main thread and empties the lane. When [report] is set, the
     * lane is the current track, so the main thread learns it ended through [musicEnded].
     */
    private fun retire(lane: MusicLane, report: Boolean) {
        val stream = lane.stream ?: return
        // The main thread keeps fewer open streams than the ring holds, so this push succeeds.
        retired.push(stream)
        if (report) musicEnded.store(lane.id)
        lane.clear()
    }

    private fun mixMusic(lane: MusicLane, output: FloatArray, frames: Int, busStart: Float, busEnd: Float) {
        val report = lane === current
        val busDelta = (busEnd - busStart) / frames
        val buffer = lane.buffer
        val stereo = lane.channels == 2
        val step = lane.step
        var out = 0
        for (i in 0 until frames) {
            while (lane.position >= lane.buffered - 1) {
                if (!lane.refill()) {
                    retire(lane, report)
                    return
                }
            }
            val position = lane.position
            val index = if (position < 0.0) -1 else position.toInt()
            val fraction = (position - index).toFloat()
            val next = index + 1
            val left0: Float
            val right0: Float
            if (index < 0) {
                left0 = lane.lastLeft
                right0 = lane.lastRight
            } else if (stereo) {
                left0 = buffer[index * 2]
                right0 = buffer[index * 2 + 1]
            } else {
                left0 = buffer[index]
                right0 = left0
            }
            val left1: Float
            val right1: Float
            if (stereo) {
                left1 = buffer[next * 2]
                right1 = buffer[next * 2 + 1]
            } else {
                left1 = buffer[next]
                right1 = left1
            }
            val gain = lane.fade * (busStart + busDelta * i)
            output[out] += (left0 + (left1 - left0) * fraction) * gain
            output[out + 1] += (right0 + (right1 - right0) * fraction) * gain
            out += 2
            lane.position = position + step
            if (lane.advanceFade()) {
                retire(lane, report)
                return
            }
        }
    }

    override fun toString(): String = "Mixer(sampleRate=$sampleRate, maxVoices=$maxVoices)"

    internal companion object {
        const val PLAY: Int = 1
        const val STOP: Int = 2
        const val SET_VOLUME: Int = 3
        const val SET_PITCH: Int = 4
        const val SET_PAN: Int = 5
        const val STOP_ALL: Int = 6
        const val PLAY_MUSIC: Int = 7
        const val STOP_MUSIC: Int = 8
    }
}

/**
 * One music track inside the [Mixer]: its decoder, the decoded block, the read position and the
 * fade. Audio thread only.
 *
 * [position] is a fractional frame index into [buffer] that runs from -1 up to [buffered] - 1;
 * index -1 is the last frame of the previous block, kept in [lastLeft] and [lastRight] so the
 * interpolation runs across block boundaries without a seam.
 */
private class MusicLane(bufferFrames: Int) {
    val buffer = FloatArray(bufferFrames * 2)
    var stream: PcmStream? = null
    var id = 0
    var channels = 0
    var loop = false
    var step = 1.0
    var position = 0.0
    var buffered = 0
    var lastLeft = 0f
    var lastRight = 0f
    var fade = 1f
    private var fadeStep = 0f
    private var fadeTarget = 1f
    private var fadeRemaining = 0
    private var stopAtFadeEnd = false

    fun start(stream: PcmStream, id: Int, loop: Boolean, step: Double) {
        this.stream = stream
        this.id = id
        this.loop = loop
        this.step = step
        channels = stream.channels
        position = 0.0
        buffered = 0
        lastLeft = 0f
        lastRight = 0f
        fade = 1f
        fadeStep = 0f
        fadeTarget = 1f
        fadeRemaining = 0
        stopAtFadeEnd = false
    }

    fun clear() {
        stream = null
        buffered = 0
    }

    /** Starts a linear ramp from the current fade to [target] over [frames] frames. */
    fun fadeTo(target: Float, frames: Int, stop: Boolean) {
        fadeTarget = target
        fadeRemaining = frames
        fadeStep = (target - fade) / frames
        stopAtFadeEnd = stop
    }

    /** Moves the fade one frame on and returns `true` when a fade out has just reached silence. */
    fun advanceFade(): Boolean {
        if (fadeRemaining == 0) return false
        fadeRemaining--
        if (fadeRemaining == 0) {
            fade = fadeTarget
            return stopAtFadeEnd
        }
        fade += fadeStep
        return false
    }

    /**
     * Keeps the last frame of the current block, reads the next block and returns `true`, or
     * returns `false` when the stream has ended and does not loop.
     */
    fun refill(): Boolean {
        val stream = stream ?: return false
        if (buffered > 0) {
            val last = (buffered - 1) * channels
            lastLeft = buffer[last]
            lastRight = buffer[last + channels - 1]
            position -= buffered
        }
        val capacity = buffer.size / channels
        var read = stream.read(buffer, capacity)
        if (read <= 0 && loop) {
            stream.rewind()
            read = stream.read(buffer, capacity)
        }
        if (read <= 0) {
            buffered = 0
            return false
        }
        buffered = read
        return true
    }
}

/** A [Float] that one thread stores and another loads, held as its bits in an atomic int. */
internal class AtomicFloat(initial: Float) {
    private val bits = AtomicInt(initial.toRawBits())

    fun load(): Float = Float.fromBits(bits.load())

    fun store(value: Float) {
        bits.store(value.toRawBits())
    }

    override fun toString(): String = load().toString()
}
