# klyph

A 2D game engine in pure Kotlin/Native on SDL3 and SDL_GPU.

klyph compiles to a native executable with no JVM. The public API follows the
conventions of the Kotlin standard library, so a Kotlin developer can guess a
call before looking it up. Shaders are written once in HLSL and compiled ahead
of time to Metal, Vulkan and Direct3D 12.

## Status

Pre-alpha. 0.1.0 is the first release, and the API changes without notice until 1.0.

What works today:

- A framework core: `runApp` owns the loop and runs systems in phases (startup,
  input, fixed update, update, late update, render, shutdown), with a fixed
  timestep, a typed event bus fed from the platform, and an entity hierarchy
  whose world transforms are propagated each frame.
- An entity world with sparse-set component storage and typed queries over two
  or three components.
- A sprite renderer that pulls per-sprite records from a storage buffer in one
  draw call, with cycling transfer buffers for upload.
- An HLSL shader pipeline through SDL_shadercross, with the compiled shaders
  checked in as Kotlin.
- Audio: sound effects from WAV or Ogg Vorbis with volume, pitch and pan, and
  streamed Ogg Vorbis music with fades, mixed by klyph on SDL's audio thread.
  The main thread never waits on the mixer, and a game runs silently when
  there is no audio device.
- Scenes: a stack of scenes, each with its own systems and enter and exit
  actions. Only the top scene's systems run, and entities a scene spawns are
  destroyed when it leaves the stack.
- Collisions: box and circle colliders with layers and masks, checked on a
  uniform grid once per frame after the fixed steps. Overlapping pairs arrive as
  `Overlap` events, and `app.collisions` answers shape queries.
- Particles: an emitter component with bursts, a continuous rate, ranges for
  speed, lifetime and rotation, gravity, and color and scale blended over each
  particle's life. Particles live in a pool inside the emitter, not as entities.
- Text: BMFont fonts with kerning and a built-in 8x8 font that needs no files,
  drawn through a `Text` component in the world or through `app.canvas` in world
  or screen space, with alignment, color and scale. Each texture is sampled
  linear or nearest, so pixel art and the built-in font stay crisp.
- A debug overlay on F3 with frame rate, frame and phase times, entity, sprite
  and draw call counts, audio voices and a frame-time graph.
- A benchmarks executable with named benchmarks: `sprites` (a flat world),
  `hierarchy` (a three-level tree of orbiting sprites), `collisions` (moving
  circle colliders), `particles` (emitters of 1000 particles each, kept full)
  and `mixer` (32 voices and music, mixed headless).
- A top-down shooter sample: a ship flies and fires over a starfield in an
  arena larger than the window, enemies drift down and explode with a
  sprite-sheet animation and a particle burst, and the camera follows the ship.
  It has a title screen, a pause screen and a game-over screen, shows the score
  and lives on screen, and finds hits through the engine's collisions. Shots,
  explosions and hits have sounds and a music loop plays. It plays with the
  keyboard and mouse aim or with a gamepad, pauses when the window loses focus,
  loads its art and sounds from an `assets/` folder next to the binary and can play itself
  for demos.
- A bug-squishing sample for young children: bugs crawl over a picnic blanket
  toward the food and the player squishes them with a fly swatter moved by the
  mouse, the keyboard or a gamepad, in one-minute rounds with a title, pause and
  end screen.

## Design decisions

- **Kotlin/Native only.** One language from game code to the SDL calls, native
  startup and memory footprint, and no runtime to ship.
- **SDL3 and SDL_GPU.** SDL handles windows, input and audio on every desktop
  platform. SDL_GPU is an explicit GPU API that maps to Metal, Vulkan and D3D12
  without a renderer per backend. See [prior-art.md](prior-art.md) for the
  rendering sweep behind this choice.
- **Vertex pulling over instancing.** Sprites are 64-byte records in a storage
  buffer and the vertex shader builds the quad from its vertex id. Each frame
  does one buffer write and one draw.
- **Entities, components, systems.** Entities are generational handles,
  components are plain data, and systems are plain functions over queries.
  Systems run in fixed phases, in the order they were added. There are no
  archetypes, no dependency-based scheduler and no query compiler.
- **Stdlib-style API.** Every public declaration follows
  [API_STYLE.md](API_STYLE.md): members hold only the core concept, extensions
  hold the rest, throwing calls have `OrNull` siblings, and every non-trivial
  function has a sample that runs as a test.

## Performance

Benchmarks on an Apple M4 Pro, Metal, release build, rendering offscreen and
waiting on a fence so the display is out of the loop, through the full
`runApp` loop. In the sprite rows each sprite is an alpha-blended textured quad
with its own transform, moved and rotated every frame, and the CPU column is
update, transform propagation and extraction. The collision row moves 10,000
circles of radius 4 to 12 in a 4000 by 4000 area, about 2,600 overlapping pairs
a frame, and its CPU column is detection alone. The particle row runs 100
emitters kept full at 1000 particles each, and its CPU column is advancing and
extracting them. Per-frame averages after a warm-up.

| Benchmark | Frame | CPU | GPU |
|---|---|---|---|
| 10,000 sprites | 0.41 ms | 0.08 ms | 0.31 ms |
| 100,000 sprites | 2.54 ms | 0.81 ms | 1.69 ms |
| 1,000,000 sprites | 26.5 ms | 8.79 ms | 17.6 ms |
| 10,000 colliders | 0.65 ms | 0.36 ms | 0.23 ms |
| 100,000 particles | 2.40 ms | 1.45 ms | 0.92 ms |

