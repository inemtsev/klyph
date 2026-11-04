package klyph.entity

/**
 * Identifies an entity in a [World] by a slot [index] and a [generation] that changes when the
 * slot is reused, so a stale handle never aliases a new entity. How the two are packed is not
 * part of the API.
 *
 * @sample samples.entity.Components.entity
 */
public value class Entity @PublishedApi internal constructor(@PublishedApi internal val raw: Long) {
    /** Slot index inside the world. Stable while the entity is alive. */
    public val index: Int get() = raw.toInt()

    /** Reuse count of the slot when this handle was issued. */
    public val generation: Int get() = (raw ushr 32).toInt()

    override fun toString(): String = "Entity($index:$generation)"

    internal companion object {
        fun of(index: Int, generation: Int): Entity =
            Entity((generation.toLong() shl 32) or (index.toLong() and 0xFFFF_FFFFL))
    }
}
