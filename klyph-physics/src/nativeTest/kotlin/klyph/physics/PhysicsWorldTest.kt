package klyph.physics

import klyph.math.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration

class PhysicsWorldTest {
    private val dt = 1f / 60f

    @Test
    fun aFallingParticleMatchesTheAnalyticResult() {
        val g = 1500f
        val world = PhysicsWorld(PhysicsConfig(gravity = Vec2(0f, g), damping = 0f, maxStep = 1000f))
        val p = world.particles.add(0f, 0f)
        repeat(60) { world.step(dt) }
        val t = 1f
        val h = dt / world.config.substeps
        // Verlet from rest gets the velocity exactly and is half a substep ahead on position.
        assertEquals(g * t, world.velocityYOf(p), g * t * 0.001f)
        assertEquals(g * t * t / 2f, world.particles.yOf(p), g * h * t / 2f + 0.5f)
        assertEquals(0f, world.particles.xOf(p))
    }

    @Test
    fun dampingLosesTheSameShareOfSpeedPerSecondWhateverTheSubsteps() {
        fun speedAfterOneSecond(substeps: Int): Float {
            val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO, substeps = substeps, damping = 1f, maxStep = 1000f))
            val p = world.particles.add(0f, 0f)
            world.step(dt)
            world.setVelocity(p, 100f, 0f)
            repeat(60) { world.step(dt) }
            return world.velocityXOf(p)
        }
        val few = speedAfterOneSecond(2)
        val many = speedAfterOneSecond(16)
        assertEquals(100f * kotlin.math.exp(-1f), few, 0.5f)
        assertEquals(few, many, 0.5f)
    }

    @Test
    fun aParticleNeverMovesFartherThanMaxStepInOneSubstep() {
        val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO, damping = 0f, maxStep = 4f))
        val p = world.particles.add(0f, 0f)
        world.step(dt)
        world.setVelocity(p, 100_000f, 0f)
        world.step(dt)
        assertEquals(4f * world.config.substeps, world.particles.xOf(p), 0.001f)
    }

    @Test
    fun staticAndRemovedParticlesStayPut() {
        val world = PhysicsWorld()
        val pinned = world.particles.add(5f, 5f, mass = Particles.STATIC)
        val gone = world.particles.add(7f, 7f)
        world.particles.remove(gone)
        repeat(10) { world.step(dt) }
        assertEquals(5f, world.particles.yOf(pinned))
        assertEquals(7f, world.particles.yOf(gone))
    }

    @Test
    fun twoWorldsFedTheSameInputGiveIdenticalResults() {
        fun run(): FloatArray {
            val world = PhysicsWorld()
            val random = kotlin.random.Random(42)
            repeat(200) { step ->
                if (step % 3 == 0) {
                    val p = world.particles.add(random.nextFloat() * 100f, random.nextFloat() * 100f, mass = 0.5f + random.nextFloat())
                    world.setVelocity(p, random.nextFloat() * 400f - 200f, -random.nextFloat() * 300f)
                }
                if (step % 7 == 0 && world.particles.size > 0) world.particles.remove(Particle(random.nextInt(world.particles.end)))
                world.step(dt)
            }
            val ps = world.particles
            return FloatArray(ps.end * 2) { i -> if (i % 2 == 0) ps.xOf(Particle(i / 2)) else ps.yOf(Particle(i / 2)) }
        }
        val a = run()
        val b = run()
        assertTrue(a.contentEquals(b), "same input, same bits")
    }

    @Test
    fun aStepNeedsATimeThatMakesSense() {
        val world = PhysicsWorld()
        assertFailsWith<IllegalArgumentException> { world.step(0f) }
        assertFailsWith<IllegalArgumentException> { world.step(Float.NaN) }
    }

    @Test
    fun aStepGivenAsADurationMovesEverythingExactlyAsTheSameStepInSeconds() {
        fun scene(): PhysicsWorld {
            val world = PhysicsWorld()
            world.solids = TestBodies.grid().apply { for (c in 0 until columns) fill(c, 9) }
            world.softBody(TestBodies.dome(), centerX = 80f, baseY = 100f)
            val p = world.particles.add(40f, 20f, radius = 2f, bodyCollision = BodyCollision.COLLIDE)
            world.setVelocity(p, 120f, -50f)
            return world
        }
        val inSeconds = scene()
        val asDuration = scene()
        repeat(60) {
            inSeconds.step(TestBodies.DT)
            asDuration.step(TestBodies.STEP)
        }
        assertTrue(inSeconds.particles.size > 0)
        inSeconds.particles.forEach { p ->
            assertEquals(inSeconds.particles.xOf(p).toRawBits(), asDuration.particles.xOf(p).toRawBits(), "x of $p")
            assertEquals(inSeconds.particles.yOf(p).toRawBits(), asDuration.particles.yOf(p).toRawBits(), "y of $p")
        }
    }

    @Test
    fun aStepOfNoTimeIsRefused() {
        val world = PhysicsWorld()
        assertFailsWith<IllegalArgumentException> { world.step(Duration.ZERO) }
    }

    @Test
    fun aConfigRefusesValuesThatCannotWork() {
        assertFailsWith<IllegalArgumentException> { PhysicsConfig(substeps = 0) }
        assertFailsWith<IllegalArgumentException> { PhysicsConfig(iterations = 0) }
        assertFailsWith<IllegalArgumentException> { PhysicsConfig(damping = -1f) }
        assertFailsWith<IllegalArgumentException> { PhysicsConfig(maxStep = 0f) }
    }
}
