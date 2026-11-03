package com.eventslooped.klyph.gradle

import org.gradle.api.GradleException
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Sdl3FetcherTest {
    private val cache: File = createTempDirectory("klyph-sdl3-cache").toFile()

    @AfterTest
    fun cleanUp() {
        deleteTree(cache.toPath())
    }

    @Test
    fun sha256OfKnownBytes() {
        val file = File(cache, "abc.txt").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sdl3Fetcher.sha256Of(file))
    }

    @Test
    fun cachedMingwPackageUnpacksIntoPrefix() {
        val versionDirectory = File(cache, "3.4.18").apply { mkdirs() }
        val archive = File(versionDirectory, "SDL3-devel-3.4.18-mingw.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            for (name in listOf(
                "SDL3-3.4.18/i686-w64-mingw32/bin/SDL3.dll",
                "SDL3-3.4.18/x86_64-w64-mingw32/bin/SDL3.dll",
                "SDL3-3.4.18/x86_64-w64-mingw32/include/SDL3/SDL.h",
                "SDL3-3.4.18/x86_64-w64-mingw32/lib/libSDL3.dll.a",
            )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(name.toByteArray())
                zip.closeEntry()
            }
        }
        val prefix = File(versionDirectory, "mingwX64")
        val commands = mutableListOf<List<String>>()
        // The archive is already in the cache with the expected hash, so nothing is downloaded.
        repeat(2) {
            Sdl3Fetcher.fetch(
                url = "https://invalid.example/never-used",
                fileName = archive.name,
                target = KlyphTargets.MINGW_X64,
                version = "3.4.18",
                sha256 = Sdl3Fetcher.sha256Of(archive),
                downloadDirectory = versionDirectory,
                prefix = prefix,
                run = { commands += it; "" },
                log = {},
            )
        }
        assertTrue(File(prefix, "include/SDL3/SDL.h").isFile)
        assertTrue(File(prefix, "lib/libSDL3.dll.a").isFile)
        assertEquals("SDL3-3.4.18/x86_64-w64-mingw32/bin/SDL3.dll", File(prefix, "bin/SDL3.dll").readText())
        assertFalse(File(prefix, "i686-w64-mingw32").exists())
        assertEquals(emptyList(), commands)
        assertEquals(setOf(archive.name, "mingwX64", ".lock"), versionDirectory.list()!!.toSet())
    }

    @Test
    fun mismatchedDownloadIsDeleted() {
        val directory = File(cache, "3.4.18").apply { mkdirs() }
        val archive = File(directory, "SDL3-3.4.18.dmg")
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/asset") { exchange ->
            val body = "not the release".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val failure = assertFailsWith<GradleException> {
                Sdl3Fetcher.download("http://127.0.0.1:${server.address.port}/asset", archive, "0".repeat(64))
            }
            assertTrue(failure.message.orEmpty().contains("was deleted"))
        } finally {
            server.stop(0)
        }
        assertFalse(archive.exists())
        assertFalse(File(archive.path + ".part").exists())
    }
}
