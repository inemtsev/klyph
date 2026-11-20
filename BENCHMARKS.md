# Benchmarks

Results from the benchmarks executable. Reproduce with
`./gradlew :benchmarks:benchmark<Name>Offscreen -Pklyph.sprites=<n>` (release build; the
properties mirror the environment variables listed in CLAUDE.md).
Numbers are per-frame averages after a warm-up. Before 2026-11-03 the warm-up was always one
second; since then it ends after one second or after a quarter of the frame limit, whichever
comes first (see "Benchmark warm-up" below).
Since 2026-10-31 a world unit is a logical point, so on a Retina display sprites would cover four
times the pixels they did. The benchmarks spread sprites over the window's size in points and
divide sprite sizes (and the hierarchy's ring radii) by the window's pixel density, so every
sprite still covers the same pixels as in the earlier runs and the GPU fill work matches them.

## Automatic texture atlas (2026-11-20)

`assets.region(path)` packs the files it loads into shared 2048 by 2048 atlas pages, so sprites
of different files in one layer share a draw call. The sample games were run with no change to
their code beyond printing the last frame's `FrameStats.drawCallCount` at exit, once with the atlas
and once with `-Pklyph.atlas=0`:
`./gradlew :games:<game>:run -Pklyph.autopilot=1 -Pklyph.frameDelta=0.016 -Pklyph.frames=<n>`
(625 and 1875 frames are the 10 s and 30 s autopilot runs, `-Pklyph.seconds`). Draw counts depend
on the scene, not the machine. Quads are the frame's sprites, glyphs and particles; the particles
draw from an unseeded `Random`, so the count can differ by a few between runs.

| Game, frame | Quads | Draws, atlas off | Draws, atlas on |
|---|---|---|---|
| shooter, 300 | 653 | 7 | 5 |
| shooter, 625 | 646 | 6 | 5 |
| shooter, 900 | 653 | 5 | 5 |
| roller, 300 | 207 | 25 | 17 |
| roller, 900 | 178 | 25 | 17 |
| roller, 1875 | 193 | 23 | 15 |

The roller loses a third of its draws: its ninja, blobs, helicopters, pickups, signs and posters
now share one page. What is left includes a texture each for the tiled walls (repeating textures
stay apart), its painted sheet, the sky, the dust disc and the built-in font. The shooter's ship,
enemies and bullets share a page, but its explosion sheet, particle texture and text keep their
own, and sprites of different files in different layers still need a draw each. With each emitter's `random` seeded
for the comparison, the shooter's frame 300 screenshot is byte for byte the same with and without
the atlas, and the roller's differs in 2 of 2,073,600 pixels by one level in one channel: its
files are sampled LINEAR, and texture coordinates on a 2048 page round differently from those on
the file's own texture.
## Moving solids (2026-11-20)

`./gradlew :benchmarks:benchmarkCollisionsOffscreen -Pklyph.sprites=<n> -Pklyph.moving=<share>
-Pklyph.frameDelta=0.016667` runs the `collisions` world plus a platformer level: one 64 by 16
`Solid` per ten circles on a jittered grid, a 12 by 12 `KinematicBody` with gravity dropped on
each, and `<share>` of the solids given a `Velocity` of 40 units a second that turns every two
seconds, so their riders are carried. The fixed frame delta makes every frame run one fixed step,
so `fixed` is the cost of one step: the circles' move system (the same in every row) and the
bodies' move, carries and pushes. `collide` includes the solids' rescan. Same machine and
offscreen setup as below, 2,000 frames, other builds running at times; the lines are typical runs.

| circles | solids | moving | `fixed` ms | `collide` ms |
|---|---|---|---|---|
| 10k | none | - | 0.021 | 0.506 |
| 10k | 1,000 | 0 | 0.145 | 0.686 |
| 10k | 1,000 | 0.1 | 0.186 | 0.688 |
| 10k | 1,000 | 0.5 | 0.220 | 0.710 |
| 10k | 1,000 | 1 | 0.248 | 0.742 |
| 50k | 5,000 | 0 | 1.148 to 1.163 | 9.40 |
| 50k | 5,000 | 0.01 | 1.374 to 1.414 | 9.22 to 9.43 |
| 50k | 5,000 | 0.1 | 1.563 | 9.43 |

The first version kept every moving solid in one list that every sweep tested, as the grid's very
large solids are. Each body sweeps three or four times a step, so the cost grew with bodies times
moving solids: 2.32 ms a step at 100 moving solids of 1,000, 14.2 ms at 500 and 40.8 ms at 1,000.
Moving solids now have cells of their own, a second hash grid rebuilt at every rescan over the box
each one covered while it moved, which a sweep visits beside the still solids' cells and a body
asks for the solids that may have moved into it. Forcing every solid into the still grid instead,
so it is rebuilt whenever one moves, gave 0.187 and 0.235 ms for the 10k rows at 0.1 and 1, and
1.27 to 1.42 ms for the 50k rows at 0.01 and 0.1: within the noise of the moving cells at these
sizes, because a counting sort of 5,000 boxes is cheap. The moving cells keep the still grid from
being rebuilt every step in a level whose still solids number in the tens of thousands, such as a
tile map, at the price of a second cell lookup per cell a sweep visits.

## Tile layers (2026-11-20)

The `tiles` benchmark (`./gradlew :benchmarks:benchmarkTilesOffscreen`) draws eight `TileLayer`s of
400 by 300 cells of 16-point tiles, filled with random tiles of one 4-tile sheet, each in its own
sprite layer, under a camera that circles over them. The window is 1280 by 720 points, so each
layer has 81 by 46 cells in view and a frame writes 29,808 records in 8 draw calls; the cells off
screen cost nothing. Same machine and offscreen setup as below, 2,000 frames; the line is a typical
summary.

```
sprites=29808 layers=8 fps=850 frame=1.176ms fixed=0.000ms collide=0.000ms update=0.000ms propagate=0.000ms acquire=0.001ms extract=0.547ms record=0.013ms gpuwait=0.600ms other=0.012ms
```

Extraction costs about 18 ns a cell, against about 6 ns a sprite in a `sprites` run of 100k made
the same hour (extract 0.60 to 0.62 ms with and without the tile pass): each cell's record is built
in a 16-float template and then copied into the mapped buffer. At 0.55 ms for eight full screens of
tiles no per-chunk cache was added. The tile branches sit in `SpriteRenderer.extract` next to the
parallax ones; a version that folded both behind one flag and one call made the 100k sprite
extract 3.2 ms, five times slower, so the branches stay separate.

## Nullable entity handles (2026-11-20)

`Entity` is a value class over a `Long`, so a function that returns `Entity?` may box the handle
on every call that finds one. `Overlap.otherOrNull`, `World.parentOrNull` and
`KinematicBody.floorOrNull` (and `BoxSlide.floorOrNull`) all return one. The `overlaps` benchmark
(`./gradlew :benchmarks:benchmarkOverlapsOffscreen -Pklyph.sprites=<n>`) runs the `collisions`
world and, in `LATE_UPDATE`, visits every `Overlap` of the frame twice: once calling
`otherOrNull(entity)` and once calling `entity in overlap` and then `otherThan(entity)`, which
returns a plain `Entity`. The entity passed is the pair's `a` and `b` in turn, so every call finds
the other one, and both loops add up the index of what they get. The `ornull` and `control`
stages are the two loops, which alternate which runs first; the last line gives the time of one
call of each over the whole run and counts the garbage collections after startup. The `parents`
benchmark (`./gradlew :benchmarks:benchmarkParentsOffscreen -Pklyph.sprites=<n>`) spawns the
`hierarchy` tree and calls `World.parentOrNull` for every child and grandchild in `LATE_UPDATE`;
its `parent` stage is those calls alone, and it counts collections the same way, so `hierarchy`
itself stays free of allocations. Same machine and offscreen setup as below, 10k entities, 2,000
frames, with other builds running on the machine; the lines are a typical run.

```
colliders=10000 calls=2535 fps=877 frame=1.139ms fixed=0.001ms collide=0.481ms update=0.000ms propagate=0.000ms acquire=0.002ms extract=0.065ms record=0.030ms gpuwait=0.488ms ornull=0.020ms control=0.017ms other=0.032ms
ornull=7.83ns/call control=6.62ns/call collections=64 checksum=0
sprites=10000 fps=1033 frame=0.968ms fixed=0.000ms collide=0.000ms update=0.027ms propagate=0.234ms acquire=0.001ms extract=0.155ms record=0.021ms gpuwait=0.363ms parent=0.141ms other=0.024ms
collections=93 checksum=92156960000
```

Five `overlaps` runs gave `otherOrNull` 7.8 to 10.2 ns a call and the control 6.4 to 7.7, with
`otherOrNull` slower by 1.2 to 2.5 ns in every run (1.2 to 1.5 in four of them). At about 2,600
pairs a frame that is 3 to 4 µs, under 1% of the `collide` stage, which read 0.48 to 0.54 ms in
those runs. The box is real, though. Temporary builds that ran one loop only counted 37 or 38
collections with the control alone, from the `Overlap` events detection publishes, and 62 to 64
with `otherOrNull` alone: one box per call, about 2,600 a frame. Release builds do not remove it
even though the result is only read for its index.

`parentOrNull` costs 14.5 ns a call in three runs (`parent` 0.134 to 0.136 ms for 9,228 children)
and makes 92 or 93 collections where the run without the stage makes none. A temporary loop over
the same children calling `World.getOrNull(entity, Transform)`, a lookup that returns a reference,
took 0.100 ms with no collections, so the box and the extra store lookup add about 3.5 ns a call.
`floorOrNull` was not measured; it builds its result the same way, and a game asks it once a
frame per body.

The time is small but the allocation shows in every count, so no note went into the KDoc of the
three helpers; issue #58 holds the proposal of a form that does not box, `Entity.NONE` with
`xOrDefault` siblings, and the decision.

## Drawn position (2026-11-20)

`drawnPositionOf` returns a new `Vec2`, so a game that asks for the drawn position of every entity
allocates once per entity per frame. The `drawn` benchmark
(`./gradlew :benchmarks:benchmarkDrawnOffscreen -Pklyph.sprites=<n>`) measures what that costs. It
moves `KLYPH_SPRITES` sprites in `FIXED_UPDATE`, as `sprites` does with `KLYPH_INTERPOLATE=1`, and
calls `App.drawnPositionOf` for each of them in `LATE_UPDATE`, adding up the results so the calls
stay in the build. The `drawn` stage is those calls alone and `extract` is the sprite pass writing
the same sprites. The last line counts the garbage collections after startup. Same machine and
offscreen setup as below, 10k sprites, 2,000 frames, with other builds running on the machine. Ten
runs gave `drawn` from 0.369 to 0.434 ms, nine of them from 0.369 to 0.403, and `extract` from
0.140 to 0.161 ms; the lines are a typical run.

```
sprites=10000 calls=10000 fps=669 frame=1.494ms fixed=0.005ms collide=0.000ms update=0.000ms propagate=0.000ms acquire=0.001ms extract=0.142ms record=0.023ms gpuwait=0.927ms drawn=0.378ms other=0.014ms
collections=148
```

A call costs 38 ns, so 10,000 of them take 0.38 ms. That is 2.7 times what the sprite pass takes to
write the same sprites (`extract` matches the `sprites` benchmark with interpolation on) and 2.3% of
a 60 Hz frame. The `Vec2` is a small part of it. A build that returned one shared `Vec2` from the
same lookups and blend, with no allocation and no collections, measured `drawn` at 0.34 to 0.35 ms
in three runs. The allocation and its 148 collections therefore cost about 4 ns a call, 0.04 ms for
10,000, and the other 34 ns is the lookups and the blend, which any form of the call pays. No
non-allocating form was added.

An entity with a parent also walks up its chain, because `drawnPositionOf` composes the current
transforms of the chain instead of reading the last propagation. A temporary variant of the
benchmark put a chain of bare children under each sprite and asked for the last child, two runs
each: 65 ns for a chain of one child, then 80, 109 and 154 ns for chains of two, three and five.
Each further level adds about 20 ns. Roots cost the same 38 ns as before the change.

## Benchmark warm-up (2026-11-03)

The summary used to skip the first second and then add up whole seconds, so a run whose frame
limit ended inside the first second printed a summary over 0 frames, and the frames after the
last whole second were dropped. The 10k `collisions` run at the default 2,000 frames lasts about
1.5 s, so no whole second followed the warm-up, and it printed this:

```
--- summary (after 1 s warm-up, 0 frames) ---
too short to summarize
```

The warm-up now starts at the first recorded frame and ends after one second or after a quarter
of `KLYPH_FRAMES`, whichever comes first, so it never skips more frames than it leaves. Every frame
after it counts. The summary line names the warm-up it used, for example
`--- summary of 1500 frames after a warm-up of 500 frames (0.44 s) ---`. Runs longer than about
four seconds still warm up for one second, so the 100k `sprites` numbers did not move: 2.410 ms
before and 2.409 ms after.

## Collisions (2026-11-03)

The `collisions` benchmark moves `KLYPH_SPRITES` circle colliders with radii from 4 to 12 world
units at 20 to 100 units per second inside a 4000 by 4000 area, bouncing at its edges, with seed
42 so every run places them the same. The engine detects overlaps once per frame after
`FIXED_UPDATE` on a uniform grid of `KLYPH_CELL` units (default 64). The `collide` stage is the
detection alone, including the `Overlap` events it publishes; the circles are also drawn, with the
camera zoomed out to fit the area. Same machine and offscreen setup as below, 2,000 frames, two
runs of each; the lines are the second run, and the first agrees within 0.01 ms on `collide` at
10k and 0.09 ms at 100k.

```
colliders=10000 cell=64.0 fps=1528 frame=0.654ms fixed=0.000ms collide=0.355ms update=0.000ms propagate=0.000ms acquire=0.000ms extract=0.048ms record=0.011ms gpuwait=0.229ms other=0.008ms
collision pairs per frame: 2631.4
colliders=100000 cell=64.0 fps=46 frame=21.894ms fixed=0.343ms collide=18.160ms update=0.001ms propagate=0.000ms acquire=0.006ms extract=0.562ms record=0.055ms gpuwait=2.629ms other=0.134ms
collision pairs per frame: 261542.8
```

At 10k the area is sparse: about 2,600 pairs a frame, and detection costs 36 ns per collider.
At 100k in the same area the density is ten times higher and the pairs grow a hundredfold, to
about 262,000 a frame, so the 100k run is a stress case rather than a game scene. Detection then
costs 18.2 ms, about 70 ns per reported pair including its event. A game with 10k colliders
spread like the 10k run spends about a third of a millisecond a frame on collisions.

## Particles (2026-11-03)

The `particles` benchmark runs one emitter per 1000 of `KLYPH_SPRITES`, spread over the window in
a grid. Each emitter starts with a full pool of 1000 particles and a rate that refills it faster
than particles die, so about 100,000 particles are alive at 100k. Particles are 12 pixels across
at emission and shrink to 6, with random rotation speed, gravity and a per-emitter tint, all from
one texture in one layer, so the frame is one draw call. The `particles` stage is the engine's
advance (aging, removal, emission and movement); writing their records counts in `extract`. Same
machine and offscreen setup as below, 6,000 frames; three 2,000-frame runs gave `extract`
1.20 to 1.28 ms and `particles` 0.19 to 0.21 ms, with `gpuwait` from 0.87 to 1.39 ms.

```
emitters=100 particles=99954 fps=416 frame=2.401ms fixed=0.000ms collide=0.000ms update=0.000ms propagate=0.000ms acquire=0.000ms extract=1.246ms record=0.017ms gpuwait=0.918ms particles=0.203ms other=0.015ms
```

Advancing costs 2 ns per particle. Extraction costs 12.5 ns per particle against 4.6 ns per
sprite in the `sprites` benchmark. Each particle's record is written twice: first into the
pass's quad buffer, with its color and scale blended by age, then copied into staging memory
with a group lookup per quad, the path text and `App.canvas` quads take. Entity sprites are
written into staging memory once.

## Draw spaces and text (2026-11-02)

The sprite pass now sorts every record by space, then layer, then texture, and draws the `WORLD`
runs through the camera and the `SCREEN` runs through a projection in points. `Text` components,
particles and `App.canvas` quads go through the same record buffer. The 100k `sprites` run measured
2.43 ms after the change, against 2.45 ms in the interpolation section below, so a world with no
text and no screen-space quads pays nothing measurable. Each texture also carries a sampler filter
since 2026-11-03; the renderer holds a linear and a nearest sampler and binds the one that matches
each run's texture with it, and the 100k run stayed at 2.41 ms (2.410 ms before, 2.409 ms after).

## Mixer (2026-11-02)

The `mixer` benchmark mixes audio headless, with no window and no audio device, through the public
`Audio(config)` and `Audio.renderer`. It plays 32 looping voices of a one-second mono sine at
44.1 kHz, at pitches from 0.5 to 2 and pans across the field, plus a looping music track, and
renders 60 s of 48 kHz stereo in 1024-frame blocks, the device's default block. By default the
music is a synthetic stereo stream that copies from a precomputed table, so it measures the
mixer alone; `KLYPH_MUSIC=<file.ogg>` streams a real Ogg Vorbis file instead, decoded by
stb_vorbis as it plays. Reproduce with `./gradlew :benchmarks:benchmarkMixer` and
`./gradlew :benchmarks:benchmarkMixer -Pklyph.music=games/shooter/assets/music.ogg` (the shooter's
46.8 s mono 44.1 kHz track, which loops once during the run). Same machine as below, release
build. Three runs of each, alternated; the lines are the second pair, and the other runs agree
within 3 us on the mean.

```
synthetic music: 32 voices + music, 2812 blocks of 1024 frames at 48000 Hz: per block mean=54.08us max=85.00us budget=21333us (0.254% of the block's real time)
Ogg Vorbis music: 32 voices + music, 2812 blocks of 1024 frames at 48000 Hz: per block mean=60.29us max=116.88us budget=21333us (0.283% of the block's real time)
```

A block is 21.3 ms of sound, and mixing it takes about 0.06 ms, 0.3% of the audio thread's time,
or 1.7 us per voice. Decoding the Vorbis track adds about 6 us per block, roughly 940 source
frames of mono, so music costs about as much as three or four effect voices. The worst block
stays under 0.2 ms in every run. The headroom matters because Kotlin/Native's garbage collector
can pause the audio thread along with the main thread, and the 1024-frame device buffer has to
ride that out; the mixer itself allocates nothing, so it never starts a collection.

## Fixed-step interpolation (2026-10-31)

Entities marked `Interpolated` are drawn between their last two fixed steps. At the start of each
fixed step the engine copies their `Transform` into an internal previous-transform component, and
`extract` blends each such sprite from that copy toward the current `Transform` by
`Time.fixedStepAlpha`, one sparse lookup per sprite, skipped when no entity has a copy. The same
build added `Sprite.isVisible`, which `extract` checks for every sprite. `KLYPH_INTERPOLATE=1`
(`-Pklyph.interpolate=1`) makes the `sprites` benchmark mark every sprite and move them in
`FIXED_UPDATE` at the default 60 Hz instead of in `UPDATE`, at the same speeds. The benchmark
lines gained a `fixed` stage (all of a frame's fixed steps, the engine's copy included) and the
summary prints the fixed steps per frame. Same machine and offscreen setup as below, 100k sprites,
2,000 frames. Three rounds alternated "before" (a build of the previous tree) with off and on; the
lines are the second round, and the other rounds agree within 0.02 ms per stage except one noisy
"before" round whose gpuwait rose 0.1 ms.

```
before:          sprites=100000 textures=1 fps=411 frame=2.431ms update=0.211ms propagate=0.000ms acquire=0.000ms extract=0.476ms record=0.016ms gpuwait=1.708ms other=0.018ms
interpolate=off: sprites=100000 textures=1 fps=409 frame=2.447ms fixed=0.000ms update=0.210ms propagate=0.000ms acquire=0.000ms extract=0.493ms record=0.015ms gpuwait=1.709ms other=0.017ms
interpolate=on:  sprites=100000 textures=1 fps=397 frame=2.517ms fixed=0.078ms update=0.000ms propagate=0.000ms acquire=0.001ms extract=0.676ms record=0.016ms gpuwait=1.724ms other=0.018ms
hierarchy before: sprites=100000 fps=275 frame=3.638ms update=0.301ms propagate=1.264ms acquire=0.002ms extract=0.741ms record=0.041ms gpuwait=1.253ms other=0.033ms
hierarchy after:  sprites=100000 fps=272 frame=3.678ms fixed=0.000ms update=0.301ms propagate=1.291ms acquire=0.002ms extract=0.763ms record=0.041ms gpuwait=1.244ms other=0.033ms
```

With interpolation off the frame is 0.7% slower than before, and extract 0.02 ms slower, which is
the visibility check. The hierarchy frame is 1.1% slower, extract again 0.02 ms of it.

With interpolation on, extract costs 0.18 ms more than off (1.8 ns per sprite for the lookup and
the blend). Offscreen runs at about 400 fps, so a 60 Hz fixed step runs in only 0.15 of the
frames, about every seventh. The `fixed` stage averages 0.078 ms per frame, or 0.52 ms per step,
of which the movement is about 0.21 ms (the `update` stage of the off run), so the copy costs
about 0.31 ms per step at 100k, or 0.05 ms per frame at this cadence. The per-frame cost of
interpolation is therefore about 0.23 ms here. The on frame still comes out only 0.07 ms above
off, because the movement runs 60 times a second instead of 400. At 60 fps with one step per
frame the copy would cost its full 0.31 ms every frame.

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
