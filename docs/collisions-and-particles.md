<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/CollisionsAndParticles.kt -->
# Collisions and particles

This page covers two engine steps that run every frame without a system of yours: overlap
detection, which reports touching colliders as `Overlap` events and answers shape queries, and
particles, which emit, move and fade pooled quads from emitter components.

Detection only reports overlaps. Two colliders that overlap keep overlapping until a system moves one of
them, and nothing stops a collider at a wall. For characters that stand on floors and stop at
walls, mark the walls and floors with `Solid` and move the characters with `KinematicBody`; the
[Platformers](platformers.md) page covers both and the sweep queries that go with them.

The snippets on this page are code for the setup block of `runApp`, and they share these
definitions:

<!-- snippet: layers -->
```kotlin
/** The collision layers of the game, one bit each. */
object Layers {
    val PLAYER: CollisionLayers = CollisionLayers.of(0)
    val ENEMY: CollisionLayers = CollisionLayers.of(1)
    val BULLET: CollisionLayers = CollisionLayers.of(2)
}

/** Marks a bullet. A marker carries no data, so the component is the object itself. */
object Bullet : ComponentType<Bullet>("Bullet")

/** Marks an enemy. */
object Enemy : ComponentType<Enemy>("Enemy")
```

## Colliders

A `Collider` component makes an entity take part in overlap detection. Its shape is a
`Shape.Box(width, height)`, an axis-aligned rectangle, or a `Shape.Circle(radius)`, both centered
on the entity's position plus `offsetX` and `offsetY`. The position is the entity's
`WorldTransform` when it has a parent and its `Transform` otherwise. Rotation is ignored, so
boxes stay upright.

<!-- snippet: colliders -->
```kotlin
val disc = assets.disc(24)
commands.spawn {
    add(Transform, Transform(x = 400f, y = 100f))
    add(Sprite, Sprite(disc, color = Color(1f, 0.3f, 0.3f)))
    add(Enemy, Enemy)
    // On the enemy layer; hits the player and bullets.
    add(Collider, Collider(Shape.Circle(12f), layers = Layers.ENEMY, mask = Layers.PLAYER + Layers.BULLET))
}
commands.spawn {
    add(Transform, Transform(x = 400f, y = 600f))
    add(Bullet, Bullet)
    add(Sprite, Sprite(assets.whitePixel, width = 4f, height = 12f))
    // Hits enemies only, so bullets pass through each other and through the player.
    add(Collider, Collider(Shape.Box(width = 4f, height = 12f), layers = Layers.BULLET, mask = Layers.ENEMY))
}
```

## Scale and colliders

The transform's scale scales the collider: a box's width by the x scale and its height by the y
scale, a circle's radius by the x scale alone, and the offset by both. A circle on an entity
scaled unevenly stays a circle, so it no longer matches the stretched sprite. When the scale only
animates the sprite, as a squash or a coin's spin does, set `scalesWithTransform = false` on the
collider and it keeps its size and offset whatever the scale:

<!-- snippet: unscaled-collider -->
```kotlin
val coin = commands.spawn {
    add(Transform, Transform(x = 200f, y = 300f))
    add(Sprite, Sprite(assets.disc(24), color = Color.YELLOW))
    // The spin narrows the sprite, never the hitbox.
    add(Collider, Collider(Shape.Circle(12f)).apply { scalesWithTransform = false })
}
addSystem(Phase.UPDATE) { app ->
    val seconds = app.time.elapsed.inWholeMilliseconds / 1000f
    app.world.get(coin, Transform).scaleX = cos(seconds * 4f)
}
```

## Layers and masks

`layers` says which layers a collider is on and `mask` which layers it hits. `CollisionLayers`
holds up to 32 layers, numbered 0 to 31: `CollisionLayers.of(index)` makes a set of one and `+`
joins sets. `CollisionLayers.DEFAULT` is layer 0, `ALL` every layer and `NONE` no layer. A new
collider is on the default layer and hits everything.

A pair is reported when either collider's mask contains a layer of the other. So a bullet with
the mask `ENEMY` and an enemy with the mask `PLAYER` still overlap: the bullet asked for it. To
keep two kinds apart, leave each out of the other's mask and out of its own: bullets on
`BULLET` with mask `ENEMY` never meet other bullets.

## Overlap events

Once per frame, after the last fixed step and a transform propagation, the engine publishes an
`Overlap` for every overlapping pair, once per pair. Shapes that only touch along an edge do not
overlap. UPDATE, LATE_UPDATE and RENDER systems read the events; `a` is the entity with the
smaller index, so a pair arrives in the same order every frame it lasts.

