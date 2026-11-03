package com.eventslooped.klyph.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Configures a klyph game build, registered as `klyph { }` by [KlyphPlugin].
 *
 * Only [entryPoint] is required. The plugin reads every property after the build script has run,
 * so assignments may appear anywhere in the script.
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
