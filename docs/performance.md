<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/Performance.kt -->
# Performance

klyph draws 100,000 moving sprites in 2.5 ms a frame on an Apple M4 Pro, so a typical 2D game
spends most of its frame waiting for the display. This page shows what each engine stage costs,
how to measure your own game, and the habits that keep a frame cheap: allocate nothing per frame
in hot loops, keep draw calls few, and use the engine's pooled systems instead of entities for
things that come and go by the thousand.

## The benchmarks

The klyph repository has a `benchmarks` executable with named benchmarks, each a klyph app run
from the release build. The offscreen variants draw into a texture and wait for the GPU every
frame, so the display's refresh is out of the loop and both CPU and GPU cost show:

```
./gradlew :benchmarks:benchmarkSpritesOffscreen -Pklyph.sprites=100000
./gradlew :benchmarks:benchmarkHierarchyOffscreen -Pklyph.sprites=100000
./gradlew :benchmarks:benchmarkCollisionsOffscreen -Pklyph.sprites=10000 -Pklyph.cell=64
./gradlew :benchmarks:benchmarkParticlesOffscreen -Pklyph.sprites=100000
./gradlew :benchmarks:benchmarkMixer
./gradlew :benchmarks:benchmarkList
```

Each run prints per-frame averages after a warm-up, stage by stage. `BENCHMARKS.md` in the
repository has the method, the history and every caveat.

## What each stage costs

Apple M4 Pro, Metal, release build, offscreen, per-frame averages:

| Scene | Frame | Stage costs |
|---|---|---|
| 10,000 sprites | 0.41 ms | update 0.02, extract 0.03, GPU 0.31 ms |
| 100,000 sprites | 2.54 ms | update 0.21, extract 0.35, GPU 1.69 ms |
| 1,000,000 sprites | 26.5 ms | update 1.96, extract 4.03, GPU 17.6 ms |
| 100,000 sprites in a three-level hierarchy | 3.59 ms | propagate 1.54, extract 0.39 ms |
| 10,000 circle colliders, 2,600 pairs | 0.65 ms | detection 0.36 ms |
| 100,000 particles in 100 emitters | 2.40 ms | advance 0.20, extract 1.25 ms |
| 32 voices and music, per 21 ms audio block | 0.06 ms | on the audio thread |

Per item that comes to about:

- **2 ns of update and 4 ns of extraction per sprite** on the CPU. Extraction writes one 64-byte
  record per sprite into GPU memory; the vertex shader builds each quad from it, and the whole
  frame is one draw call per texture and layer.
- **Hierarchies cost 4.7 times a flat world** to propagate. Only entities with a parent pay it:
  a world without parents spends nothing on propagation.
- **36 ns per collider** for detection in a sparse scene. Cost grows with close pairs, about 70 ns
  per reported pair including its event, so a crowded pile costs more than a spread-out one.
- **2 ns to advance and 12.5 ns to extract per particle.** Particles are cheaper than entities
  to create and destroy, but each is written twice on the way to the GPU.
- **`Interpolated` adds about 1.8 ns per sprite** to extraction plus a copy of each marked
  transform at every fixed step.
- **Sorting** costs little when sprites already come in draw order: 0.08 ms at 100k with one
  texture, 0.27 ms when every neighbor has a different texture.

The GPU limits large scenes. At 100k sprites of 24 by 24 pixels it spends 1.7 ms filling them, so
large, overlapping, alpha-blended sprites cost more than the count suggests.

## Measuring your game

Press F3 for the debug overlay: frame rate, the time of each phase and engine step, counts, and a
graph of the last 60 frames. The same numbers are in `app.stats`, for logging or for a check in
an automated run:

<!-- snippet: stats -->
```kotlin
addSystem(Phase.UPDATE) { app ->
    val stats = app.stats
    if (app.time.frameCount % 300L == 0L) {
        println(
            "frame ${stats.total}, update ${stats.durationOf(Phase.UPDATE)}, extract ${stats.spriteExtraction}, " +
                "${stats.spriteCount} sprites in ${stats.drawCallCount} draw calls",
        )
    }
}
```

Measure the release build, `./gradlew runRelease`, and measure offscreen with
`-Pklyph.offscreen=1`. In a window the frame waits for the display, and the CPU may slow its clock
while it waits, so CPU stages read three to five times slower there than offscreen. Compare
numbers from the same build on the same day: Kotlin/Native compiles the whole program at once, so
unrelated code changes can move a tight loop's cost by a few percent.

## Allocate nothing per frame in hot loops

