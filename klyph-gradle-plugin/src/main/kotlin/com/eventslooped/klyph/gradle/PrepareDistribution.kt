package com.eventslooped.klyph.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/**
 * Lays out one distribution folder: the release executable, `assets/` and the SDL3 library the
 * executable loads, so the folder runs on a machine without SDL3. `distribute<Target>` zips it.
 */
@DisableCachingByDefault(because = "Copies files; restoring them is no faster than copying.")
internal abstract class PrepareDistribution @Inject constructor(
    private val exec: ExecOperations,
    private val objects: ObjectFactory,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val executable: RegularFileProperty

    @get:Internal
    abstract val assetsDirectory: DirectoryProperty

    /** Ant-style patterns, relative to [assetsDirectory], of files left out of `assets/`. */
    @get:Input
    abstract val assetExcludes: ListProperty<String>

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val assetFiles: FileTree
        get() = objects.fileTree().from(assetsDirectory).apply { exclude(assetExcludes.get()) }

    /** Kotlin/Native target name, such as `macosArm64`. */
    @get:Input
    abstract val target: Property<String>

    @get:Internal
    abstract val sdl3Prefix: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val libraryFiles: FileTree
        get() = objects.fileTree().from(sdl3Prefix).apply { include(DistributionLayout.libraryPatterns(target.get(), sdl3Prefix.get().asFile)) }

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @TaskAction
    fun prepare() {
        val out = destination.get().asFile
        deleteTree(out.toPath())
        out.mkdirs()

        val linked = executable.get().asFile
        val copied = File(out, linked.name)
        Files.copy(linked.toPath(), copied.toPath(), StandardCopyOption.COPY_ATTRIBUTES)

        val assets = File(out, "assets")
        assetFiles.visit {
            val copy = File(assets, relativePath.pathString)
            if (isDirectory) {
                copy.mkdirs()
            } else {
                copy.parentFile.mkdirs()
                Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        val prefix = sdl3Prefix.get().asFile
        val libraries = DistributionLayout.libraries(target.get(), prefix)
            ?: throw GradleException("Cannot find the SDL3 library to ship in $prefix. Looked for ${DistributionLayout.libraryPatterns(target.get(), prefix).joinToString()}.")
        for ((source, relative) in libraries) {
            val target = File(out, relative)
            target.parentFile.mkdirs()
            // Follows symbolic links, so libSDL3.so.0 arrives as the library itself.
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        if (KlyphTargets.family(target.get()) == "OSX") MacExecutable.loadSdl3FromOwnFolder(copied, CommandRunner(exec)::run)
    }
}

/**
 * What a distribution folder holds next to the executable, per Kotlin/Native target. Libraries
 * come from the folder [Sdl3Locator.libraryDir] finds in the prefix, `bin/` on Windows.
 */
internal object DistributionLayout {
    /** Returns the folder name inside the zip, which is also the zip's base name. */
    fun folderName(project: String, version: String, target: String): String =
        if (version.isEmpty() || version == "unspecified") "$project-$target" else "$project-$version-$target"

    /** Returns the [prefix]-relative patterns of the files the distribution for [target] may copy. */
    fun libraryPatterns(target: String, prefix: File): List<String> {
        val lib = Sdl3Locator.libraryDir(prefix, target).relativeTo(prefix).invariantSeparatorsPath
        return when (KlyphTargets.family(target)) {
            "OSX" -> listOf("$lib/libSDL3.0.dylib", "$lib/SDL3.framework/Versions/A/SDL3", "$lib/SDL3.framework/Versions/A/Resources/**")
            "LINUX" -> listOf("$lib/libSDL3.so.0")
            else -> listOf("bin/SDL3.dll")
        }
    }

    /**
     * Returns each library file in [prefix] for [target] with its path inside the distribution,
     * or `null` if the prefix has none. On macOS a framework (a downloaded SDL) wins over `libSDL3.0.dylib`
     * (Homebrew or a source build); only its binary and resources are copied, since the
     * executable loads `@rpath/SDL3.framework/Versions/A/SDL3`.
     */
    fun libraries(target: String, prefix: File): List<Pair<File, String>>? = when (KlyphTargets.family(target)) {
        "OSX" -> {
            val lib = Sdl3Locator.libraryDir(prefix, target)
            val version = File(lib, "SDL3.framework/Versions/A")
            val dylib = File(lib, "libSDL3.0.dylib")
            when {
                File(version, "SDL3").isFile -> listOf(File(version, "SDL3") to "SDL3.framework/Versions/A/SDL3") +
                    File(version, "Resources").walkTopDown().filter { it.isFile }
                        .map { it to "SDL3.framework/Versions/A/Resources/" + it.relativeTo(File(version, "Resources")).invariantSeparatorsPath }
                dylib.isFile -> listOf(dylib to "libSDL3.0.dylib")
                else -> null
            }
        }
        "LINUX" -> File(Sdl3Locator.libraryDir(prefix, target), "libSDL3.so.0").takeIf { it.isFile }?.let { listOf(it to "libSDL3.so.0") }
        "MINGW" -> File(prefix, "bin/SDL3.dll").takeIf { it.isFile }?.let { listOf(it to "SDL3.dll") }
        else -> null
    }
}

/** Rewrites a copied macOS executable so it loads SDL3 from its own folder. */
internal object MacExecutable {
    /**
     * Points the executable's SDL3 reference at `@rpath`, adds `@executable_path` to its rpaths
     * when the link did not, and signs it again (ad hoc) if anything changed, because Apple
     * silicon refuses to run a binary whose signature no longer matches.
     */
    fun loadSdl3FromOwnFolder(executable: File, run: (List<String>) -> String) {
        var changed = false
        val sdl3 = linkedLibraries(run(listOf("otool", "-L", executable.path))).firstOrNull { isSdl3(it) }
        if (sdl3 != null) {
            val replacement = rpathName(sdl3)
            if (sdl3 != replacement) {
                run(listOf("install_name_tool", "-change", sdl3, replacement, executable.path))
                changed = true
            }
        }
        if ("@executable_path" !in rpaths(run(listOf("otool", "-l", executable.path)))) {
            run(listOf("install_name_tool", "-add_rpath", "@executable_path", executable.path))
            changed = true
        }
        if (changed) run(listOf("codesign", "--force", "--sign", "-", executable.path))
    }

    fun isSdl3(path: String): Boolean = path.contains("SDL3.framework/") || path.substringAfterLast('/').startsWith("libSDL3.")

    /** Returns the `@rpath` name the distribution ships the library under. */
    fun rpathName(path: String): String =
        if (path.contains("SDL3.framework/")) "@rpath/SDL3.framework/Versions/A/SDL3" else "@rpath/libSDL3.0.dylib"

    /** Parses `otool -L` output into the install names of the linked libraries. */
    fun linkedLibraries(otoolL: String): List<String> =
        otoolL.lines().drop(1).map { it.trim() }.filter { it.isNotEmpty() }.map { it.substringBefore(" (compatibility").trim() }

    /** Parses `otool -l` output into the paths of its `LC_RPATH` commands. */
    fun rpaths(otoolLoadCommands: String): List<String> {
        val lines = otoolLoadCommands.lines().map { it.trim() }
        return lines.indices.filter { lines[it] == "cmd LC_RPATH" }.mapNotNull { index ->
            lines.drop(index + 1).take(3).firstOrNull { it.startsWith("path ") }?.removePrefix("path ")?.substringBefore(" (offset")
        }
    }
}
