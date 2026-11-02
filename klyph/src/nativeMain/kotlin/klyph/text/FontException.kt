package klyph.text

import klyph.KlyphException

/**
 * Thrown when a font file cannot be turned into a [Font]: it is a binary BMFont, a line has a
 * value that is not a number, a required line is missing, or a glyph lies outside its page. The
 * message names the file.
 */
public class FontException(message: String, cause: Throwable? = null) : KlyphException(message, cause)
