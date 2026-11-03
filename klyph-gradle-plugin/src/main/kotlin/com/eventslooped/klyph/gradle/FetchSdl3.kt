package com.eventslooped.klyph.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Downloads SDL's official release for the host into `<cache>/<version>/<file name>`, checks its
 * SHA-256 and unpacks it into a prefix (`include/`, `lib/` and, on Windows, `bin/`) at
 * `<cache>/<version>/<target>/`. The prefix is shared by every build on the machine; a finished
 * one is marked with `.klyph-sdl3` and never downloaded again.
 *
 * A build has one of these, on the root project, however many projects apply the plugin; the
 * work itself goes through [Sdl3FetchService].
 */
@DisableCachingByDefault(because = "Writes into the Gradle user home, shared by every build on the machine.")
internal abstract class FetchSdl3 @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:Internal
    abstract val fetcher: Property<Sdl3FetchService>

    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val fileName: Property<String>

    @get:Input
    abstract val target: Property<String>

    @get:Input
    abstract val version: Property<String>

    /** Expected SHA-256 of the download; absent when the plugin pins none for [version]. */
    @get:Input
    @get:Optional
    abstract val sha256: Property<String>

    @get:Internal
    abstract val downloadDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val prefixDirectory: DirectoryProperty

    @TaskAction
    fun fetch() {
        val name = fileName.get()
        val expected = sha256.orNull?.lowercase() ?: throw GradleException(
            "No SHA-256 is pinned for $name. This plugin pins SDL3 ${Sdl3Release.DEFAULT_VERSION}; for another version pass " +
                "-Pklyph.sdl3.sha256=<hash of $name>, or install SDL3 and set klyph { sdl3Prefix }.",
        )
        val runner = CommandRunner(exec)
        fetcher.get().fetch(
            url = url.get(),
            fileName = name,
            target = target.get(),
            version = version.get(),
            sha256 = expected,
            downloadDirectory = downloadDirectory.get().asFile,
            prefix = prefixDirectory.get().asFile,
            run = runner::run,
            log = { logger.lifecycle(it) },
        )
    }
}

/**
 * Fetches SDL3 for every project of a build, one prefix at a time. Registered once per build
 * under [NAME]; the root project's `fetchSdl3` task calls it. A prefix finished during this build
 * is not looked at again, and [Sdl3Fetcher.fetch] skips one another build already finished.
 */
internal abstract class Sdl3FetchService : BuildService<BuildServiceParameters.None> {
    private val finished = mutableSetOf<File>()

    @Synchronized
    fun fetch(
        url: String,
        fileName: String,
        target: String,
        version: String,
        sha256: String,
        downloadDirectory: File,
        prefix: File,
        run: (List<String>) -> String,
        log: (String) -> Unit,
    ) {
        if (prefix in finished) return
        Sdl3Fetcher.fetch(url, fileName, target, version, sha256, downloadDirectory, prefix, run, log)
        finished += prefix
    }

    companion object {
        const val NAME: String = "klyphSdl3Fetch"
    }
}

/** Runs external tools for [FetchSdl3] and [PrepareDistribution] and returns their output. */
internal class CommandRunner(private val exec: ExecOperations) {
    fun run(command: List<String>): String {
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val result = exec.exec {
            commandLine(command)
            standardOutput = output
            errorOutput = errors
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException("Command failed with exit code ${result.exitValue}: ${command.joinToString(" ")}\n${errors.toString(Charsets.UTF_8).trim()}")
        }
        return output.toString(Charsets.UTF_8)
    }
}

internal object Sdl3Fetcher {
    private const val MARKER = ".klyph-sdl3"

    /** Folder inside the macOS disk image that holds the framework for Apple silicon and Intel. */
    const val DMG_FRAMEWORK: String = "SDL3.xcframework/macos-arm64_x86_64/SDL3.framework"

