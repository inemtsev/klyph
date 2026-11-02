@file:OptIn(ObsoleteWorkersApi::class)

package klyph.audio

import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommandRingTest {
    @Test
    fun drainsEveryColumnInPushOrder() {
        val ring = CommandRing(capacity = 4)
        val payload = Any()
        assertTrue(ring.push(1, int0 = 10, int1 = 11, int2 = 12, float0 = 0.5f, float1 = 1.5f, float2 = 2.5f, reference = payload))
        assertTrue(ring.push(2, int0 = 20))
        assertEquals(2, ring.size)

        val kinds = ArrayList<Int>()
        val drained = ring.drain { r, row ->
            kinds.add(r.kindAt(row))
            if (r.kindAt(row) == 1) {
                assertEquals(10, r.int0At(row))
                assertEquals(11, r.int1At(row))
                assertEquals(12, r.int2At(row))
                assertEquals(0.5f, r.float0At(row))
                assertEquals(1.5f, r.float1At(row))
                assertEquals(2.5f, r.float2At(row))
                assertSame(payload, r.referenceAt(row))
            } else {
                assertEquals(20, r.int0At(row))
                assertNull(r.referenceAt(row))
            }
        }
        assertEquals(2, drained)
        assertEquals(listOf(1, 2), kinds)
        assertEquals(0, ring.size)
    }

    @Test
    fun throwingConsumerDropsItsRowAndKeepsTheRest() {
        val ring = CommandRing(capacity = 4)
        ring.push(1)
        ring.push(2, reference = Any())
        ring.push(3)

        val first = ArrayList<Int>()
        assertFailsWith<IllegalStateException> {
            ring.drain { r, row ->
                first.add(r.kindAt(row))
                if (r.kindAt(row) == 2) throw IllegalStateException("boom")
            }
        }
        assertEquals(listOf(1, 2), first)
        assertEquals(1, ring.size)

        val second = ArrayList<Int>()
        assertEquals(1, ring.drain { r, row -> second.add(r.kindAt(row)) })
        assertEquals(listOf(3), second)
        assertEquals(0, ring.size)
    }

    @Test
    fun fullRingRefusesPushWithoutBlocking() {
        val ring = CommandRing(capacity = 4)
        repeat(4) { assertTrue(ring.push(it)) }
        assertFalse(ring.push(99))
        assertEquals(4, ring.size)

        val seen = ArrayList<Int>()
        ring.drain { r, row -> seen.add(r.kindAt(row)) }
        assertEquals(listOf(0, 1, 2, 3), seen)
        assertTrue(ring.push(4))
    }

    @Test
    fun drainClearsReferences() {
        val ring = CommandRing(capacity = 2)
        ring.push(1, reference = "sound")
        ring.drain { _, _ -> }
        // The row is reused by the next push; before that, the ring must not hold the old object.
        var leftover: Any? = "unset"
        ring.push(2)
        ring.drain { r, row -> leftover = r.referenceAt(row) }
        assertNull(leftover)
    }

    @Test
    fun wrapsAroundManyTimes() {
        val ring = CommandRing(capacity = 8)
        var expected = 0
        for (i in 0 until 1000) {
            ring.push(1, int0 = i)
            if (i % 3 == 2) ring.drain { r, row -> assertEquals(expected++, r.int0At(row)) }
        }
        ring.drain { r, row -> assertEquals(expected++, r.int0At(row)) }
        assertEquals(1000, expected)
    }

    @Test
    fun rejectsCapacityThatIsNotAPowerOfTwo() {
        assertFailsWith<IllegalArgumentException> { CommandRing(capacity = 6) }
        assertFailsWith<IllegalArgumentException> { CommandRing(capacity = 0) }
        assertFailsWith<IllegalArgumentException> { RetiredRing(capacity = 3) }
    }

    @Test
    fun retiredRingReturnsStreamsInOrderAndRefusesWhenFull() {
        val ring = RetiredRing(capacity = 2)
        val a = CountingStream()
        val b = CountingStream()
        assertTrue(ring.push(a))
        assertTrue(ring.push(b))
        assertFalse(ring.push(CountingStream()))
        assertSame(a, ring.pollOrNull())
        assertSame(b, ring.pollOrNull())
        assertNull(ring.pollOrNull())
    }

    @Test
    fun anotherThreadPushesAMillionCommandsInOrder() {
        val ring = CommandRing()
        val worker = Worker.start(name = "ring producer")
        try {
            val done = worker.execute(TransferMode.SAFE, { ring }) { producer ->
                var sequence = 0
                var refused = 0L
                while (sequence < STRESS_COUNT) {
                    if (producer.push(1, int0 = sequence, float0 = sequence.toFloat(), reference = producer)) {
                        sequence++
                    } else {
                        refused++
                    }
                }
                refused
            }
            var expected = 0
            var mismatches = 0
            val consumer = CommandConsumer { r, row ->
                if (r.int0At(row) != expected || r.referenceAt(row) !== r) mismatches++
                expected++
            }
            while (expected < STRESS_COUNT) ring.drain(consumer)
            done.result
            assertEquals(0, mismatches)
            assertEquals(STRESS_COUNT, expected)
            assertEquals(0, ring.size)
        } finally {
            worker.requestTermination().result
        }
    }

    private companion object {
        const val STRESS_COUNT = 1_000_000
    }
}

/** A stream of constant frames that counts how often it is closed and rewound. */
internal class CountingStream(
    override val channels: Int = 2,
    override val sampleRate: Int = 48_000,
    private val frames: Int = Int.MAX_VALUE,
    private val value: Float = 1f,
) : PcmStream {
    var closes = 0
    var rewinds = 0
    private var position = 0

    override fun read(output: FloatArray, frames: Int): Int {
        val count = minOf(frames, this.frames - position)
        output.fill(value, 0, count * channels)
        position += count
        return count
    }

    override fun rewind() {
        rewinds++
        position = 0
    }

    override fun close() {
        closes++
    }
}
