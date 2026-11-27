<!-- Snippets: samples/src/nativeTest/kotlin/samples/guide/Physics.kt -->
# Physics

The `klyph-physics` module adds a 2D physics engine to a klyph game: crates that stack and
tumble, soft bodies that squash, ropes, chains and cloth that hang and tear, sand, joints and
motors. Everything is solved with XPBD in substeps, in one `PhysicsWorld` that the app steps
in every fixed step and that allocates nothing while it runs. This page covers adding it to a
game, the kinds of bodies, the level they collide with, and how a game reads what happened.

The [Collisions and particles](collisions-and-particles.md) and [Platformers](platformers.md)
pages cover klyph's own overlaps and kinematic bodies, which need no physics: use those for a
character that runs and jumps by its own rules, and this module for things that fall, push
and roll. The two work side by side.

The module is a separate dependency, next to `klyph`:

```kotlin
kotlin {
    sourceSets.nativeMain.dependencies {
        implementation("com.eventslooped.klyph:klyph:1.2.0")
        implementation("com.eventslooped.klyph:klyph-physics:1.2.0")
    }
}
```

The snippets on this page are code for the setup block of `runApp`.

## The world

`addPhysics` makes a `Physics` that holds a `PhysicsWorld` and steps it in `Phase.FIXED_UPDATE`
by the app's fixed step. `app.physics` finds it again from any system. A `PhysicsConfig` sets
gravity, how many substeps and solver passes a step takes, damping, and how soon still rigid
bodies fall asleep; every value has a default that suits a game at 60 steps a second.

<!-- snippet: add-physics -->
```kotlin
val physics = addPhysics(PhysicsWorld(PhysicsConfig(substeps = 4, timeToSleep = Duration.INFINITE)))
val world = physics.world
```

Systems run in the order they are added. Add the fixed-step systems that push, steer or spawn
things before `addPhysics`, and those that read what happened, such as contacts, after it.

The world's stores hold everything in it: `world.particles`, `world.rigidBodies`,
`world.softBodies`, `world.constraints`, `world.joints` and `world.attachments`. Adding a thing
returns a value-class handle such as `RigidBody` or `Particle`, and the store reads and changes
it: `rigidBodies.xOf(crate)`, `rigidBodies.setVelocity(crate, vx, vy)`. A removed thing's slot
is reused, so check `isAlive` before using a handle kept across removals.

## Rigid bodies

A rigid body keeps its shape and turns. It is made of a `Shape` and placed at a point; its
mass and how hard it is to turn come from the shape's area and a density. A `PhysicsBody`
component links an entity to a body: after every step the entity's `Transform` is moved to the
body and turned with it. Give the entity `Interpolated` too and it moves smoothly at any frame
rate.

<!-- snippet: crates -->
```kotlin
val world = physics.world
world.rigidBody(Shape.box(width = 1200f, height = 40f), x = 640f, y = 700f, kind = BodyKind.STATIC)
repeat(5) { i ->
    val crate = world.rigidBody(Shape.box(40f, 40f), x = 640f, y = 600f - i * 45f)
    commands.spawn {
        add(Transform, Transform())
        add(Interpolated, Interpolated)
        add(Sprite, Sprite(assets.whitePixel, width = 40f, height = 40f, color = Color(0.9f, 0.6f, 0.3f)))
        // The entity's Transform follows the crate, its angle too, after every step.
        add(PhysicsBody, PhysicsBody.of(crate))
    }
}
```

`BodyKind.STATIC` bodies never move, `DYNAMIC` ones fall and are pushed, and `KINEMATIC` ones
move only as they are told, with their velocity or `moveKinematic`, and push dynamic bodies
without being pushed back: lifts, moving platforms and paddles.

`Shape.circle`, `Shape.box`, `Shape.capsule` and `Shape.polygon` make convex shapes, and a body
made of several is a compound body. A `Material` says how much a surface grips and bounces;
`Material.ICE` and `Material.RUBBER` are ready made.

