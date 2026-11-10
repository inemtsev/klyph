<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/Platformers.kt -->
# Platformers

`Collisions` reports which colliders overlap, and that is all it does for ordinary colliders: a
hero that falls onto a roof sinks into it until a system pushes it back out. For characters that
stand on floors, stop at walls and jump through ledges, klyph has two components on top of the
same colliders. A `Solid` is a wall, floor or ledge. A `KinematicBody` moves in every fixed step
and stops at the solids in its way. There is no physics engine behind them: no mass, no
bouncing, no slopes and no moving platforms, only upright boxes that stop exactly where they
touch.

What makes a platformer feel good stays in your code, because every game tunes it differently:
gravity, acceleration, jump height, coyote time and the jump buffer. This page builds a hero with
all of them in one `FIXED_UPDATE` system. The snippets are code for the setup block of `runApp`.

## Solids

A `Solid` goes on an entity with a `Transform` and a `Collider` whose shape is a `Shape.Box`. The
box is placed as for any collider: centered on the entity's position plus the collider's offset,
scaled by the transform unless `scalesWithTransform` is `false`. A level built from data is
easiest to write by edges, so this helper turns edges into a solid:

<!-- snippet: spawn-solid -->
```kotlin
/** Spawns a roof, a wall or a ledge of the level from its edges. */
fun App.spawnSolid(left: Float, top: Float, right: Float, bottom: Float, passable: Passable = Passable.NEVER): Entity =
    commands.spawn {
        add(Transform, Transform(x = (left + right) / 2, y = (top + bottom) / 2))
        add(Sprite, Sprite(assets.whitePixel, width = right - left, height = bottom - top, color = Color(0.3f, 0.3f, 0.4f)))
        add(Collider, Collider(Shape.Box(width = right - left, height = bottom - top)))
        add(Solid, Solid(passable))
    }
```

<!-- snippet: level -->
```kotlin
// Two roofs with a gap between them, and a steel ledge over the gap.
spawnSolid(left = 0f, top = 400f, right = 900f, bottom = 1000f)
spawnSolid(left = 1100f, top = 360f, right = 1800f, bottom = 1000f)
spawnSolid(left = 860f, top = 300f, right = 1140f, bottom = 312f, passable = Passable.FROM_BELOW)
```

`Solid(passable = Passable.FROM_BELOW)` is a one-way ledge. A body that moves down onto it, with
its feet at or above the ledge's top before the move, lands on it. A body that jumps up through
it, or walks into it from the side, passes through. The default, `Passable.NEVER`, stops bodies
from every side.

A solid on a circle collider, or without a collider, is a setup error: the next body move throws
`IllegalStateException` naming the entity. Solids are also ordinary colliders, so they still
take part in `Overlap` events and the overlap queries, and their layers and masks work as on the
[Collisions and particles](collisions-and-particles.md) page.

Solids do not move. `app.collisions` keeps them in a grid of its own and checks them against the
world once per fixed step and once per frame, which costs one pass over the solids, and rebuilds
the grid only when a solid was added, removed, moved or resized. A solid you move still stops
bodies where it now is from the next step, but nothing carries a body along with it.

## The body

A `KinematicBody` goes on an entity with a `Transform` and a box `Collider`. Its `dx` and `dy` are
its speed in world units per second, positive to the right and down. The body moves its
`Transform` in world space, so it cannot have a parent.

<!-- snippet: hero -->
```kotlin
val hero = commands.spawn {
    add(Transform, Transform(x = 100f, y = 380f - Collisions.CONTACT_GAP))
    add(Sprite, Sprite(assets.whitePixel, width = 24f, height = 40f, color = Color.YELLOW))
    add(Collider, Collider(Shape.Box(width = 24f, height = 40f)))
    add(KinematicBody, KinematicBody())
    // Drawn between its last two steps, so it moves smoothly at any frame rate.
    add(Interpolated, Interpolated)
}
```