Kotlin/Native has a garbage collector, and a collection pauses every thread, the audio thread
included. The engine's per-frame paths allocate nothing once their buffers have grown, so the
collector only runs for what your code allocates. Code that runs once per entity per frame is
where allocations add up.

`Vec2` is an immutable class, so every `Vec2` operation creates an object. It reads well in code
that runs a few times a frame, but in a loop over thousands of entities it allocates thousands of
objects a frame:

<!-- snippet: allocating -->
```kotlin
// Allocates four Vec2 objects per enemy per step.
addSystem(Phase.FIXED_UPDATE) { app ->
    val target = app.world.get(player, Transform)
    app.world.forEach(Transform, Enemy) { _, transform, _ ->
        val toTarget = Vec2(target.x, target.y) - Vec2(transform.x, transform.y)
        if (toTarget.length > 1f) {
            val step = toTarget * (150f / toTarget.length * app.time.deltaSeconds)
            transform.x += step.x
            transform.y += step.y
        }
    }
}
```

The same motion on plain floats allocates nothing:

<!-- snippet: floats -->
```kotlin
// The same motion on plain floats allocates nothing.
addSystem(Phase.FIXED_UPDATE) { app ->
    val target = app.world.get(player, Transform)
    val dt = app.time.deltaSeconds
    app.world.forEach(Transform, Enemy) { _, transform, _ ->
        val dx = target.x - transform.x
        val dy = target.y - transform.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > 1f) {
            transform.x += dx / distance * 150f * dt
            transform.y += dy / distance * 150f * dt
        }
    }
}
```

The same goes for collections and text. Create lists once and refill them, use the `To` form of a
query, and hide a sprite with `isVisible` rather than destroying and respawning it when it comes
back soon:

<!-- snippet: reuse -->
```kotlin
// Created once, filled every frame.
val nearby = ArrayList<Entity>()
val probe = Shape.Circle(64f)
addSystem(Phase.UPDATE) { app ->
    app.world.forEach(Transform, Sprite) { _, transform, sprite ->
        nearby.clear()
        app.collisions.overlappingTo(nearby, probe, transform.x, transform.y)
        // Hide instead of destroying and respawning: no component stores change.
        sprite.isVisible = nearby.size < 8
    }
}
```

Other habits that keep the collector quiet:

- Refill one `StringBuilder` for text that changes every frame; `Text.text` and
  `canvas.drawText` take any `CharSequence`. [Text, canvas and overlay](text-canvas-and-overlay.md)
  shows the pattern.
- Share frame lists between animations. `SpriteAnimation(sheet, ...)` shares the sheet's list.
- Avoid capturing lambdas created per entity per frame; `world.forEach` is inline and allocates
  nothing.

## Keep draw calls few

Every distinct pair of layer and texture in a frame is one draw call, and sprites are sorted by
space, layer and texture before drawing. A few hundred draw calls are fine; thousands are not.

- Put the frames and tiles that draw together on one texture: a sprite sheet or atlas, cut with
  `SpriteSheet.fromGrid` or `texture.region`.
- Use layers as a handful of draw-order groups, such as background, world, effects and HUD, not
  one per sprite.
- Rectangles on the canvas and the built-in font share one texture, so a HUD of panels and text
  can be one draw call.

## Use the pooled systems

- **Particles** for sparks, smoke, exhaust and debris. A pool of 1000 particles is one component
  on one entity; 1000 entities would each need stores and commands.
- **The canvas** for drawing that changes every frame, such as a HUD or debug shapes, instead of
  spawning entities for it.
- **`Lifetime` and `Fade`** for timed removal, instead of a system of your own that scans every
  entity.

## What to avoid

- Deep hierarchies for things that do not move together. Parent an entity only when it must
  follow another.
- Moving `Interpolated` entities outside FIXED_UPDATE: the engine copies their transforms every
  step, and moves made elsewhere are drawn partly blended.
- A collision cell size far from your colliders' size. Much smaller puts each collider in many
  cells; much larger tests many distant pairs. Start near the size of a typical collider.
- Queries in FIXED_UPDATE that expect this frame's positions: they read the last detection,
  which ran in the previous frame.
- Loading assets inside a system. Loaders cache, but the first call reads and uploads the file in
  the middle of a frame. Load in the setup block, STARTUP or a scene's `onEnter`.

## See also

- [FrameStats](../klyph/klyph.app/-frame-stats/index.html)
- [DebugOverlay](../klyph/klyph.app/-debug-overlay/index.html)
- [Collisions.overlappingTo](../klyph/klyph.collision/-collisions/overlapping-to.html)
- [ParticleEmitter](../klyph/klyph.particle/-particle-emitter/index.html)
- [The documentation home](index.md), with every guide page
