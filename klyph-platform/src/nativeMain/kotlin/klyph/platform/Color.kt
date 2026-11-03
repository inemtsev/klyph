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

    public companion object {
        /** Opaque white, the identity tint. */
        public val WHITE: Color = Color(0xFFFFFFFFu)
    }
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
