# Benchmarks

Results from the `sprites` benchmark. Reproduce with the release build of the `benchmarks`
executable, `benchmarks.kexe sprites`, and the environment variables listed in CLAUDE.md.
Numbers are per-frame averages after a one-second warm-up.

## Sprite rendering, vertex pulling (2026-10-27)

Machine: Apple M4 Pro, 48 GB, macOS, SDL 3.4.18 on Metal, Kotlin/Native 2.4.20 release build.
Window 1280x720 points at 2x density. Each sprite is a 24x24 alpha-blended textured quad with
its own transform, moved and spun every frame by a system over Transform and Velocity components.

Offscreen mode (`KLYPH_OFFSCREEN=1`): draw into a texture and wait on a fence, so the display is
not in the loop. "update" is the movement system, "extract" writes one 64-byte record per sprite
into mapped staging memory, "gpuwait" is submit-to-fence time.

| Sprites | fps | frame ms | update ms | extract ms | record ms | gpuwait ms |
|---|---|---|---|---|---|---|
| 10,000 | 2687 | 0.372 | 0.021 | 0.035 | 0.010 | 0.303 |
| 100,000 | 434 | 2.304 | 0.213 | 0.364 | 0.015 | 1.710 |
| 300,000 | 136 | 7.368 | 0.698 | 1.198 | 0.030 | 5.439 |
| 1,000,000 | 40 | 24.703 | 2.110 | 4.047 | 0.060 | 18.480 |

Per sprite that is about 2 ns of update and 4 ns of extract on the CPU. The GPU becomes the
limiter at 100k sprites and up, as expected for fill rate at this sprite size.

Presenting to the window (default mode): every run is paced at 121 fps by the display, even with
the immediate present mode accepted by SDL, and the wait shows up in the swapchain acquire stage.
CPU stages also measure 3 to 5 times slower in that mode (for example extract 1.9 ms at 100k),
most likely because the core downclocks while it idles in the acquire wait. Use offscreen mode
for CPU and GPU cost; use presenting mode only to check that the frame reaches the screen.

Not yet measured: instancing versus vertex pulling, indexed versus non-indexed quads, and
compute-generated vertices. The platform API needs vertex input state before the first two can run.