Full table, method and caveats in [BENCHMARKS.md](BENCHMARKS.md).

## Use klyph

klyph is on Maven Central from 0.1.0: the engine as `com.eventslooped.klyph:klyph` and the
Gradle plugin as `com.eventslooped.klyph`. A game's whole build file is three lines:

```kotlin
plugins { id("com.eventslooped.klyph") version "0.1.0" }
klyph { entryPoint = "my.game.main" }
kotlin { sourceSets.nativeMain.dependencies { implementation("com.eventslooped.klyph:klyph:0.1.0") } }
```

and `settings.gradle.kts` names the repositories:

```kotlin
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
```

The plugin applies Kotlin Multiplatform 2.4.20 itself, so the build does not declare Kotlin.
Sources go in `src/nativeMain/kotlin` and art and sounds in `assets/`. The `klyph { }` block
takes more settings when the defaults do not fit:

```kotlin
klyph {
    entryPoint = "my.game.main"                         // required
    assetsDir = layout.projectDirectory.dir("assets")   // the default
    sdl3Prefix = "/opt/sdl3"                            // an SDL3 install the lookup does not find
    targets = listOf("host")                            // or listOf("all"), or target names
}
```

`./gradlew run` builds the debug executable, copies `assets/` next to it and starts the game;
`-Pklyph.frames=600` and the other `-Pklyph.*` properties reach it as `KLYPH_*` variables.
`./gradlew distribute` zips the release executable, `assets/` and the SDL3 library into
`build/distributions/`; the unzipped folder runs on a machine without SDL3 installed.

The plugin finds SDL3 3.4 or newer on its own, or `-Psdl3.prefix=<path>` points at one:

- **macOS**: `brew install sdl3`. Without Homebrew's SDL3 the plugin downloads SDL's official
  release once, checks its SHA-256 and caches it under `~/.gradle/caches/klyph/sdl3/`.
- **Windows**: nothing to install. The plugin downloads SDL's MinGW package the same way, and
  every link copies `SDL3.dll` next to the executable.
- **Linux**: install the development package, `libsdl3-dev` on Debian 13 and Ubuntu 25.04 or
  newer, `sdl3` on Arch, `SDL3-devel` on Fedora. Older distributions have no package; build SDL
  from source into `/usr/local`. SDL publishes no Linux binary, so there is no download.

The plugin's own reference, with every task and property, is
[klyph-gradle-plugin/README.md](klyph-gradle-plugin/README.md).

## Building

Requirements:

- JDK 17 or newer for Gradle.
- SDL3 3.4 or newer. On macOS, `brew install sdl3`. The build finds Homebrew,
  `/usr/local` and `/usr` on its own; pass `-Psdl3.prefix=<path>` for anything
  else.
- [SDL_shadercross](https://github.com/libsdl-org/SDL_shadercross) only if you
  change a shader. Without it the build uses the checked-in compiled shaders and
  prints a warning.

Run the sprite benchmark in a window, or offscreen for clean numbers:

```
./gradlew :benchmarks:benchmarkSprites -Pklyph.sprites=50000
./gradlew :benchmarks:benchmarkSpritesOffscreen -Pklyph.sprites=100000
```

Play the shooter sample (WASD, arrow keys or the left stick to fly, the mouse or
the right stick to aim, Space, left click, the south button or the right trigger
to fire, Escape or Start to pause), or watch it play itself:

```
./gradlew :shooter:run
./gradlew :shooter:run -Pklyph.autopilot=1
```

IntelliJ picks up the same tasks as run configurations from `.run/`.

Run the tests and samples:

```
./gradlew check
```

The build configures the host's own target: `macosArm64`, `linuxX64`,
`linuxArm64` or `mingwX64`. `-Pklyph.targets=all` configures all four, which a
macOS host can compile; tests and `run` stay on the host target.

The repository's own games build with the plugin too, through an included build,
and `tools/consumer-test.sh local` builds the shooter in a fresh folder from the
artifacts in `~/.m2` the way a game outside the repository would.

## Layout

| Module | Contents |
|---|---|
| `klyph-platform` | SDL3 bindings and the thin Kotlin API over them. The only module that touches C interop. |
| `klyph` | The engine: entities, components, scenes, rendering, text, particles, collisions, shaders, audio. |
| `samples` | The `@sample` functions referenced from KDoc, run as tests. |
| `klyph-gradle-plugin` | The Gradle plugin games apply: targets, SDL3 lookup and download, `run`, `distribute`. |
| `sandbox` | Executable smoke test: opens a window and clears it each frame. |
| `shooter` | The reference game: a top-down shooter that exercises the engine end to end. |
| `squish` | A bug-squishing game for young children. |
| `benchmarks` | Executable that runs a benchmark by name, such as `sprites`. |

## Roadmap

Next: 1.0.

Done: entity hierarchy, system phases and events; image loading, sprite sheets,
animation, multiple textures and a camera; keyboard, mouse and gamepad state;
sound effects and music; macOS, Linux and Windows CI; scenes, collision tests,
particles, bitmap text and a debug overlay; a Gradle plugin and artifacts on
Maven Central.

## Contributing

Read [API_STYLE.md](API_STYLE.md) before adding public API and run
`./gradlew check` before opening a pull request.

## License

[Apache License 2.0](LICENSE).
