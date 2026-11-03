package klyph.audio

/**
 * Identifies one playback of a [Sound], as returned by [Audio.play]. Pass it to [Audio.stop],
 * [Audio.setVolume], [Audio.setPitch], [Audio.setPan] and [Audio.isPlaying].
 *
 * A voice is a mixer slot plus a generation that changes each time the slot starts a new sound,
 * so a handle kept after its sound ended, or after another sound took its slot, never controls
 * the new sound: operations on it do nothing and [Audio.isPlaying] returns `false`.
 *
 * [toString] gives the slot and generation, for example `Voice(3:7)`, or `Voice.NONE`.
 */
public value class Voice internal constructor(internal val packed: Int) {
    internal val slot: Int get() = packed and SLOT_MASK

    internal val generation: Int get() = packed ushr SLOT_BITS

    override fun toString(): String = if (this == NONE) "Voice.NONE" else "Voice($slot:$generation)"

    public companion object {
        /**
         * The handle that refers to no playback. [Audio.play] returns it when audio is disabled or
         * when its command could not be queued. Every operation on it does nothing.
         */
        public val NONE: Voice = Voice(0)

        internal const val SLOT_BITS: Int = 8
        internal const val SLOT_MASK: Int = (1 shl SLOT_BITS) - 1

        /** The most voices a mixer can have, limited by the bits a handle keeps for the slot. */
        internal const val MAX_SLOTS: Int = 1 shl SLOT_BITS

        /** Generations wrap within this mask and skip 0, so no live handle equals [NONE]. */
        internal const val GENERATION_MASK: Int = (1 shl (31 - SLOT_BITS)) - 1

        internal fun of(slot: Int, generation: Int): Voice = Voice((generation shl SLOT_BITS) or slot)

        /** Returns the generation that follows [generation], skipping 0. */
        internal fun nextGeneration(generation: Int): Int {
            val next = (generation + 1) and GENERATION_MASK
            return if (next == 0) 1 else next
        }
    }
}