`entity in overlap` tells whether an entity is in the pair, and `overlap.otherThan(entity)`
returns the other one without checking which slot is which. `otherThan` throws
`IllegalArgumentException` when the entity is in neither slot.

<!-- snippet: overlaps -->
```kotlin
addSystem(Phase.UPDATE) { app ->
    val world = app.world
    app.events.forEach<Overlap> { overlap ->
        if (player in overlap) println("The player touched ${overlap.otherThan(player)}")
        val bullet = when {
            world.has(overlap.a, Bullet) -> overlap.a
            world.has(overlap.b, Bullet) -> overlap.b
            else -> return@forEach
        }
        val target = overlap.otherThan(bullet)
        // An earlier pair this frame may have destroyed either one already.
        if (world.isAlive(bullet) && world.isAlive(target) && world.has(target, Enemy)) {
            app.commands.destroy(bullet)
            app.commands.destroy(target)
        }
    }
}
```

An overlap is reported every frame it lasts, not only when it starts. For a hit that should count
once, destroy one side, as above, or keep the state yourself, such as a short invulnerability
timer on the player. Because one handler may destroy an entity that a later pair still names,
check `world.isAlive` before you act on it.

## Queries

`app.collisions` also answers questions about an area: which colliders overlap a shape placed at
a point. Queries read the positions of the last detection, so from UPDATE on they see this
frame's positions, and from INPUT and FIXED_UPDATE the previous frame's.

| Query | Returns |
|---|---|
| `overlapping(shape, x, y, mask)` | Every entity found, as a new `List`, in ascending index order. |
| `overlappingTo(destination, shape, x, y, mask)` | The same, added to a collection you pass and reuse. |
| `firstOverlappingOrNull(shape, x, y, mask)` | The entity with the smallest index, or `null`. |

<!-- snippet: queries -->
```kotlin
val inBlast = ArrayList<Entity>()
addSystem(Phase.UPDATE) { app ->
    if (!app.input.keyboard.isJustPressed(Scancode.B)) return@addSystem
    val center = app.world.get(player, Transform)
    // Reuses one list instead of allocating a new one per query.
    inBlast.clear()
    app.collisions.overlappingTo(inBlast, Shape.Circle(150f), center.x, center.y, mask = Layers.ENEMY)
    for (enemy in inBlast) app.commands.destroy(enemy)

    val nearest = app.collisions.firstOverlappingOrNull(Shape.Box(width = 40f, height = 40f), center.x, center.y)
    val all = app.collisions.overlapping(Shape.Circle(400f), center.x, center.y)
    println("Destroyed ${inBlast.size}, first in reach: $nearest, ${all.size} within 400 units")
}
```

The query shape is in world units and not scaled. `overlappingTo` with a list you clear and reuse
allocates nothing once the list has grown, which matters for a query run every frame.

## The grid

Detection sorts colliders into a uniform grid of square cells and tests only colliders that share
a cell, so its cost grows with the number of colliders and of close pairs, not with the square of
the collider count. The grid has no bounds and empty space costs nothing. A cell size near the
size of a typical collider works best; set it with `AppConfig(collisionCellSize = 32f)` or later
through `app.collisions.cellSize`. A collider that would cover more than 256 cells is tested
against every other one instead, so one huge collider cannot stall a frame. 10,000 colliders
spread over a large area cost about a third of a millisecond a frame; see
[Performance](performance.md).

Colliders of a scene frozen under a pushed scene are left out of detection and queries.

## Particles

A `ParticleEmitter` component sends out small quads showing one region. Particles live in a pool
of `capacity` slots inside the emitter, not as entities, so thousands of them cost no more than
the arrays they live in. Once a frame, after UPDATE, the engine ages and moves the live particles,
removes those whose lifetime is over and emits new ones at the entity's world position. Once
emitted, a particle moves in world space and no longer follows the entity.

Particles come from two sources: `emit(count)` requests a burst, emitted at the next advance, and
`rate` emits continuously, in particles per second. An explosion is one burst on an entity of its
own. `ParticleEmitter.burst(region, count)` makes such an emitter: its capacity is `count`, the
burst is already requested and `whenEmpty` is `EmitterEnd.DESTROY`. Its named parameters set the
other properties of the table below and default to the same values; set the rest, such as
`angularVelocity`, on the result. `commands.spawnBurst(x, y, emitter)` spawns the entity that
carries it:

<!-- snippet: burst -->
```kotlin
// 64 sparks at once on an entity of their own, which goes away once the last has faded.
val sparks = ParticleEmitter.burst(
    assets.disc(8), count = 64,
    speed = 80f..260f, lifetime = 300.milliseconds..700.milliseconds, gravityY = 200f,
    startColor = Color(1f, 0.8f, 0.3f), endColor = Color(0.8f, 0.1f, 0f, 0f), endScale = 0.2f, layer = 2,
)
commands.spawnBurst(x, y, sparks)
```

