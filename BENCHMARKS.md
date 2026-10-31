# Benchmarks

Results from the `sprites` benchmark. Reproduce with
`./gradlew :benchmarks:benchmarkSpritesOffscreen -Pklyph.sprites=<n>` (release build; the
properties mirror the environment variables listed in CLAUDE.md).
Numbers are per-frame averages after a one-second warm-up.
Since 2026-10-31 a world unit is a logical point, so on a Retina display sprites would cover four
times the pixels they did. The benchmarks spread sprites over the window's size in points and
divide sprite sizes (and the hierarchy's ring radii) by the window's pixel density, so every
sprite still covers the same pixels as in the earlier runs and the GPU fill work matches them.

## Propagation only for hierarchies (2026-10-31)

`propagateTransforms` no longer copies every root `Transform` into a `WorldTransform`. Only
entities with a parent have a `WorldTransform`, and the walk starts from roots that have
children. `extract` iterates `Transform` and `Sprite` and does one sparse lookup per sprite into
the `WorldTransform` store, skipped when the store is empty. The same build also moved the sort's
per-texture cache from `Texture` fields into arrays indexed by texture id, and passes each run's
first sprite as a uniform instead of as the draw's first vertex. Same machine and offscreen setup
as below, 100k sprites, 2,000 frames, "before" from a build of the previous tree run the same
session.

```
before textures=1: sprites=100000 textures=1 fps=354 frame=2.823ms update=0.217ms propagate=0.358ms acquire=0.001ms extract=0.470ms record=0.028ms gpuwait=1.721ms other=0.024ms
after  textures=1: sprites=100000 textures=1 fps=409 frame=2.446ms update=0.219ms propagate=0.000ms acquire=0.000ms extract=0.460ms record=0.016ms gpuwait=1.729ms other=0.018ms
before textures=8: sprites=100000 textures=8 fps=337 frame=2.963ms update=0.216ms propagate=0.346ms acquire=0.001ms extract=0.634ms record=0.021ms gpuwait=1.721ms other=0.021ms
after  textures=8: sprites=100000 textures=8 fps=377 frame=2.652ms update=0.220ms propagate=0.000ms acquire=0.000ms extract=0.662ms record=0.019ms gpuwait=1.727ms other=0.021ms
hierarchy before:  sprites=100000 fps=249 frame=4.020ms update=0.377ms propagate=1.513ms acquire=0.002ms extract=0.608ms record=0.042ms gpuwait=1.442ms other=0.033ms
hierarchy after:   sprites=100000 fps=277 frame=3.609ms update=0.325ms propagate=1.211ms acquire=0.002ms extract=0.736ms record=0.042ms gpuwait=1.256ms other=0.034ms
```

The flat `sprites` frame drops by the whole propagate stage, and extract stays within noise because
the `WorldTransform` store is empty. In `hierarchy`, propagate falls 0.30 ms: the 7.7k roots get
no copy, and the pass visits the 23k entities that have children instead of all 100k. Extract
rises 0.13 ms there because 92% of its sprites take the extra lookup, so the CPU stages together
save about 0.22 ms. Its gpuwait also fell 0.19 ms, which this change does not explain. The
texture-id cache and the per-run uniform push do not show in these numbers.

## Multiple textures (2026-10-31)

`SpriteRenderer` now takes each sprite's texture from its `TextureRegion` and sorts sprites by
layer, then texture, with one draw call per run of sprites that share a texture. The `sprites`
benchmark gained `KLYPH_TEXTURES` (`-Pklyph.textures`, default 1): that many 64x64 circle
textures with different tints, assigned to sprites in turn, all in layer 0, so 8 textures give
8 draw calls a frame. In the `hierarchy` benchmark, parents, children and grandchildren share
one texture in layers 0, 1 and 2, so children draw on top of their parents. Same machine and
offscreen setup as below, 100k sprites, 2,000 frames each. The "before" lines come from a build
of the previous commit, run the same day.

```
before:     sprites=100000 fps=380 frame=2.629ms update=0.213ms propagate=0.324ms acquire=0.000ms extract=0.362ms record=0.014ms gpuwait=1.697ms other=0.016ms
textures=1: sprites=100000 textures=1 fps=368 frame=2.721ms update=0.213ms propagate=0.338ms acquire=0.000ms extract=0.440ms record=0.013ms gpuwait=1.697ms other=0.016ms
textures=8: sprites=100000 textures=8 fps=343 frame=2.919ms update=0.213ms propagate=0.334ms acquire=0.000ms extract=0.634ms record=0.014ms gpuwait=1.704ms other=0.016ms
hierarchy before: sprites=100000 fps=279 frame=3.589ms update=0.356ms propagate=1.454ms acquire=0.001ms extract=0.379ms record=0.033ms gpuwait=1.333ms other=0.029ms
hierarchy after:  sprites=100000 fps=253 frame=3.949ms update=0.370ms propagate=1.458ms acquire=0.001ms extract=0.590ms record=0.033ms gpuwait=1.463ms other=0.029ms
```

Sorting costs 0.08 ms of extract at 100k when the sprites are already in draw order (one texture),
and 0.27 ms when every neighbour has a different texture (8 textures round robin). Eight draw
calls instead of one leave gpuwait and record unchanged. The hierarchy's extract rises 0.21 ms
because its layers alternate from one sprite to the next, so every frame writes records out of
storage order. Its gpuwait rose 0.13 ms, which these runs do not explain.

Extract walks the world once. Each texture caches its group for up to four layers, so finding a
sprite's (layer, texture) group takes a few field reads. The previous frame picks how records are
written. If it was already in draw order, records go out in storage order and only group sizes
are counted. Otherwise the previous frame's group sizes give every sprite its final position
during the walk. Either guess is checked after the walk. A wrong one, such as a frame that adds
sprites to a group, costs a second walk into a scratch array and a copy to the sorted positions.
Two earlier versions measured 0.92 ms (two walks, a key stored per sprite) and 0.60 ms (one walk,
one function call per sprite). On Kotlin/Native a call that is not inlined costs a few
nanoseconds of frame setup, so the per-sprite path is inline.

## Hierarchy propagation (2026-10-31)

The `hierarchy` benchmark (`./gradlew :benchmarks:benchmarkHierarchyOffscreen -Pklyph.sprites=<n>`)
draws the same circle sprites as `sprites`, arranged as a three-level tree. Each parent is a 32x32
sprite that moves, spins and bounces like a `sprites` entity. It has `KLYPH_CHILDREN` (default 8)
12x12 children on a ring of radius 28 in local space, and every fourth child has two 6x6
grandchildren on a ring of radius 12. Children and grandchildren only spin in place (a `Spin`
component), so they orbit because their parent rotates. A family is 13 sprites, so 10k sprites
make 769 families plus 3 extra parents, and 100k make 7,692 families plus 4. Same machine and
offscreen setup as above; 10k ran 6,000 frames and 100k ran 2,000.

```
parents=772 children=6152 (up to 8 each) grandchildren=3076 total=10000 depth=3
sprites=10000 fps=2375 frame=0.420ms update=0.025ms propagate=0.118ms acquire=0.000ms extract=0.035ms record=0.010ms gpuwait=0.224ms other=0.005ms
parents=7696 children=61536 (up to 8 each) grandchildren=30768 total=100000 depth=3
sprites=100000 fps=279 frame=3.587ms update=0.380ms propagate=1.537ms acquire=0.002ms extract=0.393ms record=0.042ms gpuwait=1.195ms other=0.034ms
```

Propagating the tree costs about 4.7 times the flat `sprites` case at 100k (1.54 ms against 0.33 ms
in a run on the same day) and 4.7 times at 10k (0.118 ms against 0.025 ms).

The flat `sprites` propagate stage read 0.25 ms in the section below and 0.33 ms on the day this
benchmark was added, with no change to the engine. The only difference was more benchmark code
in the same binary. Kotlin/Native compiles the whole program together, so a tight loop's cost can
shift when unrelated code changes. Compare numbers built from the same tree on the same day.

## Sprite rendering through App (2026-10-31)

Same machine and scene as the section below. The benchmark is now a klyph app: `runApp` with
`RenderTarget.OFFSCREEN` and `GpuSync.WAIT_EACH_FRAME`, and the stages come from `App.stats`.
"update" is the UPDATE phase, which holds only the movement system. "propagate" is the transform
propagation that the entity hierarchy added after the previous section. "record" covers upload,
clear, draw and submit. "other" is everything else in the frame: event polling, the empty phases
and the loop itself. 10k ran 10,000 frames, because 2,000 frames end inside the one-second
warm-up; 100k ran 2,000 frames and 1M ran 1,000.

| Sprites | fps | frame ms | update ms | propagate ms | extract ms | record ms | gpuwait ms | other ms |
|---|---|---|---|---|---|---|---|---|
| 10,000 | 2448 | 0.408 | 0.021 | 0.025 | 0.034 | 0.011 | 0.309 | 0.005 |
| 100,000 | 394 | 2.536 | 0.213 | 0.248 | 0.351 | 0.013 | 1.691 | 0.017 |
| 1,000,000 | 38 | 26.507 | 1.957 | 2.802 | 4.032 | 0.045 | 17.624 | 0.042 |

The hand-written loop, built from the same tree and run the same day, measured 0.400, 2.519 and
26.568 ms, so App costs at most 0.02 ms per frame. The rise over the previous section (2.30 ms at
100k) comes from transform propagation, not from the loop. Presenting to the window, 10k sprites
still run at the display's 120 fps, with the wait in the acquire stage.

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
