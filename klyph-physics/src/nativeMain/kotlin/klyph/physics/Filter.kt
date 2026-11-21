package klyph.physics

import klyph.collision.CollisionLayers

/**
 * The layer the level is on. Physics filters collisions with klyph's [CollisionLayers], as a
 * [klyph.collision.Collider] does: everything that collides is on some layers and has a mask of
 * the layers it collides with, and two things collide only when each one's mask holds a layer of
 * the other. By default everything is on [CollisionLayers.DEFAULT] with the mask
 * [CollisionLayers.ALL], so everything collides with everything.
 *
 * The level, the [SolidGrid], is on [SOLIDS], layer 30: take it out of a mask and the thing passes
 * through the solids. Use layers 0 to 29 for your own.
 *
 * @sample samples.physics.LayerSamples.layers
 */
public object PhysicsLayers {
    /** Layer 30, the level's: everything whose mask holds it is kept out of the solids. */
    public val SOLIDS: CollisionLayers = CollisionLayers.of(30)

    /** Returns `true` if [layersA] with [maskA] and [layersB] with [maskB] collide, as described on [PhysicsLayers]. */
    public fun collide(layersA: CollisionLayers, maskA: CollisionLayers, layersB: CollisionLayers, maskB: CollisionLayers): Boolean =
        collides(layersA.bits, maskA.bits, layersB.bits, maskB.bits)

    internal fun collides(categoryA: Int, maskA: Int, categoryB: Int, maskB: Int): Boolean =
        categoryA and maskB != 0 && categoryB and maskA != 0

    internal const val SOLIDS_BIT: Int = 1 shl 30

    internal fun checkLayers(layers: CollisionLayers) {
        require(layers.bits != 0 && layers.bits and (SOLIDS_BIT or (1 shl 31)) == 0) {
            "Layers $layers must hold at least one of layers 0 to 29, as layer 30 is the solids'."
        }
    }
}

/** What one side of a [Touch] is. */
public enum class TouchKind {
    /** A [RigidBody]. */
    RIGID_BODY,

    /** A [SoftBody]. */
    SOFT_BODY,

    /** A [Particle] that is not part of a soft body, such as a grain of sand. */
    PARTICLE,

    /** The level: the [PhysicsWorld.solids]. */
    SOLIDS,
}

/**
 * One side of a [Touch]: a rigid body, a soft body, a particle or the level, as [kind] says.
 *
 * [toString] gives the kind and the handle, for example `TouchSide(RigidBody(3))` or
 * `TouchSide(SOLIDS)`.
 */
public value class TouchSide internal constructor(internal val packed: Int) {
    /** What this side is. */
    public val kind: TouchKind get() = TouchKind.entries[packed ushr KIND_SHIFT]

    internal val index: Int get() = packed and INDEX_MASK

    /**
     * Returns this side as the rigid body it is.
     *
     * @throws IllegalStateException if [kind] is not [TouchKind.RIGID_BODY].
     */
    public fun toRigidBody(): RigidBody {
        check(kind == TouchKind.RIGID_BODY) { "Touch side $this is not a rigid body." }
        return RigidBody(index)
    }

    /**
     * Returns this side as the soft body it is.
     *
     * @throws IllegalStateException if [kind] is not [TouchKind.SOFT_BODY].
     */
    public fun toSoftBody(): SoftBody {
        check(kind == TouchKind.SOFT_BODY) { "Touch side $this is not a soft body." }
        return SoftBody(index)
    }

    /**
     * Returns this side as the particle it is.
     *
     * @throws IllegalStateException if [kind] is not [TouchKind.PARTICLE].
     */
    public fun toParticle(): Particle {
        check(kind == TouchKind.PARTICLE) { "Touch side $this is not a particle." }
        return Particle(index)
    }

    override fun toString(): String = when (kind) {
        TouchKind.RIGID_BODY -> "TouchSide(${RigidBody(index)})"
        TouchKind.SOFT_BODY -> "TouchSide(${SoftBody(index)})"
        TouchKind.PARTICLE -> "TouchSide(${Particle(index)})"
        TouchKind.SOLIDS -> "TouchSide(SOLIDS)"
    }

    /** Builds the side a body, a particle or the level is in a touch. */
    public companion object {
        /** The level as a side of a touch. */
        public val SOLIDS: TouchSide = TouchSide(TouchKind.SOLIDS.ordinal shl KIND_SHIFT)

        /** Returns [body] as a side of a touch. */
        public fun of(body: RigidBody): TouchSide = TouchSide((TouchKind.RIGID_BODY.ordinal shl KIND_SHIFT) or body.index)

        /** Returns [body] as a side of a touch. */
        public fun of(body: SoftBody): TouchSide = TouchSide((TouchKind.SOFT_BODY.ordinal shl KIND_SHIFT) or body.index)

        /** Returns [particle] as a side of a touch. */
        public fun of(particle: Particle): TouchSide = TouchSide((TouchKind.PARTICLE.ordinal shl KIND_SHIFT) or particle.index)

        internal const val KIND_SHIFT: Int = 29
        internal const val INDEX_MASK: Int = (1 shl KIND_SHIFT) - 1
    }
}