In every fixed step, after the `FIXED_UPDATE` systems and after every `Velocity`, the engine moves
each body across by `dx` times the step, stopping at the first solid it would enter, and then up
or down by `dy` in the same way. A stop leaves a gap of `Collisions.CONTACT_GAP`, 0.01 units,
between the body and the solid, so the body never overlaps it and walking along a floor does not
count as running into it, and zeroes `dx` or `dy`. Moving across and then up or down is exact for
upright boxes, and a fast body cannot pass through a thin floor, because each move is swept.

An entity with both a `Velocity` and a `KinematicBody` is an error: the body's speed is its own
`dx` and `dy`.

## Running and jumping

The engine adds no gravity, so the system that reads the input adds it to `dy`. It runs in
`FIXED_UPDATE`, before the engine's move in the same step, and reads the jump there with
`isJustPressed`, which inside `FIXED_UPDATE` counts steps, so each press reaches exactly one
step. The contact flags it reads are the ones the previous step's move left.

<!-- snippet: run-and-jump -->
```kotlin
var coyote = 0f
var jumpBuffer = 0f
addSystem(Phase.FIXED_UPDATE) { app ->
    val body = app.world.get(hero, KinematicBody)
    val keyboard = app.input.keyboard
    val dt = app.time.deltaSeconds
    body.dx = keyboard.axis(Scancode.A, Scancode.D) * 320f
    // Coyote time: a jump still works a tenth of a second after running off an edge.
    coyote = if (body.isOnFloor) 0.1f else coyote - dt
    // Jump buffer: a press a tenth of a second before landing still jumps when it lands.
    val pressed = keyboard.isJustPressed(Scancode.SPACE) || app.input.gamepads.isJustPressed(GamepadButton.SOUTH)
    jumpBuffer = if (pressed) 0.1f else jumpBuffer - dt
    if (jumpBuffer > 0f && coyote > 0f) {
        body.dy = -700f
        coyote = 0f
        jumpBuffer = 0f
    }
    // Letting go of the button early makes a short hop.
    val gravity = if (body.dy < 0f && !keyboard.isDown(Scancode.SPACE)) 5400f else 1800f
    body.dy = minOf(body.dy + gravity * dt, 900f)
}
```

Coyote time lets a jump start a few steps after the hero ran off an edge, and the jump buffer
remembers a press made just before landing. Both are two timers in the system. The short hop is
stronger gravity while the hero rises with the button up. Change the numbers until it feels right.

## Contacts

After every move the body says what it touched:

| Property | Meaning |
|---|---|
| `isOnFloor` | The move ended on a floor: it landed moving down, or the floor check found one. |
| `isOnWall` | The move ran into a wall moving across. |
| `wallNormalX` | -1 for a wall on the right, 1 for one on the left, 0 with no wall: the way to push off for a wall jump. |
| `isOnCeiling` | The move ran into a ceiling moving up. |
| `floorOrNull` | The solid the body stands on, or `null` in the air. |

A body that stood on a floor and is not moving up looks `Collisions.FLOOR_SNAP_DISTANCE`, 2 units,
further down after its move and lands on a floor it finds there. Walking down a small step then
keeps `isOnFloor` true instead of falling for one step, which would let coyote time start.

To land with a sound or a puff of dust, keep the last step's `isOnFloor` in your system and act
when it turns from `false` to `true`.

## Dropping through a ledge

`dropThrough()` makes a body standing on one-way ledges fall through them: from the next move it
ignores every one-way solid at the height of its feet, until it has fallen below them, landed on
something else or moved up. Solids that are not one-way still stop it.

<!-- snippet: drop -->
```kotlin
addSystem(Phase.FIXED_UPDATE) { app ->
    val body = app.world.get(hero, KinematicBody)
    // Down while standing on a ledge: fall through it.
    if (body.isOnFloor && app.input.keyboard.isJustPressed(Scancode.S)) body.dropThrough()
}
```

## Sweeps

