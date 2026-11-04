package klyph.platform

/**
 * An RGBA color with 8 bits per channel, packed as `0xRRGGBBAA`.
 *
 * @sample samples.platform.Colors.color
 */
public value class Color(public val rgba: UInt) {
    /** Red channel in `0.0..1.0`. */
    public val red: Float get() = channel(24)

    /** Green channel in `0.0..1.0`. */
    public val green: Float get() = channel(16)

    /** Blue channel in `0.0..1.0`. */
    public val blue: Float get() = channel(8)

    /** Alpha channel in `0.0..1.0`, where `1.0` is opaque. */
    public val alpha: Float get() = channel(0)

    private fun channel(shift: Int): Float = ((rgba shr shift) and 0xFFu).toInt() / 255f

    override fun toString(): String = "Color(#${rgba.toString(16).padStart(8, '0')})"

    /**
     * Holds shared color constants. Every one but [TRANSPARENT] is opaque, with each channel
     * either full or zero, except [GRAY], whose channels are all `0x80`.
     *
     * @sample samples.platform.Colors.constants
     */
    public companion object {
        /** Opaque white, the identity tint. */
        public val WHITE: Color = Color(0xFFFFFFFFu)

        /** Opaque black, `#000000ff`. */
        public val BLACK: Color = Color(0x000000FFu)

        /** Opaque red, `#ff0000ff`. */
        public val RED: Color = Color(0xFF0000FFu)

        /** Opaque green, `#00ff00ff`. */
        public val GREEN: Color = Color(0x00FF00FFu)

        /** Opaque blue, `#0000ffff`. */
        public val BLUE: Color = Color(0x0000FFFFu)

        /** Opaque yellow, red and green at full, `#ffff00ff`. */
        public val YELLOW: Color = Color(0xFFFF00FFu)

        /** Opaque cyan, green and blue at full, `#00ffffff`. */
        public val CYAN: Color = Color(0x00FFFFFFu)

        /** Opaque magenta, red and blue at full, `#ff00ffff`. */
        public val MAGENTA: Color = Color(0xFF00FFFFu)

        /** Opaque middle gray, every channel `0x80`, `#808080ff`: halfway from [BLACK] to [WHITE]. */
        public val GRAY: Color = Color(0x808080FFu)

        /** Black at zero alpha: draws nothing. */
        public val TRANSPARENT: Color = Color(0x00000000u)
    }
}

/**
 * Returns this color with its alpha channel replaced by [alpha], in `0.0..1.0`, and red, green
 * and blue unchanged. `color.withAlpha(0f)` is the color fully transparent, which is what a fade
 * out ends on.
 *
 * @throws IllegalArgumentException if [alpha] is outside `0.0..1.0`.
 *
 * @sample samples.platform.Colors.withAlpha
 */
public fun Color.withAlpha(alpha: Float): Color {
    require(alpha in 0f..1f) { "Alpha channel $alpha is outside 0.0..1.0." }
    return Color((rgba and 0xFFFFFF00u) or (alpha * 255f + 0.5f).toUInt())
}

/**
 * Returns the color [t] of the way from this color to [to], blending each channel, alpha
 * included, on its own: this color at 0, [to] at 1. [t] is clamped to `0.0..1.0`, because a blend
 * past either end could leave a channel's range.
 *
 * @sample samples.platform.Colors.lerp
 */
public fun Color.lerp(to: Color, t: Float): Color {
    val amount = if (t.isNaN()) 0f else t.coerceIn(0f, 1f)
    return Color(
        lerpChannel(rgba, to.rgba, 24, amount) or
            lerpChannel(rgba, to.rgba, 16, amount) or
            lerpChannel(rgba, to.rgba, 8, amount) or
            lerpChannel(rgba, to.rgba, 0, amount),
    )
}

/** Returns the byte at [shift] blended [amount] of the way from [from] to [to], rounded and shifted back into place. */
private fun lerpChannel(from: UInt, to: UInt, shift: Int, amount: Float): UInt {
    val start = ((from shr shift) and 0xFFu).toInt()
    val end = ((to shr shift) and 0xFFu).toInt()
    val blended = (start + (end - start) * amount + 0.5f).toInt().coerceIn(0, 255)
    return blended.toUInt() shl shift
}

/**
 * Creates a [Color] from channels in `0.0..1.0`. Values outside that range are rejected.
 *
 * @throws IllegalArgumentException if a channel is outside `0.0..1.0`.
 *
 * @sample samples.platform.Colors.color
 */
public fun Color(red: Float, green: Float, blue: Float, alpha: Float = 1f): Color {
    require(red in 0f..1f) { "Red channel $red is outside 0.0..1.0." }
    require(green in 0f..1f) { "Green channel $green is outside 0.0..1.0." }
    require(blue in 0f..1f) { "Blue channel $blue is outside 0.0..1.0." }
    require(alpha in 0f..1f) { "Alpha channel $alpha is outside 0.0..1.0." }
    fun byte(value: Float): UInt = (value * 255f + 0.5f).toUInt()
    return Color((byte(red) shl 24) or (byte(green) shl 16) or (byte(blue) shl 8) or byte(alpha))
}
