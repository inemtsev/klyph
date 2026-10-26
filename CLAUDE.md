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
`sandbox` (executable; hosts smoke tests and benchmarks). `./gradlew check` enforces the import rule.
Shared target config lives in `buildSrc/src/main/kotlin/klyph.native.gradle.kts`.

```
./gradlew :sandbox:linkDebugExecutableMacosArm64
KLYPH_FRAMES=120 sandbox/build/bin/macosArm64/debugExecutable/sandbox.kexe
```

Use `linkReleaseExecutableMacosArm64` for optimized builds (benchmarks). The sandbox runs the
sprite benchmark: `KLYPH_SPRITES=<n>` sprite count (default 10000), `KLYPH_FRAMES=<n>` exits after
`n` frames, `KLYPH_VSYNC=1` caps at the display rate, `KLYPH_VALIDATION=1` turns on the GPU
validation layer (slow; use it after touching the platform module). macOS has no `timeout`, so
bound runs with `KLYPH_FRAMES`. Do not pipe `./gradlew` output into
another command: the daemon inherits the pipe and the shell never sees EOF. Redirect to a file.
