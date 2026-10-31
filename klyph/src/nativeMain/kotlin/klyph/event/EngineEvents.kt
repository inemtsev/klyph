package klyph.event

import klyph.platform.KeyCode

/**
 * Published when the user asks the application to quit: the window close button, the system quit
 * shortcut (Cmd+Q on macOS), or an interrupt signal.
 */
public object QuitRequested {
    override fun toString(): String = "QuitRequested"
}

/**
 * Published when the window's drawable area changes size. [pixelWidth] and [pixelHeight] are the
 * new size in physical pixels, which differ from window coordinates on high-density displays.
 */
public class WindowResized(
    /** The new drawable width in physical pixels. */
    public val pixelWidth: Int,
    /** The new drawable height in physical pixels. */
    public val pixelHeight: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is WindowResized && other.pixelWidth == pixelWidth && other.pixelHeight == pixelHeight

    override fun hashCode(): Int = 31 * pixelWidth + pixelHeight

    override fun toString(): String = "WindowResized(pixelWidth=$pixelWidth, pixelHeight=$pixelHeight)"
}

/**
 * Published when the window's pixel density changes, typically because it moved to a display
 * with a different density. [pixelDensity] is the new number of pixels per point, as in
 * [klyph.platform.Window.pixelDensity].
 *
 * Before publishing it the engine multiplies `App.camera.zoom` by the ratio of the new density to
 * the old one, so the view keeps its framing in points.
 */
public class WindowDensityChanged(
    /** The new number of pixels per point: 2 on a Retina display, 1 on a standard one. */
    public val pixelDensity: Float,
) {
    override fun equals(other: Any?): Boolean = other is WindowDensityChanged && other.pixelDensity == pixelDensity

    override fun hashCode(): Int = pixelDensity.hashCode()

    override fun toString(): String = "WindowDensityChanged(pixelDensity=$pixelDensity)"
}

/**
 * Published when [key] goes down. Holding the key publishes further events with [isRepeat] set to
 * `true` at the system's key repeat rate.
 */
public class KeyPressed(
    /** The key that went down. */
    public val key: KeyCode,
    /** `true` when the event comes from the system's key repeat rather than a new press. */
    public val isRepeat: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is KeyPressed && other.key == key && other.isRepeat == isRepeat

    override fun hashCode(): Int = 31 * key.hashCode() + isRepeat.hashCode()

    override fun toString(): String = "KeyPressed(key=$key, isRepeat=$isRepeat)"
}

/** Published when [key] goes up. */
public class KeyReleased(
    /** The key that went up. */
    public val key: KeyCode,
) {
    override fun equals(other: Any?): Boolean = other is KeyReleased && other.key == key

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = "KeyReleased(key=$key)"
}
