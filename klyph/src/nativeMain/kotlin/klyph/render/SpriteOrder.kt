package klyph.render

/**
 * Computes the order a frame's sprites are drawn in: by [DrawSpace], [DrawSpace.BACKGROUND] first,
 * then [DrawSpace.WORLD], then [DrawSpace.SCREEN], then by layer, lowest first, then grouped by
 * texture, keeping storage order inside each group. It is a counting sort, O(n) in the number of
 * sprites plus O(g log g) in the number g of distinct (space, layer, texture) groups, and
 * allocates nothing once its arrays have grown to the frame's size.
 *
 * Each frame calls [begin], then one of the three add functions once per sprite in storage
 * order, then [finish]. The functions that take a layer and a texture add a [DrawSpace.WORLD]
 * sprite; for any space, find the group with the three-argument [groupFor] and pass it to
 * [addGroup], [addGroupWithoutPosition] or [placeGroup]:
 * - [add] records each sprite's group; after [finish], [claimPosition] returns each sprite's
 *   sorted position, called once per sprite in the same order.
 * - [addWithoutPosition] is for a frame expected to be in draw order already. It stores no group
 *   per sprite, so [claimPosition] is unavailable. If [isSorted] turns out false, start over
 *   with [add].
 * - [place] is for a frame expected to have the previous frame's groups, in the same first-seen
 *   order and of the same sizes. It returns each sprite's position right away, and after
 *   [finish] [predictionHeld] tells whether those positions are right. If not, start over with
 *   [add].
 *
 * After [finish], [isSorted] tells whether every sprite already is at its sorted position, and the
 * runs ([runCount], [runTexture], [runFirst], [runSize]) list the consecutive stretches of sorted
 * sprites that share a texture and a space; each is one draw call. Neighboring groups with the
 * same texture in adjacent layers of one space merge into one run. Runs before [firstWorldRun]
 * are [DrawSpace.BACKGROUND] runs, those from there up to [firstScreenRun] are
 * [DrawSpace.WORLD] runs, the rest [DrawSpace.SCREEN] runs. Group sizes are summed per unbroken
 * stretch of one group, not per sprite.
 *
 * For each texture this order keeps, in an array indexed by [Texture.id] and stamped with the
 * frame number, the texture's slot and the [DrawSpace.WORLD] groups of up to four layers, one per
 * value of `layer and 3`. A world sprite whose texture has its layer cached finds its group with a
 * few array reads; otherwise, and for every background and screen sprite, an open-addressing hash
 * table keyed by (space, layer, texture slot) finds it. The state belongs to the order, so any
 * number of orders can be in use at once.
 *
 * The per-sprite functions are inline: on Kotlin/Native a call that is not inlined costs a few
 * nanoseconds of frame setup, which doubled the extract time. The state they touch is therefore
 * internal rather than private.
 */
internal class SpriteOrder {
    /** Number of sprites added since [begin]. */
    internal var size: Int = 0

    internal var frame = 0

    /** Group index of each sprite, in add order. */
    internal var keys = IntArray(1024)

    /**
     * Per-texture state, [TEXTURE_STRIDE] ints per [Texture.id]: the frame that last gave the
     * texture a slot, the slot, then (layer, group + 1) pairs for `layer and 3` = 0 to 3, with
     * group + 1 = 0 for an empty pair. An entry is only valid while its frame is [frame], so
     * [begin] clears nothing. Grows to the largest id seen.
     */
    internal var textureState = IntArray(8 * TEXTURE_STRIDE)

    /** Distinct textures of the frame by slot, in first-seen order. */
    private var textures = arrayOfNulls<Texture>(8)
    private var textureCount = 0

    // Groups: packed key (see keyOf), sprite count, and during claimPosition the next free position.
    private var groupKeys = LongArray(16)
    internal var groupSizes = IntArray(16)
    internal var groupCursors = IntArray(16)
    private var groupCount = 0
    private var sortedKeys = LongArray(16)

    /** Next position [place] hands out per group, starting from the predicted start. */
    internal var placeCursors = IntArray(16)

    // The previous finish's groups: count, keys, sizes and first positions. place() assumes this
    // frame has the same ones.
    private var predictedCount = 0
    private var predictedKeys = LongArray(16)
    private var predictedSizes = IntArray(16)
    private var predictedStarts = IntArray(16)

    /**
     * Whether the last [finish] found the same groups as the finish before it, with the same keys
     * and sizes in the same order, so the positions [place] returned were the sorted ones.
     */
    var predictionHeld: Boolean = false
        private set

    /** Hash table of group index + 1 (0 is empty), sized a power of two at most half full. */
    private var table = IntArray(32)

