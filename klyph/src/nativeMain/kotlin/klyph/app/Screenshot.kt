package klyph.app

/**
 * Names one frame for [runApp] to capture into a PNG file, as [AppConfig.screenshot].
 *
 * [frame] counts from 1 for the first frame, the way `KLYPH_FRAMES` counts, so `Screenshot(1,
 * "first.png")` captures the frame during which [Time.frameCount] is 0. [path] is written through
 * `Window.capture`: a relative path resolves against the directory that holds the executable, and
 * a missing directory on the way to the file is created. A frame the app never reaches is not captured.
 *
 * Two screenshots are equal when their frames and paths are equal.
 *
 * @throws IllegalArgumentException if [frame] is less than 1 or [path] is empty.
 *
 * @sample samples.app.Screenshots.screenshot
 */
public class Screenshot(
    /** The number of the frame to capture, counting from 1. */
    public val frame: Int,
    /** The file the frame is saved to as a PNG. */
    public val path: String,
) {
    init {
        require(frame >= 1) { "Screenshot frame $frame is less than one." }
        require(path.isNotEmpty()) { "Screenshot path is empty." }
    }

    override fun equals(other: Any?): Boolean = other is Screenshot && frame == other.frame && path == other.path

    override fun hashCode(): Int = 31 * frame + path.hashCode()

    /** Returns `Screenshot(frame=20, path=shot.png)`. The format may change. */
    override fun toString(): String = "Screenshot(frame=$frame, path=$path)"
}

/**
 * Returns the screenshot that a `KLYPH_SCREENSHOT` value of the form `<frame>:<path>` names, such
 * as `20:/tmp/shot.png`, or `null` when [value] is `null` or does not have that form: the frame
 * is not a whole number of at least 1, or the path is empty. The value splits at its first colon,
 * so a path may contain colons, as Windows paths such as `C:/shots/a.png` do.
 */
internal fun parseScreenshot(value: String?): Screenshot? {
    if (value == null) return null
    val colon = value.indexOf(':')
    if (colon < 0) return null
    val frame = value.substring(0, colon).trim().toIntOrNull() ?: return null
    val path = value.substring(colon + 1)
    if (frame < 1 || path.isEmpty()) return null
    return Screenshot(frame, path)
}