With `whenEmpty = EmitterEnd.DESTROY`, the engine destroys the emitter's entity once it has no
live particle, no pending burst and a rate of 0, so the burst fades out and then cleans up after
itself. An emitter set up by hand with `DESTROY` needs its `emit` call before the frame's
particle step, or it is empty at its first advance and is destroyed then; `burst` requests the
burst when it creates the emitter. Destroying an emitter's entity removes its
particles at once, which is why the burst gets its own entity instead of living on the enemy that
exploded.

A thruster emits continuously from a child of the ship, and `EmitterSpace.LOCAL` turns its
direction with the ship:

<!-- snippet: thruster -->
```kotlin
val exhaust = ParticleEmitter(assets.disc(6), capacity = 200).apply {
    rate = 120f
    // Out of the ship's tail: down the screen while the ship faces up, turning with it.
    space = EmitterSpace.LOCAL
    direction = (PI / 2).toFloat()
    spread = 20f.degrees
    speed = 60f..120f
    lifetime = 0.2.seconds..0.4.seconds
    startRotation = 0f..(2 * PI).toFloat()
    angularVelocity = -3f..3f
    startColor = Color(0.5f, 0.8f, 1f)
}
commands.spawn {
    setParent(ship)
    add(Transform, Transform(y = 40f))
    add(ParticleEmitter, exhaust)
}
addSystem(Phase.UPDATE) { app ->
    // Only while thrusting; the particles already out fly on and fade.
    exhaust.rate = if (app.input.keyboard.isDown(Scancode.W)) 120f else 0f
}
```

## Emitter options

Each particle draws its direction, speed, lifetime, start rotation and spin at random when it is
emitted, from the emitter's `random`, `Random.Default` unless you set a seeded one. Angles are in
radians, 0 along +x and positive clockwise on screen; `20f.degrees` converts from degrees.

| Property | Default | Meaning |
|---|---|---|
| `capacity` | 256 | Most particles alive at once; emission beyond it is dropped. Set in the constructor. |
| `rate` | 0 | Particles per second, with fractions carried between frames. |
| `direction` | 0 | Middle of the directions particles leave in. |
| `spread` | `2 * PI` | Width of the fan around `direction`; the default sends them every way. |
| `space` | `EmitterSpace.WORLD` | `LOCAL` turns `direction` by the entity's world rotation at emission. |
| `speed` | `50f..100f` | World units per second. |
| `lifetime` | `0.5.seconds..1.seconds` | How long each particle lives. |
| `startRotation` | `0f..0f` | Rotation at emission. |
| `angularVelocity` | `0f..0f` | Spin in radians per second. |
| `gravityX`, `gravityY` | 0 | Acceleration in world units per second squared; positive y pulls down. |
| `startColor` | `Color.WHITE` | Tint at birth. |
| `endColor` | `null` | Tint at death; `null` is `startColor` at zero alpha, a fade out. |
| `startScale`, `endScale` | 1 | Size as a multiple of the region's size, blended over the lifetime. |
| `layer` | 0 | Draw order, shared with sprites. |
| `isVisible` | `true` | Whether the particles are drawn. |
| `whenEmpty` | `EmitterEnd.KEEP` | `DESTROY` destroys the entity once the emitter has nothing left. |

Color and scale blend linearly from start to end over each particle's life, alpha included.
Setting a property outside its range, such as a negative rate or an empty speed range, throws
`IllegalArgumentException`. `clear()` removes every live particle and the pending burst, and
`particleCount` is the number alive.

Particles of an emitter whose scene is frozen stand still and keep drawing. 100,000 particles in
100 emitters cost about 1.5 ms of CPU a frame; see [Performance](performance.md).

## See also

- [Collider](../klyph/klyph.collision/-collider/index.html),
  [Shape](../klyph/klyph.collision/-shape/index.html) and
  [CollisionLayers](../klyph/klyph.collision/-collision-layers/index.html)
- [Overlap](../klyph/klyph.collision/-overlap/index.html) and
  [otherThan](../klyph/klyph.collision/other-than.html)
- [Collisions](../klyph/klyph.collision/-collisions/index.html)
- [ParticleEmitter](../klyph/klyph.particle/-particle-emitter/index.html),
  [spawnBurst](../klyph/klyph.particle/spawn-burst.html),
  [EmitterEnd](../klyph/klyph.particle/-emitter-end/index.html) and
  [EmitterSpace](../klyph/klyph.particle/-emitter-space/index.html)
- [degrees](../klyph/klyph.math/degrees.html)
- Next: [Platformers](platformers.md)