<!-- snippet: shapes -->
```kotlin
val world = physics.world
val wheel = world.rigidBody(Shape.circle(radius = 12f), x = 100f, y = 0f, material = Material.RUBBER)
val pill = world.rigidBody(Shape.capsule(length = 30f, radius = 6f), x = 200f, y = 0f)
// A hammer: two boxes as one body, heavier at the head.
val hammer = world.rigidBodies.add(
    listOf(Shape.box(6f, 40f, y = 10f), Shape.box(24f, 10f, y = -15f)),
    x = 300f,
    y = 0f,
)
world.rigidBodies.setVelocity(hammer, vx = 0f, vy = -400f)
world.rigidBodies.setAngularVelocity(wheel, 6f)
```

The physics `Shape` is `klyph.physics.Shape`; the collider shape `klyph.collision.Shape` is a
different type, so import the one a file uses.

## The level

A `SolidGrid` of tiles is the level that everything collides with: boxes, slopes rising or
falling, and the top half of a tile. Each tile has `SolidTags`, which a body standing on it
reports, and a `Material`. `Solid.moving` makes a solid that is not on the grid, such as a
platform, moved by changing its `top` and `bottom`.

<!-- snippet: level -->
```kotlin
val grid = SolidGrid(columns = 80, rows = 45, tileSize = 16f)
for (column in 0 until 80) grid.fill(column, 44)
// A slope up to a ledge of ice.
grid.fill(20, 43, shape = SolidShape.RISING)
for (column in 21 until 30) grid.fill(column, 43, tags = LevelTags.ICE, material = Material.ICE)
physics.world.solids = grid
```

## Soft bodies

A soft body is a ring of particles around a heavy core, held by springy edges, spokes and an
area constraint: it squashes when it lands and wobbles back. `SoftBodyShape.semiDisc`,
`circle` and `box` make the rest shapes, and a `SoftBodyTuning` says how soft each part is.
A soft body reports whether it stands on a floor and the tags of what it stands on.

<!-- snippet: slime -->
```kotlin
val world = physics.world
val dome = SoftBodyShape.semiDisc(radius = 13f, arcSegments = 14, baseSegments = 8, coreMass = 34f, heightScale = 1.3f)
val slime = world.softBody(dome, centerX = 400f, baseY = 600f)
addSystem(Phase.FIXED_UPDATE) { app ->
    val keys = app.input.keyboard
    if (keys.isJustPressed(Scancode.SPACE) && world.softBodies.isOnFloor(slime)) {
        world.softBodies.addVelocity(slime, vx = 0f, vy = -600f)
    }
    if (world.softBodies.floorTagsOf(slime).intersects(LevelTags.ICE)) {
        world.softBodies.addVelocity(slime, vx = 5f, vy = 0f)
    }
}
```

## Ropes, chains and cloth

`rope`, `chain` and `cloth` build strands and sheets of particles held by the world's
constraints, and return a `Strand` or a `Cloth` that lists them. A rope goes slack when pushed;
a chain's links are rods. `attach` ties a particle to a point on a rigid body, so a rope can
hold a lamp or drag a crate. A link stretched past `tearAt` times its length breaks.

<!-- snippet: ropes -->
```kotlin
val world = physics.world
val lamp = world.rigidBody(Shape.circle(10f), x = 300f, y = 200f)
// A rope from a fixed point in the ceiling down to the lamp, tied to its top.
val rope = world.rope(fromX = 300f, fromY = 40f, toX = 300f, toY = 190f, segments = 15)
world.attach(rope.last, lamp)
// A curtain that tears when pulled too hard.
world.cloth(left = 500f, top = 40f, columns = 20, rows = 14, spacing = 8f, tearAt = 1.6f)
```

The constraints these build can also be made one at a time: `constraints.distance`, `rope`,
`pin`, `area` and `bend`, each with a compliance that says how far it gives.

## Joints and motors

