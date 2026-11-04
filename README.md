# klyph

A 2D game engine in pure Kotlin/Native on SDL3 and SDL_GPU.

klyph compiles to a native executable with no JVM. The public API follows the conventions of the
Kotlin standard library, so a Kotlin developer can guess a call before looking it up. Shaders are
written once in HLSL and compiled ahead of time to Metal, Vulkan and Direct3D 12. It runs on
macOS, Linux and Windows.

## Status

Pre-alpha. 0.1.0 is the first release, and the API changes without notice until 1.0. From 1.0
on, the public API keeps working across minor releases, except declarations marked
`@KlyphPlatformApi`: the SDL and GPU plumbing the engine runs on, which a game never needs and
can only call after opting in.

- **Framework**: `runApp` owns the loop and runs systems in phases with a fixed timestep, a typed
  event bus and an entity hierarchy whose transforms propagate each frame.
- **Entities**: sparse-set component storage and typed queries over two or three components.
- **Rendering**: sprites pulled from a storage buffer in one draw call, sprite sheets, animation,
  several textures and a camera.
- **Input**: keyboard, mouse and gamepad state.
- **Audio**: WAV and Ogg Vorbis effects with volume, pitch and pan, and streamed music with fades,
  mixed by klyph on SDL's audio thread. A game runs silently without an audio device.
- **Scenes**: a stack of scenes with their own systems; a scene's entities leave with it.
- **Collisions**: boxes and circles with layers and masks on a uniform grid, reported as
  `Overlap` events, plus shape queries.
- **Particles**: pooled emitters with bursts, rates, ranges, gravity, and color and scale over
  each particle's life.
- **Text**: BMFont fonts and a built-in 8x8 font, in the world or on screen.
- **Debug overlay** on F3: frame and phase times, counts, audio voices and a frame-time graph.
- **Gradle plugin** and artifacts on Maven Central.

## Use klyph

The engine is `com.eventslooped.klyph:klyph` on Maven Central and the Gradle plugin is
`com.eventslooped.klyph`. A game's whole build file:

```kotlin
plugins { id("com.eventslooped.klyph") version "1.0.0" }
klyph { entryPoint = "my.game.main" }
kotlin { sourceSets.nativeMain.dependencies { implementation("com.eventslooped.klyph:klyph:1.0.0") } }
```

and `settings.gradle.kts` names the repositories:

```kotlin
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
```

Sources go in `src/nativeMain/kotlin`, art and sounds in `assets/`. `./gradlew run` plays the game and
`./gradlew distribute` zips it with SDL3 for players. The plugin finds SDL3 on its own or
downloads it on macOS and Windows; see its [README](klyph-gradle-plugin/README.md).

## Links

- [Guide](https://inemtsev.github.io/klyph/guide/index.html): getting started, sprites, entities
  and scenes, input, audio, collisions, text, shipping and performance, live from 1.0. The pages
  are in [docs/](docs).
- [API reference](https://inemtsev.github.io/klyph/), live from 1.0.
- [klyph-template](https://github.com/inemtsev/klyph-template), a game to start from: a window, a
  ship that flies, and CI on three desktops.
- [games/shooter](games/shooter), the reference game: a top-down shooter with keyboard, mouse and
  gamepad controls, scenes, collisions, particles, sound and an autopilot.
- [games/squish](games/squish), a bug-squishing game for young children.
- [klyph-gradle-plugin/README.md](klyph-gradle-plugin/README.md), every plugin task and property.
- [CONTRIBUTING.md](CONTRIBUTING.md), building the repository, tests, samples and benchmarks.
- [CHANGELOG.md](CHANGELOG.md).

## Performance

Apple M4 Pro, Metal, release build, through the full `runApp` loop, rendering offscreen and
waiting on a fence so the display is out of the loop. Per-frame averages after a warm-up.

| Benchmark | Frame | CPU | GPU |
|---|---|---|---|
| 10,000 sprites | 0.41 ms | 0.08 ms | 0.31 ms |
| 100,000 sprites | 2.54 ms | 0.81 ms | 1.69 ms |
| 1,000,000 sprites | 26.5 ms | 8.79 ms | 17.6 ms |
| 10,000 colliders | 0.65 ms | 0.36 ms | 0.23 ms |
| 100,000 particles | 2.40 ms | 1.45 ms | 0.92 ms |

Each sprite is an alpha-blended textured quad, moved and rotated every frame; CPU is update,
transform propagation and extraction. The colliders are circles moving in a 4000 by 4000 area
with about 2,600 overlapping pairs a frame; CPU is detection alone. The particles are 100
emitters kept at 1000 each; CPU is advancing and extracting them. Method and caveats are in
[BENCHMARKS.md](BENCHMARKS.md).

## Design decisions

- **Kotlin/Native only.** One language from game code to the SDL calls, native startup and
  memory footprint, and no runtime to ship.
- **SDL3 and SDL_GPU.** SDL handles windows, input and audio on every desktop. SDL_GPU maps one
  explicit GPU API to Metal, Vulkan and D3D12. [prior-art.md](prior-art.md) has the rendering
  survey behind the choice.
- **Vertex pulling over instancing.** Sprites are 64-byte records in a storage buffer, and the
  vertex shader builds each quad from its vertex id: one buffer write and one draw a frame.
- **Entities, components, systems.** Entities are generational handles, components are plain
  data, systems are plain functions over queries, run in fixed phases in the order they were
  added. No archetypes, no dependency-based scheduler, no query compiler.
- **Stdlib-style API.** Every public declaration follows [API_STYLE.md](API_STYLE.md): members
  hold the core concept, extensions the rest, throwing calls have `OrNull` siblings, and
  non-trivial functions have samples that run as tests.

## Layout

| Module | Contents |
|---|---|
| `klyph-platform` | SDL3 bindings and the thin Kotlin API over them; the only module that touches C interop. |
| `klyph` | The engine: entities, scenes, rendering, text, particles, collisions, audio. |
| `samples` | The `@sample` functions referenced from KDoc, run as tests. |
| `klyph-gradle-plugin` | The Gradle plugin games apply. |
| `games/shooter`, `games/squish` | The sample games. |
| `sandbox` | Smoke test: opens a window and clears it each frame. |
| `benchmarks` | Named benchmarks, such as `sprites` and `mixer`. |

## License

[Apache License 2.0](LICENSE).
