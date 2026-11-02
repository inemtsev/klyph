package klyph.platform

import platform.posix.fflush
import platform.posix.fputs
import platform.posix.stderr

/**
 * Writes [message] and a line break to the standard error stream and flushes it, for warnings
 * that should not mix with a program's normal output on standard output.
 */
public fun printError(message: String) {
    fputs(message + "\n", stderr)
    fflush(stderr)
}
