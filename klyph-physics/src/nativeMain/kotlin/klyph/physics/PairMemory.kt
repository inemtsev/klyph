package klyph.physics

/**
 * What [RigidContacts] remembers about the pairs of bodies it looked at closely, from one substep to
 * the next: for each pair, where its contacts start and how many there are, and where the second
 * body was from the first when the pair was last found by the narrowphase, across, down and turned.
 *
 * A pair that has hardly moved against each other since then has contacts that would be found the
 * same, so [RigidContacts] copies them instead of looking again. This holds this substep's pairs, as
 * [add] notes them, and the last substep's, which [recall] finds by the two bodies. None are kept
 * from one step to the next: see [forgetAll]. It never allocates once it has grown to the pairs a
 * scene has.
 */
internal class PairMemory {
    private var count = 0
    private var p = IntArray(INITIAL)
    private var q = IntArray(INITIAL)
    private var first = IntArray(INITIAL)
    private var n = IntArray(INITIAL)
    private var x = FloatArray(INITIAL)
    private var y = FloatArray(INITIAL)
    private var turn = FloatArray(INITIAL)

    @PublishedApi internal var oldCount = 0
    @PublishedApi internal var oldP = IntArray(INITIAL)
    @PublishedApi internal var oldQ = IntArray(INITIAL)
    @PublishedApi internal var oldFirst = IntArray(INITIAL)
    @PublishedApi internal var oldN = IntArray(INITIAL)
    @PublishedApi internal var oldX = FloatArray(INITIAL)
    @PublishedApi internal var oldY = FloatArray(INITIAL)
    @PublishedApi internal var oldTurn = FloatArray(INITIAL)

    @PublishedApi internal var table = IntArray(2 * INITIAL)
    private var newStep = true

    /** Forgets every pair, the last substep's too: a new step begins, and the bodies have moved. */
    fun forgetAll() {
        newStep = true
    }

    /** Makes this substep's pairs the last's, and starts a substep with none, unless a new step began: then there are none to keep. */
    fun nextSubstep() {
        val kept = if (newStep) 0 else count
        newStep = false
        p = oldP.also { oldP = p }
        q = oldQ.also { oldQ = q }
        first = oldFirst.also { oldFirst = first }
        n = oldN.also { oldN = n }
        x = oldX.also { oldX = x }
        y = oldY.also { oldY = y }
        turn = oldTurn.also { oldTurn = turn }
        var size = table.size
        while (size < 2 * kept) size *= 2
        if (size != table.size) table = IntArray(size) else table.fill(0)
        val mask = size - 1
        for (k in 0 until kept) {
            var slot = keyHash(oldP[k], oldQ[k], 0, 0) and mask
            while (table[slot] != 0) slot = (slot + 1) and mask
            table[slot] = k + 1
        }
        oldCount = kept
        count = 0
    }

    /** Returns the last substep's pair of bodies [bodyP] and [bodyQ], or -1. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun recall(bodyP: Int, bodyQ: Int): Int {
        if (oldCount == 0) return -1
        val mask = table.size - 1
        var slot = keyHash(bodyP, bodyQ, 0, 0) and mask
        var found = -1
        while (true) {
            val k = table[slot] - 1
            if (k < 0 || oldP[k] == bodyP && oldQ[k] == bodyQ) {
                found = k
                break
            }
            slot = (slot + 1) and mask
        }
        return found
    }

    /**
     * Notes that bodies [bodyP] and [bodyQ] were looked at: their [contacts] start at contact [firstContact], and
     * ([relX], [relY]) and [relTurn] are where the second was from the first when last found.
     */
    fun add(bodyP: Int, bodyQ: Int, firstContact: Int, contacts: Int, relX: Float, relY: Float, relTurn: Float) {
        if (count == p.size) grow()
        val k = count++
        p[k] = bodyP
        q[k] = bodyQ
        first[k] = firstContact
        n[k] = contacts
        x[k] = relX
        y[k] = relY
        turn[k] = relTurn
    }

    /** Returns where the contacts of last substep's pair [k] start. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun firstContactOf(k: Int): Int = oldFirst[k]

    /** Returns how many contacts last substep's pair [k] has. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun contactsOf(k: Int): Int = oldN[k]

    /** Returns where the second body of last substep's pair [k] was from the first, across, when last found. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun relXOf(k: Int): Float = oldX[k]

    /** Returns where the second body of last substep's pair [k] was from the first, down, when last found. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun relYOf(k: Int): Float = oldY[k]

    /** Returns how far the second body of last substep's pair [k] was turned from the first when last found. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun relTurnOf(k: Int): Float = oldTurn[k]

    /** Doubles the room for pairs, this substep's and the last's alike, as they're swapped. */
    private fun grow() {
        val size = p.size * 2
        p = p.copyOf(size)
        q = q.copyOf(size)
        first = first.copyOf(size)
        n = n.copyOf(size)
        x = x.copyOf(size)
        y = y.copyOf(size)
        turn = turn.copyOf(size)
        oldP = oldP.copyOf(size)
        oldQ = oldQ.copyOf(size)
        oldFirst = oldFirst.copyOf(size)
        oldN = oldN.copyOf(size)
        oldX = oldX.copyOf(size)
        oldY = oldY.copyOf(size)
        oldTurn = oldTurn.copyOf(size)
    }

    private companion object {
        const val INITIAL = 64
    }
}

/** Hashes what makes a contact the same from one substep to the next: its two bodies, their shapes and the feature. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun keyHash(p: Int, q: Int, shapes: Int, id: Int): Int =
    (p * 73_856_093) xor (q * 19_349_663) xor (shapes * 83_492_791) xor (id * -1_640_531_535)