`app.collisions.sweepBox` moves a box along a straight line against the solids, without moving
anything, and returns where it first touches one as a `SweepHit`, or `null` if the way is clear.
The hit holds the solid, the `fraction` of the move the box gets through, from 0 to 1, and the
normal of the face it touched: `normalY` is -1 for a floor, because y points down.
`sweepBoxTo(destination, …)` adds every solid along the move to a collection, in the order the box
reaches them. A sweep with `dx` and `dy` both 0 returns the solid the box overlaps where it is.
Sweeps follow the one-way rule and the `mask` parameter as bodies do.

Unlike the overlap queries, which read the positions of the last detection, sweeps read the
solids as they are now, so they work in `FIXED_UPDATE`. An enemy that turns at walls and at the
edge of its roof needs one sweep a step:

<!-- snippet: walker -->
```kotlin
/** An enemy that walks back and forth along a roof without falling off it. */
class Walker(var direction: Float = 1f) {
    override fun toString(): String = "Walker(direction=$direction)"

    companion object : ComponentType<Walker>("Walker")
}
```

<!-- snippet: patrol -->
```kotlin
commands.spawn {
    add(Transform, Transform(x = 1400f, y = 344f - Collisions.CONTACT_GAP))
    add(Sprite, Sprite(assets.whitePixel, width = 24f, height = 32f, color = Color.RED))
    add(Collider, Collider(Shape.Box(width = 24f, height = 32f)))
    add(KinematicBody, KinematicBody())
    add(Walker, Walker())
}
addSystem(Phase.FIXED_UPDATE) { app ->
    app.world.forEach(Walker, KinematicBody, Transform) { _, walker, body, transform ->
        // A thin box just ahead of the feet, swept a little down, finds the floor there.
        val ahead = transform.x + walker.direction * 16f
        val floorAhead = app.collisions.sweepBox(x = ahead, y = transform.y, width = 4f, height = 32f, dx = 0f, dy = 8f)
        if (body.isOnWall || (body.isOnFloor && floorAhead == null)) walker.direction = -walker.direction
        body.dx = walker.direction * 80f
        body.dy += 1800f * app.time.deltaSeconds
    }
}
```

An autopilot or a smarter enemy can plan with sweeps too, such as finding where a fall from a
point ends:

<!-- snippet: landing -->
```kotlin
/**
 * Returns the y at which a hero 24 by 40 units centered on ([x], [y]) would come to rest falling
 * straight down, or `null` if nothing is below it within 2000 units.
 */
fun App.landingYOrNull(x: Float, y: Float): Float? {
    val hit = collisions.sweepBox(x = x, y = y, width = 24f, height = 40f, dx = 0f, dy = 2000f) ?: return null
    return y + 2000f * hit.fraction - Collisions.CONTACT_GAP
}
```

To follow a jump, step its arc ahead in short sweeps, one per fixed step, with the same gravity
your system adds.

## Limits

- Bodies and solids are upright boxes: a rotation is ignored and a circle collider is an error.
- Solids do not move bodies: there are no moving platforms, and a solid moved onto a body lets
  the body pass through it.
- Bodies do not stop each other. Use `Overlap` events between them, as for any colliders.
- A body that starts a move inside a solid moves through that solid, so it is never stuck, but
  place bodies outside solids.
- A scene frozen under a pushed scene keeps its bodies still, and its solids stop nothing.

## See also

- [Solid](../klyph/klyph.collision/-solid/index.html) and
  [Passable](../klyph/klyph.collision/-passable/index.html)
- [KinematicBody](../klyph/klyph.collision/-kinematic-body/index.html)
- [Collisions](../klyph/klyph.collision/-collisions/index.html) and
  [SweepHit](../klyph/klyph.collision/-sweep-hit/index.html)
- [Input](input.md) for `isJustPressed` in `FIXED_UPDATE`
- Next: [Text, canvas and overlay](text-canvas-and-overlay.md)