`world.joints` holds bodies together: a `hinge` lets two bodies turn about a shared point, a
`slider` lets one slide along an axis of the other, a `weld` holds them fixed and can break,
and a `distance` joint keeps two points a length apart. `JointEnd.WORLD` as the second end holds
a body to a fixed point. Hinges and sliders take limits and a motor.

<!-- snippet: joints -->
```kotlin
val world = physics.world
val body = world.rigidBody(Shape.box(80f, 20f), x = 400f, y = 300f)
val front = world.rigidBody(Shape.circle(14f), x = 430f, y = 320f)
val back = world.rigidBody(Shape.circle(14f), x = 370f, y = 320f)
world.joints.hinge(front, body, x = 430f, y = 320f)
val drive = world.joints.hinge(back, body, x = 370f, y = 320f)
// A gate on a hinge to the world, which may open a quarter turn.
val gate = world.rigidBody(Shape.box(8f, 60f), x = 600f, y = 270f)
val hinge = world.joints.hinge(gate, JointEnd.WORLD, x = 600f, y = 240f)
world.joints.setLimits(hinge, lower = -1.57f, upper = 0f)
addSystem(Phase.FIXED_UPDATE) { app ->
    val speed = if (app.input.keyboard.isDown(Scancode.RIGHT)) 20f else 0f
    world.joints.setMotor(drive, speed = speed, maxTorque = 5_000_000f)
}
```

## Contacts, sensors and events

`world.events` reports what the last step did. `contactsBegun` and `contactsEnded` list the
pairs that started and stopped touching, `sensorsEntered` and `sensorsExited` what came into
and left a sensor, and `brokenJointAt` the joints that broke. A body with
`ContactResponse.SENSE` is a sensor: it pushes nothing and only notices. Each `Touch` names its
two sides as `TouchSide` values, which compare with `TouchSide.of(handle)` and
`TouchSide.SOLIDS`.

<!-- snippet: events -->
```kotlin
val world = physics.world
val coin = world.rigidBody(Shape.circle(6f), x = 500f, y = 600f, kind = BodyKind.STATIC)
world.rigidBodies.setResponse(coin, ContactResponse.SENSE)
val ball = world.rigidBody(Shape.circle(10f), x = 500f, y = 400f)
// After addPhysics, so it reads what the step just did.
addSystem(Phase.FIXED_UPDATE) { _ ->
    if (world.events.sensorsEntered.has(TouchSide.of(coin), TouchSide.of(ball))) {
        world.rigidBodies.remove(coin)
    }
    for (i in 0 until world.events.contactsBegun.size) {
        val touch = world.events.contactsBegun[i]
        if (touch.involves(TouchSide.SOLIDS)) println("Something landed: $touch")
    }
}
```

## Layers and fast things

Physics filters collisions with klyph's `CollisionLayers`: everything is on some layers (any of
the 32) and has a mask of the layers it collides with. Two things collide only when each one's mask
holds a layer of the other, so either can refuse. The level has no layer: a mask covers only other
bodies and particles, and `setSolidCollision(thing, SolidCollision.PASS)` lets one thing fall
through the level. A particle meets only the level unless it is added with
`BodyCollision.COLLIDE`, which sand needs.
`Motion.BULLET` sweeps a fast body or particle every substep, so it never passes through a
wall, however thin.

<!-- snippet: layers -->
```kotlin
val world = physics.world
val ghost = world.rigidBody(Shape.circle(8f), x = 100f, y = 100f)
// On layer 3, colliding with no other body. The level still holds it up.
world.rigidBodies.setLayers(ghost, layers = CollisionLayers.of(3), mask = CollisionLayers.NONE)
// Sand that falls through other sand would be no sand; grains collide with everything.
world.particles.add(x = 120f, y = 100f, radius = 2f, bodyCollision = BodyCollision.COLLIDE)
val shot = world.rigidBody(Shape.circle(2f), x = 0f, y = 100f)
world.rigidBodies.setMotion(shot, Motion.BULLET)
world.rigidBodies.setVelocity(shot, vx = 6_000f, vy = 0f)
```