    /** Group of the sprite added last, or -1. */
    internal var lastGroup = -1

    /** Index of the first sprite of the current stretch of [lastGroup]. */
    internal var stretchStart = 0

    /** Number of times the group changed from one added sprite to the next, counting the first. */
    internal var entries = 0

    /**
     * Whether the last [finish] found the sprites already in draw order, so that [claimPosition]
     * returns each sprite's own index. That holds when each group's sprites were added in one
     * unbroken stretch and the stretches came in sorted order.
     */
    var isSorted: Boolean = true
        private set

    /** Number of runs found by the last [finish]. */
    var runCount: Int = 0
        private set

    /**
     * Index of the first run that is not a [DrawSpace.BACKGROUND] run, found by the last [finish]:
     * the number of background runs, 0 if there is none, and [runCount] if every run is one.
     */
    var firstWorldRun: Int = 0
        private set

    /**
     * Index of the first [DrawSpace.SCREEN] run found by the last [finish], or [runCount] if there
     * is none. It is never below [firstWorldRun], and the runs between them are
     * [DrawSpace.WORLD] runs.
     */
    var firstScreenRun: Int = 0
        private set
    private var runTextures = arrayOfNulls<Texture>(8)
    private var runFirsts = IntArray(8)
    private var runSizes = IntArray(8)

    /** Clears the previous frame. */
    fun begin() {
        frame++
        size = 0
        textures.fill(null, 0, textureCount)
        textureCount = 0
        if (groupCount > 0) table.fill(0)
        groupCount = 0
        runTextures.fill(null, 0, runCount)
        runCount = 0
        firstWorldRun = 0
        firstScreenRun = 0
        lastGroup = -1
        stretchStart = 0
        entries = 0
    }

    /** Adds the next sprite in storage order, drawn in [layer] from [texture] in [DrawSpace.WORLD]. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun add(layer: Int, texture: Texture) {
        addGroup(groupFor(layer, texture))
    }

    /** Adds the next sprite in storage order, of [group] as [groupFor] returned it. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun addGroup(group: Int) {
        val index = size
        enter(group, index)
        if (index == keys.size) keys = keys.copyOf(index * 2)
        keys[index] = group
        size = index + 1
    }

    /** Adds the next sprite like [add] but without recording its group for [claimPosition]. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun addWithoutPosition(layer: Int, texture: Texture) {
        addGroupWithoutPosition(groupFor(layer, texture))
    }

    /** Adds the next sprite like [addGroup] but without recording its group for [claimPosition]. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun addGroupWithoutPosition(group: Int) {
        val index = size
        enter(group, index)
        size = index + 1
    }

    /**
     * Adds the next sprite like [add] and returns its position, assuming this frame's groups are
     * the previous frame's. The position is only right if [predictionHeld] is `true` after
     * [finish]; when the sprite's group was not in the previous frame, it is at least
     * [UNPLACED], so callers can bounds-check it.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun place(layer: Int, texture: Texture): Int = placeGroup(groupFor(layer, texture))

    /** Adds the next sprite of [group] and returns its position, as [place] does. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun placeGroup(group: Int): Int {
        val index = size
        enter(group, index)
        size = index + 1
        val position = placeCursors[group]
        placeCursors[group] = position + 1
        return position
    }

    /**
     * Returns the group of [layer] and [texture] in [space], creating it if new. A
     * [DrawSpace.WORLD] group goes through the per-texture cache; a [DrawSpace.BACKGROUND] or
     * [DrawSpace.SCREEN] group always takes the hash table, which is fine for the few hundred
     * quads a backdrop or a screen overlay has.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun groupFor(layer: Int, texture: Texture, space: DrawSpace): Int =
        if (space === DrawSpace.WORLD) groupFor(layer, texture) else uncachedGroupOf(space, layer, texture)

    /** Returns the [DrawSpace.WORLD] group of [layer] and [texture], creating it if new. */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun groupFor(layer: Int, texture: Texture): Int {
        val state = textureState
        val base = texture.id * TEXTURE_STRIDE
        if (base < state.size && state[base] == frame) {
            val entry = base + PAIRS + ((layer and 3) shl 1)
            if (state[entry] == layer) {
                val group = state[entry + 1]
                if (group != 0) return group - 1
            }
        }
        return groupOf(layer, texture)
    }

    /** Starts a new stretch at sprite [index] if [group] differs from the previous sprite's. */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun enter(group: Int, index: Int) {
        val last = lastGroup
        if (group != last) {
            if (last >= 0) groupSizes[last] += index - stretchStart
            stretchStart = index
            lastGroup = group
            entries++
        }
    }