    fun fetch(
        url: String,
        fileName: String,
        target: String,
        version: String,
        sha256: String,
        downloadDirectory: File,
        prefix: File,
        run: (List<String>) -> String,
        log: (String) -> Unit,
    ) {
        if (isComplete(prefix, sha256)) return
        downloadDirectory.mkdirs()
        // One fetch at a time per machine: other builds may want the same prefix.
        synchronized(this) {
            FileChannel.open(File(downloadDirectory, ".lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use {
                    if (isComplete(prefix, sha256)) return
                    val archive = File(downloadDirectory, fileName)
                    if (!archive.isFile || sha256Of(archive) != sha256) {
                        log("Downloading $url")
                        download(url, archive, sha256)
                    }
                    val staging = Files.createTempDirectory(downloadDirectory.toPath(), "$target-").toFile()
                    try {
                        when (target) {
                            KlyphTargets.MACOS_ARM64 -> unpackDmg(archive, staging, run)
                            KlyphTargets.MINGW_X64 -> unpackMingwZip(archive, staging, version)
                            else -> throw GradleException("SDL publishes no release for $target.")
                        }
                        if (!File(staging, Sdl3Locator.HEADER).isFile) {
                            throw GradleException("$fileName unpacked without ${Sdl3Locator.HEADER}.")
                        }
                        File(staging, MARKER).writeText(sha256)
                        deleteTree(prefix.toPath())
                        Files.move(staging.toPath(), prefix.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        log("SDL3 $version for $target is in $prefix")
                    } finally {
                        deleteTree(staging.toPath())
                    }
                }
            }
        }
    }

    private fun isComplete(prefix: File, sha256: String): Boolean {
        val marker = File(prefix, MARKER)
        return marker.isFile && marker.readText().trim() == sha256
    }

    /** Downloads [url] into [archive]; a file whose SHA-256 is not [sha256] is deleted and fails the build. */
    fun download(url: String, archive: File, sha256: String) {
        val partial = File(archive.path + ".part")
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(30))
            .build()
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(10)).GET().build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofFile(partial.toPath()))
        } catch (e: IOException) {
            partial.delete()
            throw GradleException("Cannot download $url: ${e.message}", e)
        }
        if (response.statusCode() != 200) {
            partial.delete()
            throw GradleException("Downloading $url failed with HTTP status ${response.statusCode()}.")
        }
        val actual = sha256Of(partial)
        if (actual != sha256) {
            partial.delete()
            archive.delete()
            throw GradleException("SHA-256 of ${archive.name} is $actual, expected $sha256. The download was deleted.")
        }
        Files.move(partial.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Builds a prefix from the macOS disk image: `lib/SDL3.framework` copied from the image,
     * `include/SDL3` from its headers, and `lib/libSDL3.dylib` linking to the framework binary so
     * the cinterop klib's `-lSDL3` resolves. The binary's install name is
     * `@rpath/SDL3.framework/Versions/A/SDL3`, so executables need an rpath to `lib/`.
     */
    private fun unpackDmg(archive: File, staging: File, run: (List<String>) -> String) {
        val mount = Files.createTempDirectory("klyph-sdl3-").toFile()
        val lib = File(staging, "lib").apply { mkdirs() }
        run(listOf("hdiutil", "attach", "-nobrowse", "-readonly", "-noverify", "-mountpoint", mount.path, archive.path))
        try {
            val framework = File(mount, DMG_FRAMEWORK)
            if (!framework.isDirectory) throw GradleException("${archive.name} has no $DMG_FRAMEWORK.")
            // ditto keeps the framework's symbolic links and code signature.
            run(listOf("ditto", framework.path, File(lib, "SDL3.framework").path))
        } finally {
            try {
                run(listOf("hdiutil", "detach", mount.path, "-quiet"))
            } catch (_: GradleException) {
                run(listOf("hdiutil", "detach", mount.path, "-force", "-quiet"))
            }
            mount.delete()
        }
        val include = File(staging, "include").apply { mkdirs() }
        run(listOf("ditto", File(lib, "SDL3.framework/Versions/A/Headers").path, File(include, "SDL3").path))
        Files.createSymbolicLink(File(lib, "libSDL3.dylib").toPath(), Path.of("SDL3.framework/Versions/A/SDL3"))
    }

    /** Builds a prefix from the 64-bit half (`SDL3-<version>/x86_64-w64-mingw32/`) of the MinGW package. */
    private fun unpackMingwZip(archive: File, staging: File, version: String) {
        val root = "SDL3-$version/x86_64-w64-mingw32/"
        val destination = staging.toPath().toAbsolutePath().normalize()
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.name.startsWith(root) || entry.name == root) continue
                val path = destination.resolve(entry.name.removePrefix(root)).normalize()
                if (!path.startsWith(destination)) throw GradleException("${archive.name} has an entry outside its folder: ${entry.name}.")
                if (entry.isDirectory) {
                    Files.createDirectories(path)
                } else {
                    Files.createDirectories(path.parent)
                    Files.copy(zip, path, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}

/** Deletes [root] and everything under it without following symbolic links. */
internal fun deleteTree(root: Path) {
    if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(
        root,
        object : java.nio.file.SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                Files.delete(file)
                return java.nio.file.FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): java.nio.file.FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return java.nio.file.FileVisitResult.CONTINUE
            }
        },
    )
}
