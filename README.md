# klyph

A 2D game engine in pure Kotlin/Native on SDL3 and SDL_GPU.

klyph compiles to a native executable with no JVM. The public API follows the
conventions of the Kotlin standard library, so a Kotlin developer can guess a
call before looking it up. Shaders are written once in HLSL and compiled ahead
of time to Metal, Vulkan and Direct3D 12.

## Status

Pre-alpha. There are no releases and the API changes without notice.

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
- A benchmarks executable with named benchmarks: `sprites` (a flat world),
  `hierarchy` (a three-level tree of orbiting sprites) and `mixer` (32 voices
  and music, mixed headless).
- A top-down shooter sample: a ship flies and fires over a starfield in an
  arena larger than the window, enemies drift down and explode with a
  sprite-sheet animation, and the camera follows the ship. Shots, explosions
  and hits have sounds and a music loop plays. It plays with the
  keyboard and mouse aim or with a gamepad, pauses when the window loses focus,
  loads its art and sounds from an `assets/` folder next to the binary and can play itself
  for demos.

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

Sprite benchmark on an Apple M4 Pro, Metal, release build, rendering offscreen
and waiting on a fence so the display is out of the loop. Each sprite is an
alpha-blended textured quad with its own transform, moved and rotated every
frame, through the full `runApp` loop with transform propagation. Per-frame
averages after a one-second warm-up.

| Sprites | Frame | CPU (update + propagate + extract) | GPU |
|---|---|---|---|
| 10,000 | 0.41 ms | 0.08 ms | 0.31 ms |
| 100,000 | 2.54 ms | 0.81 ms | 1.69 ms |
| 1,000,000 | 26.5 ms | 8.79 ms | 17.6 ms |

Full table, method and caveats in [BENCHMARKS.md](BENCHMARKS.md).

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
to fire, Escape to quit), or watch it play itself:

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
`linuxArm64` or `mingwX64`. Linux and Windows builds are set up but not yet
tested; `-Pklyph.target=<name>` picks a target explicitly.

## Layout

| Module | Contents |
|---|---|
| `klyph-platform` | SDL3 bindings and the thin Kotlin API over them. The only module that touches C interop. |
| `klyph` | The engine: entities, components, rendering, shaders, audio. |
| `samples` | The `@sample` functions referenced from KDoc, run as tests. |
| `sandbox` | Executable smoke test: opens a window and clears it each frame. |
| `shooter` | The reference game: a top-down shooter that exercises the engine end to end. |
| `benchmarks` | Executable that runs a benchmark by name, such as `sprites`. |

## Roadmap

In order:

1. Collision tests, particles, bitmap text and a debug overlay.
2. A Gradle plugin and published artifacts.

Done: entity hierarchy, system phases and events; image loading, sprite sheets,
animation, multiple textures and a camera; keyboard, mouse and gamepad state;
sound effects and music; macOS, Linux and Windows CI.

## Contributing

Read [API_STYLE.md](API_STYLE.md) before adding public API and run
`./gradlew check` before opening a pull request.

## License

[Apache License 2.0](LICENSE).