    /** Sorts the groups, assigns each its first position, and builds the runs. */
    fun finish() {
        val last = lastGroup
        if (last >= 0) groupSizes[last] += size - stretchStart
        lastGroup = -1
        val count = groupCount
        var held = count == predictedCount
        if (held) {
            for (group in 0 until count) {
                if (groupKeys[group] != predictedKeys[group] || groupSizes[group] != predictedSizes[group]) {
                    held = false
                    break
                }
            }
        }
        predictionHeld = held
        if (predictedKeys.size < count) {
            predictedKeys = LongArray(groupKeys.size)
            predictedSizes = IntArray(groupKeys.size)
            predictedStarts = IntArray(groupKeys.size)
        }
        if (sortedKeys.size < count) sortedKeys = LongArray(groupKeys.size)
        groupKeys.copyInto(sortedKeys, 0, 0, count)
        // Signed order of the packed key is (space, layer, texture slot) order; see keyOf.
        sortedKeys.sort(0, count)
        // Groups are numbered in first-added order, so stretches in sorted order number 0, 1, 2...
        var sorted = entries == count
        var start = 0
        var worldRun = -1
        var screenRun = -1
        for (k in 0 until count) {
            val key = sortedKeys[k]
            val group = find(key)
            if (group != k) sorted = false
            val groupSize = groupSizes[group]
            groupCursors[group] = start
            predictedStarts[group] = start
            val texture = textures[(key and SLOT_MASK).toInt()]
            // Keys are sorted, so the first one at or past a space's start begins that space's first run, which
            // never merges into the run before it. With no world groups, the first screen group begins both.
            val startsWorld = key >= WORLD_KEY_START && worldRun < 0
            val startsScreen = key >= SCREEN_KEY_START && screenRun < 0
            if (startsWorld) worldRun = runCount
            if (startsScreen) screenRun = runCount
            val startsSpace = startsWorld || startsScreen
            if (runCount > 0 && !startsSpace && runTextures[runCount - 1] === texture) {
                runSizes[runCount - 1] += groupSize
            } else {
                if (runCount == runTextures.size) growRuns()
                runTextures[runCount] = texture
                runFirsts[runCount] = start
                runSizes[runCount] = groupSize
                runCount++
            }
            start += groupSize
        }
        firstWorldRun = if (worldRun < 0) runCount else worldRun
        firstScreenRun = if (screenRun < 0) runCount else screenRun
        isSorted = sorted
        groupKeys.copyInto(predictedKeys, 0, 0, count)
        groupSizes.copyInto(predictedSizes, 0, 0, count)
        predictedCount = count
    }

    /**
     * Returns the sorted position of sprite [index], the [index]th sprite passed to [add]. Call it
     * once per sprite after [finish], in add order: each call moves its group's cursor.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun claimPosition(index: Int): Int {
        val group = keys[index]
        val position = groupCursors[group]
        groupCursors[group] = position + 1
        return position
    }

    /** Returns the texture of run [run], in `0 until runCount`. */
    fun runTexture(run: Int): Texture = runTextures[run]!!

    /** Returns the sorted position of the first sprite of run [run]. */
    fun runFirst(run: Int): Int = runFirsts[run]

    /** Returns the number of sprites in run [run]. */
    fun runSize(run: Int): Int = runSizes[run]

    /**
     * Returns the [DrawSpace.WORLD] group of [layer] and [texture], creating it if new, and caches
     * it for [texture].
     */
    internal fun groupOf(layer: Int, texture: Texture): Int {
        val group = groupOf(keyOf(WORLD_SPACE, layer, slotOf(texture)))
        val entry = texture.id * TEXTURE_STRIDE + PAIRS + ((layer and 3) shl 1)
        textureState[entry] = layer
        textureState[entry + 1] = group + 1
        return group
    }

    private fun slotOf(texture: Texture): Int {
        val base = texture.id * TEXTURE_STRIDE
        if (base >= textureState.size) growTextureState(base + TEXTURE_STRIDE)
        val state = textureState
        if (state[base] == frame) return state[base + 1]
        if (textureCount == textures.size) textures = textures.copyOf(textureCount * 2)
        val slot = textureCount++
        textures[slot] = texture
        state[base] = frame
        state[base + 1] = slot
        state[base + PAIRS + 1] = 0
        state[base + PAIRS + 3] = 0
        state[base + PAIRS + 5] = 0
        state[base + PAIRS + 7] = 0
        return slot
    }

