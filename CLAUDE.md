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
to import `kotlinx.cinterop`, `sdl3`, `cnames` or `platform.*`), `klyph` (the engine, no interop;
its `klyph.app` package owns the frame loop, which games and executables enter through `runApp`;
systems get `app.assets`, which loads from `assets/` next to the executable unless
`AppConfig.assetRoot` overrides it, `app.camera`, which sprites are drawn through, `app.input`, the
keyboard, mouse and gamepad state updated at the start of INPUT, `app.commands`, the world command
queue applied after every system, and `app.resources`, shared state keyed by class),
`sandbox` (executable; a smoke test that opens a window and clears it each frame), `shooter`
(executable; the reference game sample, keyboard with mouse aim or gamepad, its art in
`shooter/assets/` synced next to the binary after every link; `KLYPH_AUTOPILOT=1` plays itself,
and `KLYPH_FRAMES` or `KLYPH_SECONDS` bounds a run), `benchmarks`
(executable; named benchmarks, run from the release build), `samples` (the `@sample` functions from
API_STYLE.md section 8, run as tests; `@Sample` is a typealias of `kotlin.test.Test` and
`assertPrints` lives in `samples/src/nativeTest/kotlin/samples/Harness.kt`). `./gradlew check`
runs every module's tests and samples plus the interop-import rule; a single module's tests run
with `:<module>:macosArm64Test`. GPU, window and event functions, `runApp`, `App` and `Assets` have no
samples because they need a display. Shared target config and SDL link flags live in
`buildSrc/src/main/kotlin/klyph.native.gradle.kts`.

```
./gradlew :sandbox:run -Pklyph.frames=120
./gradlew :shooter:run                                          # WASD/arrows/stick, mouse aim, Space/click/south
./gradlew :shooter:run -Pklyph.autopilot=1 -Pklyph.seconds=10   # plays itself, fails if no kill
./gradlew :benchmarks:benchmarkSpritesOffscreen -Pklyph.sprites=100000
./gradlew :benchmarks:benchmarkSprites -Pklyph.sprites=50000     # in a window
./gradlew :benchmarks:benchmarkHierarchy -Pklyph.sprites=5000 -Pklyph.children=8
./gradlew :benchmarks:benchmarkList
```

`:sandbox:run` runs the debug smoke test and `:shooter:run` the debug game; the `benchmark*`
tasks run the release build. Every `KLYPH_*` variable below can be given as a Gradle property
instead (`-Pklyph.sprites=…`, `-Pklyph.frames=…`, `-Pklyph.offscreen=1`, `-Pklyph.vsync=1`,
`-Pklyph.validation=1`, `-Pklyph.autopilot=1`), which is how the IntelliJ run configurations in
`.run/` pass them. `benchmark<Name>Offscreen` sets `KLYPH_OFFSCREEN=1` and 2000 frames;
`-Pklyph.benchmark=<name>` picks the benchmark for the generic `benchmark` task. The binaries
are also runnable directly: `benchmarks/build/bin/macosArm64/releaseExecutable/benchmarks.kexe
sprites`.

`KLYPH_SPRITES=<n>` sprite count (default 10000), `KLYPH_CHILDREN=<n>` children per parent in the
`hierarchy` benchmark (default 8), `KLYPH_FRAMES=<n>` exits after `n` frames, `KLYPH_SECONDS=<s>`
exits once `Time.elapsed` reaches `s` seconds (sandbox, benchmarks and shooter),
`KLYPH_VSYNC=1` caps at the display rate, `KLYPH_VALIDATION=1` turns on the GPU validation layer
(slow; use it after touching the platform module), `KLYPH_OFFSCREEN=1` draws into a texture and
waits for the GPU each frame so the display is out of the loop. `KLYPH_AUTOPILOT=1` makes the
shooter play itself and fail at exit if it destroyed no enemy. macOS has no `timeout`, so bound
runs with `KLYPH_FRAMES`. Do not pipe `./gradlew` output into
another command: the daemon inherits the pipe and the shell never sees EOF. Redirect to a file.

On Windows the host target is `mingwX64`, built with the MinGW toolchain Kotlin/Native downloads
into `~/.konan`; no Visual Studio is needed. Use SDL's own MinGW package,
`SDL3-devel-<version>-mingw.zip` from the SDL GitHub releases, and pass its `x86_64-w64-mingw32`
folder with forward slashes, for example `-Psdl3.prefix=C:/SDL3-3.4.18/x86_64-w64-mingw32`, or
set `sdl3.prefix` in `gradle.properties`; nothing is found automatically on Windows. Binaries link
against the import library `lib/libSDL3.dll.a`, and every link copies `<prefix>/bin/SDL3.dll`
next to its `.exe`, so tests and executables run without `PATH` changes. MSYS2's
`mingw-w64-x86_64-sdl3` also builds (prefix `C:/msys64/mingw64`), but its `SDL3.dll` needs
`libiconv-2.dll`, which is not copied, so put `C:\msys64\mingw64\bin` on `PATH` to run anything.
The `windows` CI job builds this target and runs the tests; it never opens a window, so the D3D12
rendering path is only checked by running the sandbox on a real machine. The same build
cross-compiles from macOS or Linux with `-Pklyph.target=mingwX64 -Psdl3.prefix=<that folder>`
(compile and link only; the tests need Windows to run).

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
