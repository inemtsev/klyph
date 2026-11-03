# klyph Gradle plugin

`com.eventslooped.klyph` turns a Gradle project into a klyph game. It applies Kotlin
Multiplatform 2.4.20, so the game does not declare Kotlin itself, configures the Kotlin/Native
target for your machine with one executable, finds or downloads SDL3, copies your assets next to
every binary, and adds tasks to run the game and zip it for players.

## Build file

```kotlin
plugins { id("com.eventslooped.klyph") version "0.1.0" }
klyph { entryPoint = "my.game.main" }
kotlin { sourceSets.nativeMain.dependencies { implementation("com.eventslooped.klyph:klyph:0.1.0") } }
```

`settings.gradle.kts` resolves both from Maven Central:

```kotlin
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
```

Sources go in `src/nativeMain/kotlin`, assets in `assets/`. Apply this plugin instead of
`org.jetbrains.kotlin.multiplatform`: the build fails if Kotlin is applied first. The shorter
`kotlin { dependencies { } }` block also works, but Kotlin 2.4.20 marks it experimental and
warns on every build script compile.

## `klyph { }`

```kotlin
klyph {
    entryPoint = "my.game.main"                         // required
    assetsDir = layout.projectDirectory.dir("assets")   // default
    assetExcludes = listOf("README.md")                 // default
    sdl3Prefix = "/opt/sdl3"                            // default: unset, see below
    sdl3Version = "3.4.18"                              // only used for a download
    targets = listOf("host")                            // or listOf("all"), or target names
}
```

