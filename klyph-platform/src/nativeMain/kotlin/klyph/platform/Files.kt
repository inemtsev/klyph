package klyph.platform

import klyph.KlyphException
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.size_tVar
import sdl3.SDL_GetBasePath
import sdl3.SDL_GetError
import sdl3.SDL_LoadFile
import sdl3.SDL_free

/**
 * Reads whole files from disk. Relative paths resolve against [basePath], the directory that holds
 * the running executable, so assets shipped next to the game are found wherever it is started from.
 * Absolute paths are used as given.
 *
 * Has no sample because it needs a file on disk.
 */
public object Files {
    /**
     * Returns the directory that holds the running executable, ending with a path separator.
     *
     * The value is read from SDL once and cached. When SDL cannot report it, which can happen
     * before [Sdl.init] on some platforms, the value is `"./"` and relative paths resolve against
     * the current working directory instead.
     */
    public val basePath: String by lazy { SDL_GetBasePath()?.toKString() ?: "./" }

    /**
     * Returns the contents of the file at [path].
     *
     * A relative [path] resolves against [basePath]; an absolute one is used as given.
     *
     * Has no sample because it needs a file on disk.
     *
     * @throws FileException if the file does not exist, cannot be read, or is larger than
     *   [Int.MAX_VALUE] bytes.
     */
    public fun readBytes(path: String): ByteArray = readBytesOrElse(
        path,
        onSuccess = { it },
        onFailure = { resolved, reason -> throw FileException("File '$resolved' could not be read: $reason.") },
    )

    /**
     * Returns the contents of the file at [path], or `null` when the file does not exist, cannot be
     * read, or is larger than [Int.MAX_VALUE] bytes.
     *
     * A relative [path] resolves against [basePath]; an absolute one is used as given.
     *
     * Has no sample because it needs a file on disk.
     */
    public fun readBytesOrNull(path: String): ByteArray? = readBytesOrElse(
        path,
        onSuccess = { it },
        onFailure = { _, _ -> null },
    )

    /**
     * Returns the contents of the file at [path] decoded as UTF-8. Malformed byte sequences become
     * the replacement character `U+FFFD`.
     *
     * A relative [path] resolves against [basePath]; an absolute one is used as given.
     *
     * Has no sample because it needs a file on disk.
     *
     * @throws FileException if the file does not exist, cannot be read, or is larger than
     *   [Int.MAX_VALUE] bytes.
     */
    public fun readText(path: String): String = readBytes(path).decodeToString()

    private inline fun <R> readBytesOrElse(
        path: String,
        onSuccess: (ByteArray) -> R,
        onFailure: (resolved: String, reason: String) -> R,
    ): R {
        val resolved = resolve(path)
        val bytes = memScoped {
            val size = alloc<size_tVar>()
            val data = SDL_LoadFile(resolved, size.ptr)
                ?: return onFailure(resolved, SDL_GetError()?.toKString().orEmpty())
            try {
                if (size.value > Int.MAX_VALUE.toULong()) {
                    return onFailure(resolved, "it is larger than ${Int.MAX_VALUE} bytes")
                }
                // readBytes(0) is fine: SDL returns a valid, empty buffer for an empty file.
                data.reinterpret<ByteVar>().readBytes(size.value.toInt())
            } finally {
                SDL_free(data)
            }
        }
        return onSuccess(bytes)
    }

    private fun resolve(path: String): String = if (isAbsolute(path)) path else basePath + path

    private fun isAbsolute(path: String): Boolean =
        path.startsWith('/') ||
            path.startsWith('\\') ||
            (path.length >= 2 && path[1] == ':' && path[0].isLetter())

    override fun toString(): String = "Files"
}

/**
 * Thrown when a file cannot be read. The message names the file and repeats the reason SDL gives.
 */
public class FileException(message: String) : KlyphException(message)
