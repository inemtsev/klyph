package klyph.render

/**
 * Places rectangles one at a time into a [width] by [height] area without overlap and never moves
 * one once placed, with the skyline bottom-left rule of stb_rect_pack: the area's filled part is
 * kept as a skyline, a row of horizontal segments that together span the whole width, each at the
 * height below which everything is taken. A rectangle goes where its top edge ends lowest, which
 * is "bottom-left" with y growing downwards from the top-left corner, and on a tie at the leftmost
 * such place. Space under an overhang is lost, which is what keeps every insert cheap.
 *
 * [insert] costs O(s²) in the number s of skyline segments, which stays small for sprite-sized
 * rectangles. Not thread-safe.
 */
internal class SkylinePacker(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Packer size ${width}x$height is not positive." }
    }

    /** The skyline, left to right: each segment's left edge, width and the height filled below it. */
    private val segments = ArrayList<Segment>().apply { add(Segment(x = 0, y = 0, width = width)) }

    /** The skyline as it is now, left to right, a copy that later inserts do not change. */
    val skyline: List<Segment> get() = segments.map { Segment(it.x, it.y, it.width) }

    /**
     * Places a rectangle of [width] by [height] and returns its top-left corner, or returns `null`,
     * changing nothing, when no place in the area is free for it.
     *
     * @throws IllegalArgumentException if [width] or [height] is not positive.
     */
    fun insert(width: Int, height: Int): Placement? {
        require(width > 0 && height > 0) { "Rectangle size ${width}x$height is not positive." }
        // A rectangle wider than the area fits on no segment, and one taller than it under no row.
        var bestIndex = -1
        var bestY = Int.MAX_VALUE
        for (index in segments.indices) {
            val x = segments[index].x
            // Written as differences so that a huge width or height cannot overflow.
            if (width > this.width - x) break
            val y = restingHeight(index, width)
            if (height <= this.height - y && y < bestY) {
                bestIndex = index
                bestY = y
            }
        }
        if (bestIndex < 0) return null
        val x = segments[bestIndex].x
        raise(bestIndex, x, width, bestY + height)
        return Placement(x, bestY)
    }

    /** Returns the lowest top edge a rectangle [width] wide can have with its left edge on segment [first]. */
    private fun restingHeight(first: Int, width: Int): Int {
        val right = segments[first].x + width
        var y = 0
        var index = first
        while (index < segments.size && segments[index].x < right) {
            y = maxOf(y, segments[index].y)
            index++
        }
        return y
    }

    /**
     * Raises the skyline from [x] over [width] to [top], starting at segment [first], whose left
     * edge is [x]: the segments wholly under the new one go, one cut by its right edge is
     * shortened, and neighbors at the same height merge.
     */
    private fun raise(first: Int, x: Int, width: Int, top: Int) {
        val right = x + width
        // Drop or trim every segment the new one covers, from first on.
        while (first < segments.size && segments[first].x < right) {
            val segment = segments[first]
            val segmentRight = segment.x + segment.width
            if (segmentRight <= right) {
                segments.removeAt(first)
            } else {
                segment.width = segmentRight - right
                segment.x = right
                break
            }
        }
        segments.add(first, Segment(x = x, y = top, width = width))
        merge()
    }

    /** Joins neighboring segments at the same height into one. */
    private fun merge() {
        var index = 0
        while (index < segments.size - 1) {
            val left = segments[index]
            val next = segments[index + 1]
            if (left.y == next.y) {
                left.width += next.width
                segments.removeAt(index + 1)
            } else {
                index++
            }
        }
    }

    /** Returns `SkylinePacker(64x64, skyline=[0+16@8, 16+48@0])`: each segment as its left edge, `+` its width and `@` its height. */
    override fun toString(): String =
        "SkylinePacker(${width}x$height, skyline=${segments.joinToString(prefix = "[", postfix = "]") { "${it.x}+${it.width}@${it.y}" }})"

    /** One segment of the skyline: [width] columns from [x] on are filled from the top down to row [y]. */
    class Segment(var x: Int, var y: Int, var width: Int) {
        override fun equals(other: Any?): Boolean = other is Segment && other.x == x && other.y == y && other.width == width

        override fun hashCode(): Int = 31 * (31 * x + y) + width

        override fun toString(): String = "Segment(x=$x, y=$y, width=$width)"
    }
}

/** Where [SkylinePacker.insert] put a rectangle: its top-left corner at column [x] and row [y]. */
internal class Placement(val x: Int, val y: Int) {
    override fun equals(other: Any?): Boolean = other is Placement && other.x == x && other.y == y

    override fun hashCode(): Int = 31 * x + y

    override fun toString(): String = "Placement(x=$x, y=$y)"
}
