package klyph.physics

import klyph.math.Vec2
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rigid bodies meeting particles and soft bodies, both sides moving: crates on blobs, blobs at crates, ropes tied to crates. */
class CouplingTest {
    private val dt = TestBodies.DT
    private val tile = TestBodies.TILE

    /** A room 40 tiles wide and 30 high with a floor along its bottom two rows: its top at y = 448. */
    private fun room(material: Material = Material.DEFAULT) = PhysicsWorld().apply {
        solids = SolidGrid(40, 30, tile).apply {
            for (c in 0 until columns) for (r in 28 until 30) fill(c, r, material = material)
            for (r in 0 until 30) {
                fill(0, r)
                fill(columns - 1, r)
            }
        }
    }

    private val floor = 28 * 16f
    private val dome = SoftBodyShape.semiDisc(radius = 16f, arcSegments = 16, baseSegments = 8, coreMass = 34f)

    /** A blob wider than a crate, so one can sit on it: on a narrower one it would roll off, as off a ball. */
    private val wide = SoftBodyShape.semiDisc(radius = 26f, arcSegments = 22, baseSegments = 12, coreMass = 34f)

    /** The highest point of soft body [s]'s outline: the smallest y. */
    private fun topOf(world: PhysicsWorld, s: SoftBody): Float {
        var top = Float.MAX_VALUE
        val ring = world.softBodies.ringOf(s)
        for (i in 0 until ring.size) top = minOf(top, ring.yAt(i))
        return top
    }

    @Test
    fun aCrateRestingOnABlobSinksIntoItAndIsHeldUp() {
        val world = room()
        val blob = world.softBody(wide, 320f, floor)
        repeat(60) { world.step(dt) }
        val restingTop = topOf(world, blob)
        val crate = world.rigidBody(Shape.box(28f, 28f), 320f, restingTop - 14f - 1f)
        val bodies = world.rigidBodies
        repeat(240) { world.step(dt) }
        // it settles, then stays put: the blob shivers a little under it, but no more than that
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        repeat(60) {
            world.step(dt)
            low = minOf(low, bodies.yOf(crate))
            high = maxOf(high, bodies.yOf(crate))
        }
        assertTrue(high - low < 0.5f, "and rests there: it moved between $low and $high")
        val bottom = bodies.yOf(crate) + 14f
        assertTrue(bottom > restingTop + 1f, "it sinks in: its bottom at $bottom, the blob's top was at $restingTop")
        assertTrue(bottom < floor - 4f, "but is held up off the floor: its bottom at $bottom")
        assertEquals(320f, bodies.xOf(crate), 4f, "on top, not slid off")
        assertTrue(world.softBodies.isFinite(blob))
        // nothing of the blob is inside the crate
        val ps = world.particles
        for ((i, p) in world.softBodies.particlesOf(blob).withIndex()) {
            assertFalse(bodies.shapesOf(crate)[0].contains(ps.xOf(p) - bodies.xOf(crate), ps.yOf(p) - bodies.yOf(crate)), "particle $i of the blob is inside the crate")
        }
    }

    @Test
    fun aBlobThrownAtATowerOfCratesKnocksItOver() {
        val world = room()
        val bodies = world.rigidBodies
        val tower = List(4) { world.rigidBody(Shape.box(20f, 20f), 400f, floor - 10f - it * 20f) }
        val blob = world.softBody(dome, 200f, floor - 50f)
        for (p in world.softBodies.particlesOf(blob)) world.setVelocity(p, 700f, -60f)
        repeat(120) { world.step(dt) }
        val top = tower.last()
        assertTrue(abs(bodies.xOf(top) - 400f) > 20f || abs(bodies.angleOf(top)) > 0.5f, "the top crate was knocked off: at ${bodies.xOf(top)}, turned ${bodies.angleOf(top)}")
        assertTrue(world.softBodies.isFinite(blob))
    }

    @Test
    fun aBlobDroppedOnACrateRestsOnItWithoutGoingIn() {
        val world = room()
        val crate = world.rigidBody(Shape.box(60f, 20f), 320f, floor - 10f)
        val blob = world.softBody(dome, 320f, floor - 20f - 40f)
        repeat(240) { world.step(dt) }
        val ps = world.particles
        val bodies = world.rigidBodies
        for ((i, p) in world.softBodies.particlesOf(blob).withIndex()) {
            assertTrue(ps.yOf(p) < floor - 20f + 1f, "particle $i of the blob rests on the crate: at ${ps.yOf(p)}")
        }
        assertEquals(floor - 10f, bodies.yOf(crate), 0.5f, "and the crate on the floor")
        assertTrue(abs(world.softBodies.meanVelocityYOf(blob)) < 5f, "at rest")
    }

