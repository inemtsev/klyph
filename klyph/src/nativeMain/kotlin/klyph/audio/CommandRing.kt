@file:OptIn(ExperimentalAtomicApi::class)

package klyph.audio

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A fixed-size queue of commands from one producer thread to one consumer thread, without locks.
 *
 * A command is one row across parallel columns: a kind, three ints, three floats and one
 * reference, so pushing a command boxes nothing and allocates nothing. The producer writes a row,
 * then stores the tail index; the consumer loads the tail, reads every row up to it, then stores
 * the head index, which hands the rows back to the producer. Atomic loads and stores are
 * sequentially consistent, so a row is fully written before the consumer can see it and fully
 * read before the producer can overwrite it.
 *
 * [push] may be called only from the producer thread and [drain] only from the consumer thread.
 * [capacity] must be a power of two, so an index wraps with a mask. The head and tail indices run
 * freely and wrap at [Int.MAX_VALUE]; their difference stays correct across the wrap.
 */
internal class CommandRing(val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0 && capacity and (capacity - 1) == 0) {
            "Command ring capacity $capacity is not a positive power of two."
        }
    }

    private val mask = capacity - 1
    private val kinds = IntArray(capacity)
    private val ints0 = IntArray(capacity)
    private val ints1 = IntArray(capacity)
    private val ints2 = IntArray(capacity)
    private val floats0 = FloatArray(capacity)
    private val floats1 = FloatArray(capacity)
    private val floats2 = FloatArray(capacity)
    private val references = arrayOfNulls<Any>(capacity)
    private val head = AtomicInt(0)
    private val tail = AtomicInt(0)

    /** Number of commands pushed and not yet drained. Exact on either thread only between calls of the other. */
    val size: Int get() = tail.load() - head.load()

    /**
     * Appends one command and returns `true`, or returns `false` without blocking when the ring is
     * full. Producer thread only.
     */
    fun push(
        kind: Int,
        int0: Int = 0,
        int1: Int = 0,
        int2: Int = 0,
        float0: Float = 0f,
        float1: Float = 0f,
        float2: Float = 0f,
        reference: Any? = null,
    ): Boolean {
        val t = tail.load()
        if (t - head.load() == capacity) return false
        val row = t and mask
        kinds[row] = kind
        ints0[row] = int0
        ints1[row] = int1
        ints2[row] = int2
        floats0[row] = float0
        floats1[row] = float1
        floats2[row] = float2
        references[row] = reference
        tail.store(t + 1)
        return true
    }

    /**
     * Passes every queued command to [consumer] in the order it was pushed, then frees the rows and
     * returns how many there were. Consumer thread only.
     *
     * The consumer reads a row through the `xAt(row)` accessors during its call; the reference
     * column is cleared right after, so the ring keeps nothing alive.
     *
     * If [consumer] throws, the row it threw on counts as consumed and is dropped, the rows already
     * consumed stay consumed, the rows after it stay queued for the next drain, and the exception
     * propagates. No row is ever delivered twice.
     */
    fun drain(consumer: CommandConsumer): Int {
        var h = head.load()
        val t = tail.load()
        val count = t - h
        try {
            while (h != t) {
                val row = h and mask
                try {
                    consumer.consume(this, row)
                } finally {
                    references[row] = null
                    h++
                }
            }
        } finally {
            head.store(h)
        }
        return count
    }

    fun kindAt(row: Int): Int = kinds[row]
    fun int0At(row: Int): Int = ints0[row]
    fun int1At(row: Int): Int = ints1[row]
    fun int2At(row: Int): Int = ints2[row]
    fun float0At(row: Int): Float = floats0[row]
    fun float1At(row: Int): Float = floats1[row]
    fun float2At(row: Int): Float = floats2[row]
    fun referenceAt(row: Int): Any? = references[row]

    override fun toString(): String = "CommandRing(size=$size, capacity=$capacity)"

    companion object {
        const val DEFAULT_CAPACITY: Int = 256
    }
}

/** Receives the commands of a [CommandRing.drain], one row at a time. */
internal fun interface CommandConsumer {
    fun consume(ring: CommandRing, row: Int)
}

/**
 * A fixed-size queue of [PcmStream]s from the audio thread back to the main thread, without locks.
 *
 * The mixer pushes a stream it no longer plays, and the main thread polls it and closes it, so the
 * audio thread never frees anything. Same ordering rules as [CommandRing]: [push] from the
 * producer thread only, [pollOrNull] from the consumer thread only.
 */
internal class RetiredRing(val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0 && capacity and (capacity - 1) == 0) {
            "Retired ring capacity $capacity is not a positive power of two."
        }
    }

    private val mask = capacity - 1
    private val streams = arrayOfNulls<PcmStream>(capacity)
    private val head = AtomicInt(0)
    private val tail = AtomicInt(0)

    /** Number of streams pushed and not yet polled. */
    val size: Int get() = tail.load() - head.load()

    /** Appends [stream] and returns `true`, or returns `false` when the ring is full. Producer thread only. */
    fun push(stream: PcmStream): Boolean {
        val t = tail.load()
        if (t - head.load() == capacity) return false
        streams[t and mask] = stream
        tail.store(t + 1)
        return true
    }

    /** Removes and returns the oldest stream, or returns `null` when the ring is empty. Consumer thread only. */
    fun pollOrNull(): PcmStream? {
        val h = head.load()
        if (h == tail.load()) return null
        val row = h and mask
        val stream = streams[row]
        streams[row] = null
        head.store(h + 1)
        return stream
    }

    override fun toString(): String = "RetiredRing(size=$size, capacity=$capacity)"

    companion object {
        const val DEFAULT_CAPACITY: Int = 64
    }
}
