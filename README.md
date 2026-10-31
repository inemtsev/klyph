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
- A benchmarks executable that runs named benchmarks, starting with `sprites`.

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
frame. Per-frame averages after a one-second warm-up.

| Sprites | Frame | CPU (update + extract) | GPU |
|---|---|---|---|
| 10,000 | 0.37 ms | 0.06 ms | 0.30 ms |
| 100,000 | 2.30 ms | 0.58 ms | 1.71 ms |
| 1,000,000 | 24.7 ms | 6.16 ms | 18.5 ms |

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

Build and run the sprite benchmark:

```
./gradlew :benchmarks:linkReleaseExecutableMacosArm64
KLYPH_OFFSCREEN=1 KLYPH_SPRITES=100000 KLYPH_FRAMES=600 benchmarks/build/bin/macosArm64/releaseExecutable/benchmarks.kexe sprites
```

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
| `klyph` | The engine: entities, components, rendering, shaders. |
| `samples` | The `@sample` functions referenced from KDoc, run as tests. |
| `sandbox` | Executable smoke test: opens a window and clears it each frame. |
| `benchmarks` | Executable that runs a benchmark by name, such as `sprites`. |

## Roadmap

In order:

1. Entity hierarchy, system phases and events.
2. Scene files with stable entity ids.
3. Linux CI.

## Contributing

Read [API_STYLE.md](API_STYLE.md) before adding public API and run
`./gradlew check` before opening a pull request.

## License

[Apache License 2.0](LICENSE).