## Queries

`raycast` finds the first thing along a line, and `queryPoint` and `queryBox` everything at a
point or in a box, filtered by `QueryTargets`. They write into a `RayHit` or an `Overlaps` the
game makes once and keeps, so a query allocates nothing.

<!-- snippet: queries -->
```kotlin
val world = physics.world
// Made once and kept: the queries write into them and allocate nothing.
val hit = RayHit()
val found = Overlaps()
addSystem(Phase.FIXED_UPDATE) { app ->
    val mouse = app.input.mouse.worldPosition
    if (world.raycast(fromX = 100f, fromY = 100f, toX = mouse.x, toY = mouse.y, hit = hit)) {
        println("The laser stops at (${hit.x}, ${hit.y}) on ${hit.kind}")
    }
    val under = world.queryPoint(mouse.x, mouse.y, found, targets = QueryTargets.RIGID_BODIES + QueryTargets.SOFT_BODIES)
    if (under > 0) println("Under the mouse: $found")
}
```

## Seeing it

`DebugDraw` draws what the physics sees on a klyph `Canvas`: soft bodies filled from their
outlines, rigid bodies, the level, and, a level of `DebugView` at a time, every particle, every
constraint coloured by its stress, and every contact with its normal.

<!-- snippet: debug -->
```kotlin
val debug = DebugDraw(assets.whitePixel, assets.defaultFont)
addSystem(Phase.UPDATE) { app ->
    if (app.input.keyboard.isJustPressed(Scancode.F1)) debug.view = debug.view.next()
}
addSystem(Phase.RENDER) { app ->
    val world = app.physics.world
    world.solids?.let { debug.solids(app.canvas, it, Rect(0f, 0f, 1280f, 720f)) }
    world.rigidBodies.forEach { body -> debug.rigidBody(app.canvas, world, body, Color(0.9f, 0.6f, 0.3f)) }
    world.softBodies.forEach { body -> debug.softBody(app.canvas, world, body, Color(0.5f, 0.8f, 0.4f)) }
    debug.overlay(app.canvas, world)
}
```

The `games/physics` demo in the repository has eighteen scenes of everything on this page,
with these views on keys; run it with `./gradlew :games:physics:run`.

## Limits

- One world per `Physics`, stepped on the main thread. A step of 1 000 crates, 5 000 grains and
  50 soft bodies takes about 19 ms on an Apple M4 Max in a release build.
- Shapes are convex. A concave body is a compound of convex shapes.
- The world is deterministic: the same calls in the same order give the same result, to the
  bit, on the same build. It is not deterministic across platforms or compilers.
- Positions are in world units with y growing down, as everywhere in klyph, and angles in
  radians, clockwise on screen.

## How it works

The pages in [docs/physics](https://github.com/inemtsev/klyph/blob/main/docs/physics/README.md)
explain the engine one part at a time, with pictures: Verlet integration and substeps, XPBD and
compliance, soft bodies, contacts and friction, the broadphase, SAT, stacking, joints, sleeping,
tunnelling, and making it fast.

## See also

- [PhysicsWorld](../klyph-physics/klyph.physics/-physics-world/index.html) and
  [PhysicsConfig](../klyph-physics/klyph.physics/-physics-config/index.html)
- [RigidBodies](../klyph-physics/klyph.physics/-rigid-bodies/index.html),
  [SoftBodies](../klyph-physics/klyph.physics/-soft-bodies/index.html) and
  [Joints](../klyph-physics/klyph.physics/-joints/index.html)
- [Events](../klyph-physics/klyph.physics/-events/index.html)
- [Collisions and particles](collisions-and-particles.md) for klyph's own colliders and layers
- [Performance](performance.md) for the cost of a frame
- Next: [Scrolling worlds](scrolling.md)