    @Test
    fun aBlobTouchingACrateStandsOnIt() {
        val world = room()
        world.rigidBody(Shape.box(60f, 20f), 320f, floor - 10f, kind = BodyKind.STATIC)
        val blob = world.softBody(dome, 320f, floor - 20f - 5f)
        repeat(120) { world.step(dt) }
        val ps = world.particles
        var standing = false
        // the ring: every particle but the core, which comes last
        for (p in world.softBodies.particlesOf(blob).dropLast(1)) if (world.events.isOnFloor(p)) standing = true
        assertTrue(standing, "a push from below counts as a floor, so it could jump off the crate")
    }

    @Test
    fun aBallThrownAtACratePushesItAlong() {
        val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO, damping = 0f))
        val bodies = world.rigidBodies
        val crate = world.rigidBody(Shape.box(20f, 20f), 300f, 200f)
        val mass = 1f / bodies.inverseMassOf(crate)
        val ball = world.particles.add(200f, 200f, mass = mass, radius = 4f, collision = ParticleCollision.EVERYTHING)
        world.setVelocity(ball, 300f, 0f)
        repeat(60) { world.step(dt) }
        assertTrue(bodies.velocityXOf(crate) > 100f, "the crate was pushed: ${bodies.velocityXOf(crate)}")
        assertTrue(world.velocityXOf(ball) < bodies.velocityXOf(crate) + 1f, "and the ball stays behind it")
        // as heavy as each other, they share what the ball had
        val momentum = mass * bodies.velocityXOf(crate) + mass * world.velocityXOf(ball)
        assertEquals(300f * mass, momentum, 300f * mass * 0.05f, "no speed made or lost along the way")
        assertEquals(0f, bodies.angularVelocityOf(crate), 0.05f, "hit square on its middle, it doesn't turn")
    }

    @Test
    fun aBallHittingACrateOffItsMiddleTurnsIt() {
        val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO, damping = 0f))
        val bodies = world.rigidBodies
        val crate = world.rigidBody(Shape.box(20f, 40f), 300f, 200f)
        val ball = world.particles.add(200f, 185f, mass = 20f, radius = 2f, collision = ParticleCollision.EVERYTHING)
        world.setVelocity(ball, 300f, 0f)
        repeat(60) { world.step(dt) }
        assertTrue(bodies.angularVelocityOf(crate) > 0.2f, "hit above its middle, it turns clockwise: ${bodies.angularVelocityOf(crate)}")
    }

    @Test
    fun grainsPouredOnACrateHeapUpOnIt() {
        val world = room()
        world.rigidBody(Shape.box(100f, 20f), 320f, floor - 10f, kind = BodyKind.STATIC)
        val grains = List(60) { world.particles.add(300f + (it % 10) * 4f, 300f - (it / 10) * 4f, radius = 2f, collision = ParticleCollision.EVERYTHING) }
        repeat(300) { world.step(dt) }
        val ps = world.particles
        var on = 0
        for (g in grains) {
            assertTrue(ps.yOf(g) < floor - 20f + 0.5f || ps.xOf(g) !in 270f..370f, "grain $g is in the crate at (${ps.xOf(g)}, ${ps.yOf(g)})")
            if (ps.yOf(g) < floor - 20f) on++
        }
        assertTrue(on > 40, "most stay on top: $on")
    }

    @Test
    fun aCrateCornerFirstCantPokeIntoABlob() {
        val world = room()
        val blob = world.softBody(dome, 320f, floor)
        repeat(60) { world.step(dt) }
        // a triangle point down, dropped from high up
        val wedge = world.rigidBody(Shape.polygon(listOf(Vec2(-12f, -8f), Vec2(12f, -8f), Vec2(0f, 14f))), 320f, topOf(world, blob) - 40f)
        val bodies = world.rigidBodies
        val shape = bodies.shapesOf(wedge)[0]
        repeat(240) {
            world.step(dt)
            for (k in 0 until shape.vertexCount) {
                val x = bodies.worldXOf(wedge, shape.vertexXAt(k), shape.vertexYAt(k))
                val y = bodies.worldYOf(wedge, shape.vertexXAt(k), shape.vertexYAt(k))
                val inside = Outline.contains(world.softBodies.ringOf(blob), x, y)
                if (inside) {
                    val out = FloatArray(2)
                    val depth = sqrt(Outline.nearestPoint(world.softBodies.ringOf(blob), x, y, out))
                    assertTrue(depth < 1.5f, "corner $k is $depth into the blob at step $it")
                }
            }
        }
    }

    @Test
    fun rubberHoldsABlobOnATiltedCrateAndIceLetsItSlide() {
        for ((material, holds) in listOf(Material.RUBBER to true, Material.ICE to false)) {
            val world = PhysicsWorld()
            val tilt = 0.3f
            world.rigidBody(Shape.box(300f, 20f), 320f, 300f, angle = tilt, kind = BodyKind.STATIC, material = material)
            // resting on the tilted top: its base 10 above the plank's middle, along the plank's normal
            val x = 320f + 10f * kotlin.math.sin(tilt)
            val y = 300f - 10f * kotlin.math.cos(tilt) - 1f
            val blob = world.softBody(dome, x, y, material = material)
            repeat(30) { world.step(dt) }
            val startX = world.softBodies.centerXOf(blob)
            repeat(120) { world.step(dt) }
            val moved = world.softBodies.centerXOf(blob) - startX
            if (holds) assertTrue(abs(moved) < 4f, "rubber holds: moved $moved") else assertTrue(moved > 30f, "ice slides: moved $moved")
        }
    }

    @Test
    fun aCrateHangsFromARopeTiedToIt() {
        val world = PhysicsWorld()
        val ps = world.particles
        // a rope of 10 links from a fixed point, its end tied to a crate's top-left corner
        val top = ps.add(320f, 100f, mass = 1f)
        world.constraints.pin(top)
        var last = top
        for (k in 1..10) {
            val p = ps.add(320f, 100f + k * 8f, mass = 1f)
            world.constraints.rope(last, p)
            last = p
        }
        val crate = world.rigidBody(Shape.box(20f, 20f), 330f, 180f + 10f)
        val tie = world.attach(last, crate)
        repeat(600) { world.step(dt) }
        val bodies = world.rigidBodies
        assertEquals(world.attachments.pointXOf(tie), ps.xOf(last), 0.5f, "the rope's end stays on the crate")
        assertEquals(world.attachments.pointYOf(tie), ps.yOf(last), 0.5f)
        // it hangs with its centre of mass right below the rope's end
        assertEquals(ps.xOf(last), bodies.xOf(crate), 1f, "hanging straight down from the tie")
        assertTrue(bodies.yOf(crate) > ps.yOf(last), "below it")
        assertTrue(abs(bodies.angularVelocityOf(crate)) < 0.1f && abs(bodies.velocityXOf(crate)) < 2f, "and still")
    }

    @Test
    fun aCrateTiedToTheRopeOfAStaticPointSwingsAndADetachedOneFalls() {
        val world = PhysicsWorld()
        val anchor = world.rigidBody(Shape.box(10f, 10f), 320f, 100f, kind = BodyKind.STATIC)
        val p = world.particles.add(320f, 105f, mass = 1f)
        val crate = world.rigidBody(Shape.box(20f, 20f), 320f, 140f)
        val q = world.particles.add(320f, 130f, mass = 1f)
        world.constraints.rope(p, q)
        world.attach(p, anchor)
        val tie = world.attach(q, crate)
        repeat(120) { world.step(dt) }
        val bodies = world.rigidBodies
        assertEquals(140f, bodies.yOf(crate), 1.5f, "hanging from the anchor")
        world.attachments.remove(tie)
        assertFalse(world.attachments.isAlive(tie))
        repeat(30) { world.step(dt) }
        assertTrue(bodies.yOf(crate) > 160f, "let go, it falls: ${bodies.yOf(crate)}")
    }

    @Test
    fun grainsAndBlobsMeetTheShapesOfABodyFarFromItsMiddle() {
        // two shelves, far apart, made one static body: its middle is between them, over nothing
        val world = room()
        val shelves = world.rigidBody(
            listOf(Shape.box(60f, 10f, x = -150f), Shape.box(60f, 10f, x = 150f)),
            320f,
            300f,
            kind = BodyKind.STATIC,
        )
        val grain = world.particles.add(470f, 250f, radius = 2f, collision = ParticleCollision.EVERYTHING)
        val blob = world.softBody(dome, 170f, 250f)
        val missed = world.particles.add(320f, 250f, radius = 2f, collision = ParticleCollision.EVERYTHING)
        repeat(120) { world.step(dt) }
        assertEquals(300f - 5f - 2f, world.particles.yOf(grain), 0.5f, "the grain rests on the right shelf")
        assertTrue(topOf(world, blob) < 295f, "the blob rests on the left one: its top is at ${topOf(world, blob)}")
        assertTrue(world.particles.yOf(missed) > 400f, "between them, a grain falls through to the floor")
        assertTrue(world.rigidBodies.isAlive(shelves))
    }
}
