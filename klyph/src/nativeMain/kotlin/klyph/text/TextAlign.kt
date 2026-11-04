package klyph.text

/**
 * Says where each line of a text sits relative to the text's anchor x.
 *
 * Alignment applies per line, so the lines of a centered block are each centered on the same x.
 * The anchor's y is always the top of the first line.
 *
 * Has no sample because laying out text needs a [Font], whose textures need a GPU device that
 * samples cannot open.
 */
public enum class TextAlign {
    /** Each line starts at the anchor. */
    LEFT,

    /** Each line is centered on the anchor. */
    CENTER,

    /** Each line ends at the anchor. */
    RIGHT,
}
