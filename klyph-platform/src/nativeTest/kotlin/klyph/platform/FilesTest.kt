package klyph.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import sdl3.SDL_RemovePath
import sdl3.SDL_SaveFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilesTest {
    private val fileName = "klyph-files-test.txt"
    private val content = "héllo, klyph\n".encodeToByteArray()

    @AfterTest
    fun removeTestFile() {
        SDL_RemovePath(Files.basePath + fileName)
    }

    @Test
    fun basePathEndsWithSeparator() {
        val last = Files.basePath.last()
        assertTrue(last == '/' || last == '\\', Files.basePath)
    }

    @Test
    fun readBytesOrNullReturnsNullForMissingFile() {
        assertNull(Files.readBytesOrNull("does-not-exist.bin"))
    }

    @Test
    fun readBytesThrowsForMissingFile() {
        val e = assertFailsWith<FileException> { Files.readBytes("does-not-exist.bin") }
        val message = e.message.orEmpty()
        assertTrue(message.startsWith("File '" + Files.basePath + "does-not-exist.bin' could not be read: "), message)
        assertTrue(message.endsWith("."), message)
    }

    @Test
    fun readTextThrowsForMissingFile() {
        assertFailsWith<FileException> { Files.readText("does-not-exist.txt") }
    }

    @Test
    fun readsFileRelativeToBasePath() {
        writeTestFile()
        assertContentEquals(content, Files.readBytes(fileName))
        assertContentEquals(content, Files.readBytesOrNull(fileName))
        assertEquals("héllo, klyph\n", Files.readText(fileName))
    }

    @Test
    fun readsFileByAbsolutePath() {
        writeTestFile()
        assertContentEquals(content, Files.readBytes(Files.basePath + fileName))
    }

    /** Writes [content] next to the test executable through SDL, so the test stays portable. */
    private fun writeTestFile() {
        val saved = content.usePinned { SDL_SaveFile(Files.basePath + fileName, it.addressOf(0), content.size.convert()) }
        assertTrue(saved, "Could not write the test file.")
    }
}