/**
 * One touch between two things, [a] and [b]. For a sensor's touch, [a] is the sensor.
 *
 * [toString] gives both sides, for example `Touch(TouchSide(RigidBody(3)), TouchSide(SOLIDS))`.
 */
public value class Touch internal constructor(internal val key: Long) {
    /** The first side. */
    public val a: TouchSide get() = TouchSide((key ushr 32).toInt())

    /** The second side. */
    public val b: TouchSide get() = TouchSide(key.toInt())

    /** Returns `true` if [side] is either side of this touch. */
    public fun involves(side: TouchSide): Boolean = a == side || b == side

    override fun toString(): String = "Touch($a, $b)"
}

/**
 * The touches of one kind that one step reported, filled in by [Events]. Read it in the step's
 * systems and do not keep it: the next step writes over it. Reading allocates nothing.
 *
 * [toString] gives how many touches it holds, for example `Touches(size=2)`.
 *
 * @sample samples.physics.EventSamples.contactsBegun
 */
public class Touches internal constructor() {
    private var keys = LongArray(16)

    /** Number of touches. */
    public var size: Int = 0
        private set

    /**
     * Returns touch [index].
     *
     * @throws IndexOutOfBoundsException if [index] is not in `0 until size`.
     */
    public operator fun get(index: Int): Touch {
        if (index !in 0 until size) throw IndexOutOfBoundsException("Index $index is out of bounds for size $size.")
        return Touch(keys[index])
    }

    /** Returns `true` if any touch has [side] on either side. */
    public fun involves(side: TouchSide): Boolean {
        for (i in 0 until size) if (Touch(keys[i]).involves(side)) return true
        return false
    }

    /** Returns `true` if there is a touch between [a] and [b], either way round. */
    public fun has(a: TouchSide, b: TouchSide): Boolean {
        val one = (a.packed.toLong() shl 32) or (b.packed.toLong() and 0xFFFFFFFFL)
        val other = (b.packed.toLong() shl 32) or (a.packed.toLong() and 0xFFFFFFFFL)
        for (i in 0 until size) if (keys[i] == one || keys[i] == other) return true
        return false
    }

    override fun toString(): String = "Touches(size=$size)"

    internal fun clear() {
        size = 0
    }

    internal fun add(key: Long) {
        if (size == keys.size) keys = keys.copyOf(size * 2)
        keys[size++] = key
    }
}

/**
 * A set of touches, each packed into a `Long`: an open-addressed hash table plus the order they came
 * in, so adding is cheap however often the same touch is seen in a step, and clearing costs only
 * what was added.
 */
internal class TouchPairs {
    private var table = LongArray(64) { EMPTY }
    private var keys = LongArray(32)

    var count = 0
        private set

    fun key(i: Int): Long = keys[i]

