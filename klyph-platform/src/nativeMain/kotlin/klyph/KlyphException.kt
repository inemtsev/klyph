package klyph

/**
 * Root of every exception that carries klyph meaning.
 *
 * Catch this type to handle any failure the library reports on purpose. Lower-level exceptions the
 * library catches internally are wrapped with the original as [cause].
 */
public open class KlyphException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
