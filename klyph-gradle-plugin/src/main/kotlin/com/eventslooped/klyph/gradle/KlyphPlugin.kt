package com.eventslooped.klyph.gradle

import org.gradle.api.GradleException
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.konan.target.Family
import java.io.File

/**
 * Turns a project into a klyph game: applies Kotlin Multiplatform, configures the klyph targets
 * with one executable each, finds or downloads SDL3, and registers `run`, `runRelease`,
 * `copyAssets`, `distribute` and, when SDL3 has to be downloaded, one `fetchSdl3` on the root
 * project for the whole build.
 *
 * Configure it through [KlyphExtension] (`klyph { entryPoint = "my.game.main" }`).
 */
public class KlyphPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        KlyphBuild.apply(project, game = true)
    }
}

/**
 * Turns a project into a klyph library, such as the shared code of a game split into modules:
 * the same targets, SDL3 lookup and link settings for its test binaries as [KlyphPlugin], but no
 * executable, no `entryPoint` and none of the run, asset or distribution tasks.
 *
 * Its id is `com.eventslooped.klyph.library`; configure it through [KlyphExtension].
 */
public class KlyphLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        KlyphBuild.apply(project, game = false)
    }
}

private class KlyphBuild(
    private val project: Project,
    private val extension: KlyphExtension,
    private val copyAssets: TaskProvider<Task>?,
) {
    private val providers = project.providers

    fun configure() {
        val entryPoint = if (copyAssets == null) {
            null
        } else {
            extension.entryPoint.orNull?.takeIf { it.isNotBlank() }
                ?: throw GradleException("Set klyph { entryPoint = \"my.game.main\" }.")
        }
        val host = extension.hostTarget.get()
        val targetNames = extension.configuredTargets.get()
        val sdl3 = locateSdl3(host)
        extension.locatedSdl3 = sdl3.prefix
        val fetch = (sdl3 as? Sdl3Location.Download)?.let { registerFetch(it) }
        // A cinterop of the project may read SDL3's headers (KlyphExtension.sdl3IncludeDir).
        if (fetch != null) project.tasks.withType(CInteropProcess::class.java).configureEach { dependsOn(fetch) }
        // Binaries link against the one SDL3 prefix found for the host, which holds the host's
        // library only. So a build that configures the host links only the host's executables
        // and test binaries; the other targets still compile. A build without the host target is
        // a cross build (-Pklyph.target=mingwX64 -Psdl3.prefix=<MinGW folder>) and links them all.
        val crossBuild = host !in targetNames

        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        for (name in targetNames) {
            val target = when (name) {
                KlyphTargets.MACOS_ARM64 -> kotlin.macosArm64()
                KlyphTargets.LINUX_X64 -> kotlin.linuxX64()
                KlyphTargets.LINUX_ARM64 -> kotlin.linuxArm64()
                KlyphTargets.MINGW_X64 -> kotlin.mingwX64()
                else -> throw GradleException("Unknown klyph target '$name'.")
            }
            configureTarget(target, entryPoint, sdl3.prefix, fetch, isHost = name == host, linksBinaries = name == host || crossBuild)
        }
    }

    private fun locateSdl3(host: String): Sdl3Location {
        val propertyPrefix = providers.gradleProperty("sdl3.prefix").orNull
        val extensionPrefix = extension.sdl3Prefix.orNull
        val candidates = Sdl3Locator.candidates(
            propertyPrefix = propertyPrefix,
            extensionPrefix = extensionPrefix,
            ignoreSystem = providers.gradleProperty("klyph.sdl3.ignoreSystem").map { it.toBoolean() }.getOrElse(false),
            home = providers.systemProperty("user.home").get(),
        )
        val found = providers.of(Sdl3PrefixSource::class.java) { parameters.candidates.set(candidates) }.orNull
        val explicit = listOf("-Psdl3.prefix" to propertyPrefix, "klyph { sdl3Prefix }" to extensionPrefix)
            .filter { !it.second.isNullOrBlank() }
        for ((source, path) in explicit) {
            if (found != path) project.logger.warn("klyph: SDL3 prefix $path from $source has no ${Sdl3Locator.HEADER}; looking further.")
            else break
        }
        val location = Sdl3Locator.locate(
            found = found,
            looked = candidates,
            host = host,
            version = extension.sdl3Version.get(),
            cacheRoot = File(project.gradle.gradleUserHomeDir, "caches/klyph/sdl3"),
            download = providers.gradleProperty("klyph.sdl3.download").map { it.toBoolean() }.getOrElse(true),
        )
        project.logger.info("klyph: SDL3 for ${project.path} is $location")
        return location
    }

    /**
     * Returns the build's one `fetchSdl3` task, on the root project, registering it for the first
     * project that needs it. Every project of a build downloads into the same prefix, so a later
     * project that wants another one stops the build. The prefix is recorded as a string in the
     * root's extra properties because projects may load this plugin in different class loaders.
     */
    private fun registerFetch(location: Sdl3Location.Download): TaskProvider<Task> {
        val root = project.rootProject
        val extra = root.extensions.extraProperties
        val registered = if (extra.has(FETCH_PREFIX)) extra.get(FETCH_PREFIX) as String else null
        if (registered != null) {
            if (registered != location.prefix.path) {
                throw GradleException(
                    "${project.path} downloads SDL3 into ${location.prefix}, but another project of this build downloads it into $registered. " +
                        "Set the same klyph { sdl3Version } in every project.",
                )
            }
            return root.tasks.named(FETCH_TASK)
        }
        extra.set(FETCH_PREFIX, location.prefix.path)
        val release = location.release
        val service = project.gradle.sharedServices.registerIfAbsent(Sdl3FetchService.NAME, Sdl3FetchService::class.java) {}
        val rootProviders = root.providers
        root.tasks.register(FETCH_TASK, FetchSdl3::class.java) {
            group = "build setup"
            description = "Downloads SDL ${release.version} for ${release.target} into ${location.prefix}, once for every project of the build."
            fetcher.set(service)
            usesService(service)
            url.set(release.url)
            fileName.set(release.fileName)
            target.set(release.target)
            version.set(release.version)
            sha256.set(rootProviders.provider { release.sha256 }.orElse(rootProviders.gradleProperty("klyph.sdl3.sha256")))
            downloadDirectory.set(location.prefix.parentFile)
            prefixDirectory.set(location.prefix)
        }
        return root.tasks.named(FETCH_TASK)
    }

    private fun configureTarget(
        target: KotlinNativeTarget,
        entryPoint: String?,
        prefix: File,
        fetch: TaskProvider<Task>?,
        isHost: Boolean,
        linksBinaries: Boolean,
    ) {
        val family = target.konanTarget.family
        val sdl3Lib = providers.of(Sdl3LibraryDirSource::class.java) {
            parameters.prefix.set(prefix.path)
            parameters.target.set(target.name)
        }.get()
        val sdl3Dll = File(prefix, "bin/SDL3.dll")

        // A library has no executable, so the rest of this function only sets up its test binaries.
        if (entryPoint != null) {
            target.binaries.executable {
                this.entryPoint = entryPoint
            }
        }

        // Every binary (executables and test runners) links against SDL3, whose library name is
        // carried by the engine's cinterop klib; only the search path and runtime path are added.
        target.binaries.configureEach {
            // Release executables look next to themselves first, where `distribute` puts SDL3.
            if (this is Executable && buildType == NativeBuildType.RELEASE) {
                when (family) {
                    Family.OSX -> linkerOpts("-Wl,-rpath,@executable_path")
                    Family.LINUX -> linkerOpts("-Wl,-rpath,\$ORIGIN")
                    else -> {}
                }
            }
            linkerOpts("-L$sdl3Lib")
            if (family != Family.MINGW) linkerOpts("-Wl,-rpath,$sdl3Lib")
            // Kotlin/Native links Linux binaries against its own sysroot with an old glibc, while
            // libSDL3.so from a current distro references newer glibc symbol versions. Skip the
            // link-time check; the loader resolves them against the host glibc at run time.
            if (family == Family.LINUX) linkerOpts("--allow-shlib-undefined")
            linkTaskProvider.configure {
                // Skipped, not removed, so `assemble`, `build` and `check` still succeed.
                enabled = linksBinaries
                if (fetch != null) dependsOn(fetch)
                // Windows has no rpath; it loads DLLs from the executable's own folder, so every
                // link copies SDL3.dll next to its output, as part of the link's outputs.
                if (family == Family.MINGW) {
                    inputs.files(sdl3Dll).withPropertyName("sdl3Dll").withPathSensitivity(PathSensitivity.NONE)
                    val destination = destinationDirectory
                    doLast {
                        if (!sdl3Dll.isFile) {
                            throw GradleException("Cannot find $sdl3Dll to copy next to the binary. Check the SDL3 prefix.")
                        }
                        sdl3Dll.copyTo(destination.get().asFile.resolve(sdl3Dll.name), overwrite = true)
                    }
                }
            }
        }

        val allAssets = copyAssets ?: return
        target.binaries.withType(Executable::class.java).configureEach {
            val binary = this
            val link = binary.linkTaskProvider
            val suffix = binary.linkTaskName.removePrefix("link")

            // The game loads its files from the assets/ folder next to the executable, so every
            // link is followed by a sync into <output folder>/assets/. The sync runs after the
            // link, not before, so a link restored from the build cache cannot wipe it.
            val assets = project.tasks.register("copyAssets$suffix", Sync::class.java) {
                description = "Copies the assets folder next to the ${binary.name} binary for ${target.name}."
                from(extension.assetsDir) { exclude(extension.assetExcludes.get()) }
                into(binary.outputDirectoryProperty.dir("assets"))
                mustRunAfter(link)
                enabled = linksBinaries
            }
            link.configure { finalizedBy(assets) }
            allAssets.configure { dependsOn(assets) }

            if (isHost) {
                val debug = binary.buildType == NativeBuildType.DEBUG
                project.tasks.register(if (debug) "run" else "runRelease", Exec::class.java) {
                    group = "application"
                    description = "Runs the game (${binary.buildType.name.lowercase()} build). KLYPH_* variables come from the shell or -Pklyph.<name>."
                    dependsOn(link, assets)
                    executable(binary.outputFile)
                    environment(extension.runEnvironment())
                }
            }

            if (linksBinaries && binary.buildType == NativeBuildType.RELEASE) registerDistribution(binary, target, prefix, fetch, isHost)
        }
    }

    private fun registerDistribution(
        binary: Executable,
        target: KotlinNativeTarget,
        prefix: File,
        fetch: TaskProvider<Task>?,
        isHost: Boolean,
    ) {
        val targetSuffix = target.name.replaceFirstChar { it.uppercase() }
        val folder = DistributionLayout.folderName(project.name, project.version.toString(), target.name)
        val prepare = project.tasks.register("prepareDistribution$targetSuffix", PrepareDistribution::class.java) {
            description = "Lays out the $folder distribution folder."
            dependsOn(binary.linkTaskProvider)
            if (fetch != null) dependsOn(fetch)
            executable.fileProvider(binary.linkTaskProvider.flatMap { it.outputFile })
            assetsDirectory.set(extension.assetsDir)
            assetExcludes.set(extension.assetExcludes)
            this.target.set(target.name)
            sdl3Prefix.set(prefix)
            destination.set(project.layout.buildDirectory.dir("klyph/distribution/${target.name}"))
        }
        val zip = project.tasks.register("distribute$targetSuffix", Zip::class.java) {
            group = "distribution"
            description = "Zips the release executable, its assets and SDL3 for ${target.name} into build/distributions/$folder.zip."
            from(prepare.flatMap { it.destination }) { into(folder) }
            // Gradle 9 archives use fixed permissions by default, which drops the executable bit.
            useFileSystemPermissions()
            archiveFileName.set("$folder.zip")
            destinationDirectory.set(project.layout.buildDirectory.dir("distributions"))
        }
        if (isHost) {
            project.tasks.register("distribute") {
                group = "distribution"
                description = "Zips the release executable, its assets and SDL3 for the host (${target.name})."
                dependsOn(zip)
            }
        }
    }

    companion object {
        private const val FETCH_TASK = "fetchSdl3"
        private const val FETCH_PREFIX = "com.eventslooped.klyph.sdl3.fetchPrefix"

        /** Applies klyph to [project]: a [game] gets executables and their tasks, a library does not. */
        fun apply(project: Project, game: Boolean) {
            if (project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                val id = if (game) "com.eventslooped.klyph" else "com.eventslooped.klyph.library"
                throw GradleException(
                    "Apply $id before org.jetbrains.kotlin.multiplatform, or drop the Kotlin plugin: klyph applies it.",
                )
            }
            val extension = project.extensions.create("klyph", KlyphExtension::class.java)
            extension.assetsDir.convention(project.layout.projectDirectory.dir("assets"))
            extension.assetExcludes.convention(listOf("README.md"))
            extension.sdl3Version.convention(Sdl3Release.DEFAULT_VERSION)
            extension.targets.convention(listOf("host"))

            val copyAssets = if (!game) {
                null
            } else {
                project.tasks.register("copyAssets") {
                    group = "application"
                    description = "Copies the assets folder next to every executable of this project."
                }
            }

            // Targets and binaries depend on the klyph { } block, which the build script sets after
            // this plugin is applied, so they are created once the script has run. The callback is
            // registered before Kotlin is applied so it runs ahead of Kotlin's own afterEvaluate
            // work, while Kotlin still treats new targets as if the build script declared them.
            project.afterEvaluate {
                if (project.state.failure == null) KlyphBuild(project, extension, copyAssets).configure()
            }
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        }
    }
}