    fun add(key: Long) {
        if (2 * (count + 1) > table.size) grow()
        val mask = table.size - 1
        var slot = hash(key) and mask
        while (true) {
            val k = table[slot]
            if (k == key) return
            if (k == EMPTY) break
            slot = (slot + 1) and mask
        }
        table[slot] = key
        if (count == keys.size) keys = keys.copyOf(count * 2)
        keys[count++] = key
    }

    fun contains(key: Long): Boolean {
        val mask = table.size - 1
        var slot = hash(key) and mask
        while (true) {
            val k = table[slot]
            if (k == key) return true
            if (k == EMPTY) return false
            slot = (slot + 1) and mask
        }
    }

    fun clear() {
        if (count == 0) return
        val mask = table.size - 1
        // only the slots that were used, found as they were filled
        for (i in 0 until count) {
            var slot = hash(keys[i]) and mask
            while (table[slot] != EMPTY) {
                table[slot] = EMPTY
                slot = (slot + 1) and mask
            }
        }
        count = 0
    }

    /**
     * Drops every touch involving [kind] and [index] (or any handle of [kind] when [index] is
     * negative). Removed keys are added to [removed] if given. Allocates nothing.
     */
    fun removeInvolving(kind: Int, index: Int, removed: TouchPairs? = null) {
        if (count == 0) return
        var any = false
        for (i in 0 until count) {
            if (involves(keys[i], kind, index)) {
                any = true
                break
            }
        }
        if (!any) return
        val mask = table.size - 1
        for (i in 0 until count) {
            var slot = hash(keys[i]) and mask
            while (table[slot] != EMPTY) {
                table[slot] = EMPTY
                slot = (slot + 1) and mask
            }
        }
        var kept = 0
        for (i in 0 until count) {
            val k = keys[i]
            if (involves(k, kind, index)) {
                removed?.add(k)
            } else {
                keys[kept++] = k
                var slot = hash(k) and mask
                while (table[slot] != EMPTY) slot = (slot + 1) and mask
                table[slot] = k
            }
        }
        count = kept
    }

    private fun involves(key: Long, kind: Int, index: Int): Boolean =
        matchesSide(kindA(key), a(key), kind, index) || matchesSide(kindB(key), b(key), kind, index)

    private fun matchesSide(sideKind: Int, sideIndex: Int, kind: Int, index: Int): Boolean =
        sideKind == kind && (index < 0 || sideIndex == index)

    private fun grow() {
        val old = keys
        val n = count
        table = LongArray(table.size * 2) { EMPTY }
        count = 0
        for (i in 0 until n) add(old[i])
    }

    companion object {
        private const val EMPTY = -1L

        /**
         * Mixes every bit of [key] into the low ones, which pick the slot: a touch with the solids has
         * the same low 29 bits as every other, so a plain multiply crowds them all into a few slots.
         * MurmurHash3's finaliser.
         */
        private fun hash(key: Long): Int {
            var h = key
            h = (h xor (h ushr 33)) * -49_064_778_989_728_563L
            h = (h xor (h ushr 33)) * -4_265_267_296_055_464_877L
            return (h xor (h ushr 33)).toInt()
        }

        /**
         * Packs a touch: two bits of kind and 29 of handle a side, so its high and low 32 bits are each
         * a [TouchSide]. The solids' handle is 0.
         */
        fun key(kindA: Int, a: Int, kindB: Int, b: Int): Long =
            (kindA.toLong() shl 61) or ((a.toLong() and HANDLE) shl 32) or (kindB.toLong() shl 29) or (b.toLong() and HANDLE)

        /** The same touch whichever side comes first: the lower kind first, then the lower handle. */
        fun ordered(kindA: Int, a: Int, kindB: Int, b: Int): Long =
            if (kindA < kindB || kindA == kindB && a <= b) key(kindA, a, kindB, b) else key(kindB, b, kindA, a)

        fun kindA(key: Long): Int = (key ushr 61).toInt() and 3

        fun a(key: Long): Int = ((key ushr 32) and HANDLE).toInt()

        fun kindB(key: Long): Int = (key ushr 29).toInt() and 3

        fun b(key: Long): Int = (key and HANDLE).toInt()

        private const val HANDLE = (1L shl 29) - 1
    }
}
