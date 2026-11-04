# Contributing to klyph

## Before you change public API

Read [API_STYLE.md](API_STYLE.md). It sets the naming vocabulary, the failure contract and the
KDoc and sample rules every public declaration follows. Copy its review checklist (section 9)
into the pull request description and tick each item.

Every public declaration gets KDoc, and every public function with non-trivial behavior gets a
`@sample` that points at a function in `samples/src/nativeTest/kotlin`. Samples compile and run
as tests, so a sample that stops matching the code fails the build.

Add a line under `Unreleased` in [CHANGELOG.md](CHANGELOG.md) for every change a user of the
engine or the plugin would notice.

## The guide

The guide pages are Markdown files in [docs/](docs), rendered into the Dokka site's `guide/`
folder by `./gradlew dokkaGenerate`. A page's first line names the Kotlin file its snippets come
from, under `samples/src/nativeTest/kotlin/samples/guide/`, and each code block follows a
`<!-- snippet: <name> -->` marker that matches a `// snippet: <name>` ... `// end snippet` region
there. The samples compile with `check`, and `:samples:checkGuideSnippets`, part of `check`,
fails when a page's code differs from its region. Change the Kotlin file first, then copy the
region into the page. `dokkaGenerate` fails on a broken relative link in the guide or in
`docs/index.md`.

## Stability

From 1.0, every public declaration in `klyph` and `klyph-platform` keeps its name, signature and
behavior across minor releases, except those marked `@KlyphPlatformApi`. That marker sits on the
platform plumbing only the engine calls: the GPU wrappers, the event pump, the audio device and
its renderer, `Sdl.init` and `Sdl.quit`, the `Window` factory, compiled shaders and frame capture.
Those may change in any release. Using one is a compile error until the caller opts in with
`@OptIn(KlyphPlatformApi::class)` or `-opt-in=klyph.platform.KlyphPlatformApi`; `klyph`,
`klyph-platform`, `sandbox` and `benchmarks` opt in for the whole module in their build files,
and the games never do. A declaration a game needs must not carry the marker. If a game in
`games/` stops compiling because of it, remove the marker from that declaration instead of
opting the game in.

## Layout

| Module | Contents |
|---|---|
| `klyph-platform` | SDL3 bindings and the thin Kotlin API over them; the only module that touches C interop. |
| `klyph` | The engine: entities, scenes, rendering, text, particles, collisions, audio. |
| `samples` | The `@sample` functions referenced from KDoc, run as tests. |
| `klyph-gradle-plugin` | The Gradle plugin games apply, a separate Gradle build included through `pluginManagement`. |
| `games/shooter`, `games/squish`, `games/roller` | The sample games. |
| `sandbox` | Smoke test: opens a window and clears it each frame. |
| `benchmarks` | Named benchmarks, such as `sprites` and `mixer`. |
| `docs` | The guide pages, rendered into the Dokka site. |
| `tools` | The consumer test, the release secrets script and the template that becomes `klyph-template`. |

## Requirements

- JDK 17 or newer for Gradle.
- SDL3 3.4 or newer. On macOS, `brew install sdl3`. The build finds Homebrew, `/usr/local` and
  `/usr` on its own; pass `-Psdl3.prefix=<path>` for anything else. On Windows, use SDL's
  `SDL3-devel-<version>-mingw.zip` and pass its `x86_64-w64-mingw32` folder with forward slashes,
  such as `-Psdl3.prefix=C:/SDL3-3.4.18/x86_64-w64-mingw32`.
- [SDL_shadercross](https://github.com/libsdl-org/SDL_shadercross), only to change a shader.
  Without it the build uses the checked-in compiled shaders and prints a warning. Commit the
  regenerated `klyph/src/nativeMain/kotlin/klyph/render/generated/Shaders.kt` with the HLSL.

## Build and test

```
./gradlew check                        # every module's tests and samples, and the interop rule
./gradlew :klyph:macosArm64Test        # one module's tests on the host target
./gradlew -p klyph-gradle-plugin check # the Gradle plugin's unit and TestKit tests
./gradlew dokkaGenerate                # the API reference, in build/dokka/html
```

Run `./gradlew check` and the plugin's `check` before opening a pull request. Engine tests live in
`klyph/src/nativeTest/kotlin` and `klyph-platform/src/nativeTest/kotlin`, samples in
`samples/src/nativeTest/kotlin`, and the plugin's tests in `klyph-gradle-plugin/src/test` and
`klyph-gradle-plugin/src/functionalTest`. Only `klyph-platform` may import `kotlinx.cinterop`,
`sdl3`, `stb`, `vorbis`, `cnames` or `platform.*`; `check` fails on such an import anywhere else.

The build configures the host's own target: `macosArm64`, `linuxX64`, `linuxArm64` or
`mingwX64`. `-Pklyph.targets=all` configures all four, which a macOS host can compile; tests and
`run` stay on the host target. `KLYPH_VALIDATION=1` turns on the GPU validation layer, which is
slow but worth a run after touching `klyph-platform`.

## Games and benchmarks

The games live in `games/` and build with the klyph Gradle plugin, the way a game outside the
repository does. IntelliJ picks up the same tasks as run configurations from `.run/`.

```
./gradlew :games:shooter:run                                         # WASD/arrows/stick, mouse aim, Space/click/south
./gradlew :games:shooter:run -Pklyph.autopilot=1 -Pklyph.seconds=10  # plays itself, fails if no kill
./gradlew :games:squish:run                                          # click, or arrows/stick and Space/south
./gradlew :sandbox:run -Pklyph.frames=120
```

Benchmarks run the release build. The offscreen variants draw into a texture and wait for the
GPU each frame, so the display is out of the loop; use them for numbers.

```
./gradlew :benchmarks:benchmarkSpritesOffscreen -Pklyph.sprites=100000
./gradlew :benchmarks:benchmarkCollisionsOffscreen -Pklyph.sprites=10000
./gradlew :benchmarks:benchmarkMixer -Pklyph.music=games/shooter/assets/music.ogg
./gradlew :benchmarks:benchmarkList
```

[BENCHMARKS.md](BENCHMARKS.md) has the method and the current results.

## Testing a release locally

`tools/consumer-test.sh local` builds the shooter in a fresh folder from the artifacts in `~/.m2`,
the way a game outside the repository would, then runs it and checks its distribution zip.
Publish first:

```
./gradlew publishToMavenLocal -Pklyph.targets=all
./gradlew :klyph-gradle-plugin:publishToMavenLocal
tools/consumer-test.sh local
```

Releases are published by `.github/workflows/release.yml` when a `v*` tag is pushed. A repository
ruleset lets only administrators create, move or delete `v*` tags, so a contributor's push cannot
start a publish; the secrets the workflow needs are never available to pull requests.
`tools/set-release-secrets.sh` copies the maintainer's Central and signing credentials from
`~/.gradle/gradle.properties` into those repository secrets.