    /** Grows [textureState] by doubling to at least [size] ints; new entries hold frame 0, never current. */
    private fun growTextureState(size: Int) {
        var grown = textureState.size * 2
        while (grown < size) grown *= 2
        textureState = textureState.copyOf(grown)
    }

    /** Returns the group of [layer] and [texture] in [space], which is not [DrawSpace.WORLD], creating it if new. Nothing is cached. */
    internal fun uncachedGroupOf(space: DrawSpace, layer: Int, texture: Texture): Int =
        groupOf(keyOf(spaceCode(space), layer, slotOf(texture)))

    /** Returns the group of the packed [key], creating it if new. */
    private fun groupOf(key: Long): Int {
        val mask = table.size - 1
        var i = hash(key) and mask
        while (true) {
            val entry = table[i]
            if (entry == 0) break
            if (groupKeys[entry - 1] == key) return entry - 1
            i = (i + 1) and mask
        }
        if (groupCount == groupKeys.size) growGroups()
        val group = groupCount++
        groupKeys[group] = key
        groupSizes[group] = 0
        placeCursors[group] = if (group < predictedCount && predictedKeys[group] == key) predictedStarts[group] else UNPLACED
        table[i] = group + 1
        if (groupCount * 2 > table.size) rehash()
        return group
    }

    private fun find(key: Long): Int {
        val mask = table.size - 1
        var i = hash(key) and mask
        while (true) {
            val entry = table[i]
            if (groupKeys[entry - 1] == key) return entry - 1
            i = (i + 1) and mask
        }
    }

    private fun rehash() {
        val grown = IntArray(table.size * 2)
        val mask = grown.size - 1
        for (group in 0 until groupCount) {
            var i = hash(groupKeys[group]) and mask
            while (grown[i] != 0) i = (i + 1) and mask
            grown[i] = group + 1
        }
        table = grown
    }

    private fun growGroups() {
        val size = groupKeys.size * 2
        groupKeys = groupKeys.copyOf(size)
        groupSizes = groupSizes.copyOf(size)
        groupCursors = groupCursors.copyOf(size)
        placeCursors = placeCursors.copyOf(size)
    }

    private fun growRuns() {
        val size = runTextures.size * 2
        runTextures = runTextures.copyOf(size)
        runFirsts = runFirsts.copyOf(size)
        runSizes = runSizes.copyOf(size)
    }

    override fun toString(): String = "SpriteOrder(sprites=$size, groups=$groupCount, runs=$runCount)"

    internal companion object {
        /** The least position [place] returns for a sprite of a group the previous frame lacked. */
        const val UNPLACED: Int = Int.MAX_VALUE / 2

        /** Ints of [textureState] per texture id: frame, slot and four (layer, group + 1) pairs, padded to 64 bytes. */
        const val TEXTURE_STRIDE: Int = 16

        /** Offset of the first (layer, group + 1) pair inside a texture's entry. */
        const val PAIRS: Int = 2

        private const val BACKGROUND_SPACE = 0L
        private const val WORLD_SPACE = 1L
        private const val SCREEN_SPACE = 2L

        /** Bits of a packed key that hold the texture slot. */
        private const val SLOT_BITS = 29
        private const val SLOT_MASK = (1L shl SLOT_BITS) - 1

        /** The smallest packed key of a [DrawSpace.WORLD] group; every [DrawSpace.BACKGROUND] key is below it. */
        private const val WORLD_KEY_START = WORLD_SPACE shl (32 + SLOT_BITS)

        /** The smallest packed key of a [DrawSpace.SCREEN] group. */
        private const val SCREEN_KEY_START = SCREEN_SPACE shl (32 + SLOT_BITS)

        /** Returns the number [keyOf] packs [space] as, in the order the spaces are drawn. */
        private fun spaceCode(space: DrawSpace): Long = when (space) {
            DrawSpace.BACKGROUND -> BACKGROUND_SPACE
            DrawSpace.WORLD -> WORLD_SPACE
            DrawSpace.SCREEN -> SCREEN_SPACE
        }

        /**
         * Packs a group into a non-negative `Long` whose signed order is (space, layer, slot) order:
         * the layer, shifted to unsigned so negative layers sort first, plus the space times 2^32,
         * fills 34 bits above a 29-bit slot. A frame has far fewer than 2^29 distinct textures.
         */
        private fun keyOf(space: Long, layer: Int, slot: Int): Long =
            (((space shl 32) + (layer.toLong() - Int.MIN_VALUE)) shl SLOT_BITS) or slot.toLong()

        private fun hash(key: Long): Int {
            val mixed = key * -7046029254386353131L
            return (mixed ushr 32).toInt() xor mixed.toInt()
        }
    }
}
