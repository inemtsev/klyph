package com.eventslooped.klyph.gradle

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.io.File
import javax.inject.Inject

/**
 * Configures a klyph game build, registered as `klyph { }` by [KlyphPlugin] and by
 * [KlyphLibraryPlugin].
 *
 * Only [entryPoint] is required, and only for a game. The plugin reads every property after the
 * build script has run, so assignments may appear anywhere in the script. [hostTarget],
 * [supportedTargets], [configuredTargets] and [sdl3IncludeDir] are read-only results for build
 * logic of your own.
 */
public abstract class KlyphExtension @Inject constructor(
    private val providers: ProviderFactory,
    private val layout: ProjectLayout,
) {
    /**
     * Fully qualified name of the game's `main` function, such as `"my.game.main"`.
     *
     * The build fails at configuration time when it is not set.
     */
    public abstract val entryPoint: Property<String>

    /**
     * Folder copied to `assets/` next to every executable after each link and into every
     * distribution. Defaults to `assets` in the project directory; a missing folder copies nothing.
     */
    public abstract val assetsDir: DirectoryProperty

    /**
     * Ant-style patterns, relative to [assetsDir], of files left out of every copy of the assets
     * folder, such as `"*.psd"`. Defaults to `listOf("README.md")`, so a note about the art
     * stays out of the game folder; set it to an empty list to copy everything.
     */
    public abstract val assetExcludes: ListProperty<String>

    /**
     * Folder that contains `include/SDL3/SDL.h` and the SDL3 library in `lib/` (on Linux also
     * `lib64/` or `lib/<architecture>-linux-gnu/`), plus `bin/SDL3.dll` on Windows.
     *
     * The Gradle property `sdl3.prefix` overrides it. When neither is set, or neither folder holds
     * the header, the plugin looks in the usual system locations and then downloads SDL's own
     * release on macOS and Windows.
     */
    public abstract val sdl3Prefix: Property<String>

    /**
     * SDL3 release downloaded when no installation is found. Defaults to the version the plugin
     * pins a SHA-256 for; other versions need the Gradle property `klyph.sdl3.sha256`.
     */
    public abstract val sdl3Version: Property<String>

    /**
     * Kotlin/Native targets to configure: `"host"` (the default), `"all"`, or target names
     * (`macosArm64`, `linuxX64`, `linuxArm64`, `mingwX64`).
     *
     * The Gradle property `klyph.targets` (a comma-separated list) or `klyph.target` (one name)
     * overrides it.
     */
    public abstract val targets: ListProperty<String>

    /**
     * Target of the machine running the build: `macosArm64`, `linuxX64`, `linuxArm64` or
     * `mingwX64`. Only its binaries run here, so it is the target for tasks of your own that run
     * an executable. Reading it on any other machine fails the build.
     */
    public val hostTarget: Provider<String> = providers.systemProperty("os.name")
        .zip(providers.systemProperty("os.arch")) { name, arch -> KlyphTargets.host(name, arch) }

    /** Every target klyph builds for, in the order a build configures them. */
    public val supportedTargets: List<String> = KlyphTargets.all

    /**
     * Targets this project configures: [targets], or the Gradle property `klyph.targets` or
     * `klyph.target` that overrides it, in the order of [supportedTargets].
     *
     * It follows [targets], so read it after the build script has run, such as in a task action.
     * Reading it fails the build when both properties are set, a name is unknown or none is given.
     */
    public val configuredTargets: Provider<List<String>> = providers.provider {
        KlyphTargets.resolve(
            targetsProperty = providers.gradleProperty("klyph.targets").orNull,
            targetProperty = providers.gradleProperty("klyph.target").orNull,
            extension = targets.get(),
            host = hostTarget.get(),
        )
    }

    /**
     * The `include` folder of the SDL3 this project compiles and links against, for a cinterop
     * of your own: `includeDirs(klyph.sdl3IncludeDir)`.
     *
     * It has no value until the plugin has located SDL3, right after the build script has run.
     * When SDL3 is downloaded, the folder fills when `fetchSdl3` runs, which every cinterop task
     * of the project runs after.
     */
    public val sdl3IncludeDir: Provider<Directory> = layout.dir(providers.provider { locatedSdl3?.resolve("include") })

    /** The SDL3 prefix the plugin settled on, set once it has located SDL3. */
    internal var locatedSdl3: File? = null

    /**
     * Returns the `KLYPH_*` environment for a run task: each variable comes from the Gradle
     * property `klyph.<name>` or, failing that, from the shell, on top of [defaults].
     *
     * `KLYPH_MUSIC` is a path and resolves against the root project directory. The `run` and
     * `runRelease` tasks already use this; call it for extra `Exec` tasks of your own.
     */
    public fun runEnvironment(defaults: Map<String, String> = emptyMap()): Map<String, String> =
        RunEnvironment.of(
            property = { providers.gradleProperty(it).orNull },
            environment = { providers.environmentVariable(it).orNull },
            rootDirectory = layout.settingsDirectory.asFile,
            defaults = defaults,
        )
}
