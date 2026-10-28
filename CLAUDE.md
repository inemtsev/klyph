# klyph

A Kotlin library written in the style of the Kotlin standard library.

Every public declaration must follow the API style spec. Read it before adding
or changing public API, and run its review checklist before finishing a change.

@API_STYLE.md

## Build and run

Kotlin/Native only, no JVM target. The build configures one target per run: the host's own
(macosArm64, linuxX64, linuxArm64, mingwX64) or `-Pklyph.target=<name>`. SDL3 is located
automatically (Homebrew, /usr/local, /usr); override with `-Psdl3.prefix=<path>`.

Modules: `klyph-platform` (SDL3 cinterop and the thin Kotlin API over it; the only module allowed
to import `kotlinx.cinterop`, `sdl3`, `cnames` or `platform.*`), `klyph` (the engine, no interop),
`sandbox` (executable; a smoke test that opens a window and clears it each frame), `benchmarks`
(executable; named benchmarks, run from the release build), `samples` (the `@sample` functions from
API_STYLE.md section 8, run as tests; `@Sample` is a typealias of `kotlin.test.Test` and
`assertPrints` lives in `samples/src/nativeTest/kotlin/samples/Harness.kt`). `./gradlew check`
runs every module's tests and samples plus the interop-import rule; a single module's tests run
with `:<module>:macosArm64Test`. GPU, window and event functions have no samples because they need
a display. Shared target config and SDL link flags live in
`buildSrc/src/main/kotlin/klyph.native.gradle.kts`.

```
./gradlew :sandbox:linkDebugExecutableMacosArm64
KLYPH_FRAMES=120 sandbox/build/bin/macosArm64/debugExecutable/sandbox.kexe
```

The sandbox exits after `KLYPH_FRAMES` frames and honors `KLYPH_VALIDATION`. Benchmarks run from
the release build; the first argument names the benchmark, and the binary lists the available
names when run without one.

```
./gradlew :benchmarks:linkReleaseExecutableMacosArm64
KLYPH_OFFSCREEN=1 KLYPH_SPRITES=100000 KLYPH_FRAMES=600 benchmarks/build/bin/macosArm64/releaseExecutable/benchmarks.kexe sprites
```

`KLYPH_SPRITES=<n>` sprite count (default 10000), `KLYPH_FRAMES=<n>` exits after `n` frames,
`KLYPH_VSYNC=1` caps at the display rate, `KLYPH_VALIDATION=1` turns on the GPU validation layer
(slow; use it after touching the platform module), `KLYPH_OFFSCREEN=1` draws into a texture and
waits for the GPU each frame so the display is out of the loop. macOS has no `timeout`, so bound
runs with `KLYPH_FRAMES`. Do not pipe `./gradlew` output into
another command: the daemon inherits the pipe and the shell never sees EOF. Redirect to a file.

## Shaders

Shaders are written once in HLSL under `klyph/src/shaders/` as `<name>.vert.hlsl` or
`<name>.frag.hlsl`. `./gradlew :klyph:compileShaders` runs SDL_shadercross and regenerates
`klyph/src/nativeMain/kotlin/klyph/render/generated/Shaders.kt`, which embeds MSL, SPIR-V and
DXIL plus resource counts as `CompiledShader` values. That file is checked in; commit it together
with the HLSL change. The task runs before every Kotlin compile and is skipped with a warning when
the tool is missing (default path `~/.local/shadercross/bin/shadercross`, override with
`-Pklyph.shadercross=<path>`). Binding conventions for HLSL: vertex storage buffers `space0`,
vertex uniforms `space1`, fragment samplers `space2`. The MSL entry point is `main0`;
`GpuDevice.createShader(CompiledShader)` handles that.