| Property | Meaning |
|---|---|
| `entryPoint` | Fully qualified name of `main`. The build stops with `Set klyph { entryPoint = "my.game.main" }.` when it is missing. |
| `assetsDir` | Folder copied to `assets/` next to each executable and into each distribution. A missing folder copies nothing. |
| `assetExcludes` | Ant-style patterns, relative to `assetsDir`, of files left out of both copies, such as `"*.psd"` or `"drafts/**"`. Defaults to `listOf("README.md")`; an empty list copies everything. |
| `sdl3Prefix` | Folder holding `include/SDL3/SDL.h` and the SDL3 library (plus `bin/SDL3.dll` on Windows). See [Where SDL3 comes from](#where-sdl3-comes-from) for the library folders it tries. |
| `sdl3Version` | SDL release to download when none is installed. The plugin pins the SHA-256 of 3.4.18; any other version needs `-Pklyph.sdl3.sha256=<hash>`. |
| `targets` | `host` (default), `all`, or any of `macosArm64`, `linuxX64`, `linuxArm64`, `mingwX64`. See [Targets](#targets). |

`klyph.runEnvironment(defaults)` returns the `KLYPH_*` environment the run tasks use, for `Exec`
tasks of your own.

## Tasks

| Task | What it does |
|---|---|
| `run` | Links the debug executable for the host and runs it. |
| `runRelease` | The same with the release executable. |
| `copyAssets` | Copies `assetsDir`, less `assetExcludes`, next to every executable. Each link also does this for its own binary. |
| `distribute` | Zips the host's release executable, `assets/` and the SDL3 library into `build/distributions/<project>-<version>-<target>.zip`, or `<project>-<target>.zip` when the project sets no `version`. |
| `distribute<Target>` | The same for one target whose executables link (see [Targets](#targets)), such as `distributeMingwX64` in a cross build. |
| `fetchSdl3` | Downloads SDL3. Registered only when no installed SDL3 is found, once per build on the root project however many projects apply the plugin; every link and distribution depends on it. |

The zip holds one folder named after the zip, with the executable, `assets/` and
`libSDL3.0.dylib` or `SDL3.framework` on macOS, `libSDL3.so.0` on Linux, `SDL3.dll` on Windows.
Release executables look for SDL3 in their own folder first (`@executable_path` on macOS,
`$ORIGIN` on Linux). On macOS the copied executable is rewritten to load SDL3 through `@rpath`
and signed again ad hoc. The plugin does no signing for distribution and builds no `.app` bundle.

## Gradle properties

| Property | Effect |
|---|---|
| `-Psdl3.prefix=<path>` | SDL3 folder; tried before `klyph { sdl3Prefix }`. |
| `-Pklyph.targets=all` | Overrides `targets`: `host`, `all` or a comma-separated list. `-Pklyph.target=<name>` picks one; setting both fails. |
| `-Pklyph.<name>=<value>` | Passed to `run` and `runRelease` as `KLYPH_<NAME>`, for `sprites`, `children`, `textures`, `interpolate`, `frames`, `seconds`, `offscreen`, `vsync`, `validation`, `autopilot`, `music`, `cell` and `overlay`. A property wins over the shell variable; `klyph.music` resolves against the root project directory. |
| `-Pklyph.sdl3.sha256=<hash>` | Expected SHA-256 when downloading an SDL version the plugin does not pin. |
| `-Pklyph.sdl3.ignoreSystem=true` | For testing the download: skips the system locations below. |

## Targets

`targets`, `-Pklyph.targets` and `-Pklyph.target` take `host`, which names the machine's own
target, `all`, or a list of target names. `all` counts only as the whole value; inside a list it
is an unknown name. An unknown name, or both properties at once, stops the build. Whatever the
order given, targets are configured in the order `macosArm64`, `linuxX64`, `linuxArm64`,
`mingwX64`, each once.

Every configured target compiles, but binaries link against the one SDL3 found for the host. So
when the host's own target is among the configured ones, only its executables and test binaries
link: the other targets' link tasks are skipped (they stay in `assemble` and `build`, which still
pass) and they get no `distribute<Target>`. A build whose targets leave the host out is a cross
build and links every target, for example a Windows game from macOS or Linux:

```
./gradlew distributeMingwX64 -Pklyph.target=mingwX64 -Psdl3.prefix=<SDL3-devel-mingw folder>/x86_64-w64-mingw32
```

`run` and `runRelease` exist only for the host.

## Where SDL3 comes from

The plugin tries, in order, `-Psdl3.prefix`, `klyph { sdl3Prefix }`, then `/opt/homebrew/opt/sdl3`,
`/opt/homebrew`, `~/.homebrew/opt/sdl3`, `~/.homebrew`, `/usr/local/opt/sdl3`, `/usr/local` and
`/usr`. The first folder containing `include/SDL3/SDL.h` wins. A blank `-Psdl3.prefix=` counts as
unset; a set one without the header logs a warning naming where it came from, and the search goes
on.

Inside the prefix, the link's `-L` and runtime path and the distribution take the library from
the first of these folders that holds it, or `lib/` when none does:

- **Linux**: `lib/`, `lib64/` (Fedora), `lib/x86_64-linux-gnu/`, `lib/aarch64-linux-gnu/` (Debian
  and Ubuntu), each for `libSDL3.so.0` or `libSDL3.so`.
- **macOS**: `lib/`, for `libSDL3.0.dylib`, `libSDL3.dylib` or `SDL3.framework`.
- **Windows**: `lib/`, for `libSDL3.dll.a`. `SDL3.dll` always comes from `bin/`.

When no folder holds the header:

- **macOS** downloads SDL's `SDL3-<version>.dmg`, checks its SHA-256 and builds a prefix from
  its `SDL3.xcframework`: `lib/SDL3.framework`, `include/SDL3` and `lib/libSDL3.dylib`. `brew
  install sdl3` avoids the download.
- **Windows** downloads SDL's `SDL3-devel-<version>-mingw.zip`, checks its SHA-256 and uses its
  `x86_64-w64-mingw32` folder. Every link copies `SDL3.dll` next to the executable.
- **Linux** has no official SDL binary, so the build stops and names the package to install:
  `libsdl3-dev` on Debian 13 and Ubuntu 25.04 or newer, `sdl3` on Arch, `SDL3-devel` on Fedora.
  On older distributions, build SDL from source as described in
  [README-linux](https://github.com/libsdl-org/SDL/blob/main/docs/README-linux.md) and install it
  into `/usr/local` or pass its folder with `-Psdl3.prefix`.

One `fetchSdl3` on the root project serves every project of the build. Downloads are cached for
every build on the machine in
`<Gradle user home>/caches/klyph/sdl3/<version>/` (normally `~/.gradle/caches/klyph/sdl3/`): the
release file itself and the unpacked prefix in `<version>/<target>/`. A file whose SHA-256 does
not match is deleted and fails the build. Delete the folder to download again.

## Building the plugin

```
./gradlew -p klyph-gradle-plugin check                  # from the repository root: unit and TestKit tests
./gradlew -p klyph-gradle-plugin publishToMavenLocal
./gradlew -p klyph-gradle-plugin publishToMavenLocal -Pklyph.version=0.2.0-SNAPSHOT
```

The plugin's version is `0.1.0` unless `-Pklyph.version` overrides it, like the engine's. The
root `publishToMavenLocal` does not publish the plugin, which is a separate included build; run
it with `-p klyph-gradle-plugin` as above, or as `:klyph-gradle-plugin:publishToMavenLocal` from
the root. Command-line `-P` properties reach the included build, so `-Pklyph.version` sets the
same version for the engine and the plugin either way.

Publishing to Maven Central goes through `com.vanniktech.maven.publish`
(`publishToMavenCentral`) with the credentials and signing key in `~/.gradle/gradle.properties`.
