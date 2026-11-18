package klyph.physics

import klyph.math.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Soft bodies: rings and cores of world particles, held by world constraints, reached through handles. */
class SoftBodyStoreTest {
    private val dt = 1f / 60f
    private val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO))
    private val bodies = world.softBodies

    private fun dome(): SoftBodyShape = TestBodies.dome()

    @Test
    fun theRestShapeIsADomeAsWideAsTwiceItsRadiusAndHeightScaleTimesItsRadiusTall() {
        val b = world.softBody(dome(), 0f, 0f)
        assertEquals(TestBodies.ARC_SEGMENTS + TestBodies.BASE_SEGMENTS, bodies.ringOf(b).size)
        assertEquals(2 * TestBodies.RADIUS, bodies.maxXOf(b) - bodies.minXOf(b), 0.01f)
        assertEquals(TestBodies.RADIUS * TestBodies.HEIGHT_SCALE, bodies.maxYOf(b) - bodies.minYOf(b), 0.01f)
    }

    @Test
    fun aBodyIsItsParticlesEdgesSpokesAndArea() {
        val b = world.softBody(dome(), 0f, 0f)
        val n = bodies.ringOf(b).size
        assertEquals(n + 1, world.particles.size, "a ring and a core")
        assertEquals(2 * n, world.constraints.linkCount, "an edge and a spoke per ring particle")
        assertEquals(1, world.constraints.areaCount)
        assertEquals(1f / TestBodies.CORE_MASS, world.particles.inverseMassOf(bodies.coreOf(b)), 0.0001f)
        assertEquals(bodies.particlesOf(b)[1], world.constraints.secondParticleOf(bodies.edgeOf(b, 0)))
        assertEquals(bodies.coreOf(b), world.constraints.secondParticleOf(bodies.spokeOf(b, 0)))
    }

    @Test
    fun placingPutsTheBaseOnTheGivenLineAndTheBodyAtRest() {
        val b = world.softBody(dome(), 0f, 0f)
        bodies.addVelocity(b, 50f, 50f)
        bodies.placeAt(b, centerX = 100f, baseY = 50f)
        assertEquals(50f, bodies.maxYOf(b), 0.01f)
        assertEquals(100f, (bodies.minXOf(b) + bodies.maxXOf(b)) / 2f, 0.01f)
        assertEquals(0f, bodies.meanVelocityYOf(b), 0.001f)
    }

    @Test
    fun turningABodyAQuarterTurnStandsItOnItsSideAboutItsMiddleAndAtRest() {
        // a box 30 wide and 10 tall, turned clockwise: its top becomes its right side
        val b = world.softBody(SoftBodyShape.box(30f, 10f, across = 6, down = 2, coreMass = 4f), 100f, 50f)
        bodies.addVelocity(b, 50f, 50f)
        val cx = bodies.centerXOf(b)
        val cy = bodies.centerYOf(b)
        bodies.rotate(b, kotlin.math.PI.toFloat() / 2f)
        assertEquals(10f, bodies.maxXOf(b) - bodies.minXOf(b), 0.01f)
        assertEquals(30f, bodies.maxYOf(b) - bodies.minYOf(b), 0.01f)
        assertEquals(cx, bodies.centerXOf(b), 0.01f)
        assertEquals(cy, bodies.centerYOf(b), 0.01f)
        val topLeft = bodies.particlesOf(b)[0]
        assertTrue(world.particles.xOf(topLeft) > cx && world.particles.yOf(topLeft) < cy, "the top-left corner is now the top-right one")
        assertEquals(0f, bodies.meanVelocityYOf(b), 0.001f)
    }

    @Test
    fun aSquashedBodyStaysFiniteAndSpringsBackToMostOfItsArea() {
        val b = world.softBody(dome(), 0f, 0f)
        val rest = bodies.areaOf(b)
        for (p in bodies.particlesOf(b)) {
            world.particles.moveTo(p, world.particles.xOf(p), world.particles.yOf(p) * 0.5f)
        }
        assertTrue(bodies.areaOf(b) < rest * 0.6f)
        var sum = 0f
        repeat(60) { step ->
            world.step(dt)
            assertTrue(bodies.isFinite(b))
            if (step >= 45) sum += bodies.areaOf(b)
        }
        assertTrue(sum / 15f > rest * 0.85f, "settled around ${sum / 15f} of $rest")
    }

    @Test
    fun anUndisturbedBodyWithNoGravityStaysWhereItIs() {
        val b = world.softBody(dome(), 0f, 0f)
        val rest = bodies.areaOf(b)
        repeat(10) { world.step(dt) }
        assertTrue(abs(bodies.areaOf(b) - rest) < rest * 0.01f)
        assertEquals(1f, bodies.maxEdgeStretchOf(b), 0.01f)
    }

    @Test
    fun theOutlineWorksOnAWorldBody() {
        val b = world.softBody(dome(), 100f, 100f)
        val ring = bodies.ringOf(b)
        assertSame(ring, bodies.ringOf(b), "the same view every time")
        assertTrue(Outline.contains(ring, bodies.centerXOf(b), 95f))
        assertFalse(Outline.contains(ring, 100f, 200f))
        val left = FloatArray(ring.size)
        val right = FloatArray(ring.size)
        assertEquals(1, Outline.spans(ring, 95f, left, right))
    }

    @Test
    fun removingABodyRemovesItsParticlesAndConstraints() {
        val a = world.softBody(dome(), 0f, 0f)
        val b = world.softBody(dome(), 100f, 0f)
        bodies.remove(a)
        assertFalse(bodies.isAlive(a))
        assertEquals(1, bodies.size)
        assertEquals(bodies.ringOf(b).size + 1, world.particles.size)
        assertEquals(2 * bodies.ringOf(b).size, world.constraints.linkCount)
        assertEquals(1, world.constraints.areaCount)
        assertTrue(bodies.isFinite(b))
        assertEquals(a, world.softBody(dome(), 200f, 0f), "the handle is reused")
    }

    @Test
    fun circlesAndBoxesHoldTheirShape() {
        val ball = world.softBody(SoftBodyShape.circle(20f, 16, coreMass = 10f), 0f, 100f)
        val box = world.softBody(SoftBodyShape.box(40f, 20f, across = 4, down = 2, coreMass = 10f), 100f, 100f)
        assertEquals(40f, bodies.maxXOf(ball) - bodies.minXOf(ball), 0.01f)
        assertEquals(100f, bodies.maxYOf(ball), 0.01f)
        assertEquals(12, bodies.ringOf(box).size)
        assertEquals(800f, bodies.areaOf(box), 0.01f)
        repeat(30) { world.step(dt) }
        assertEquals(1f, bodies.maxEdgeStretchOf(ball), 0.01f)
        assertEquals(800f, bodies.areaOf(box), 8f)
    }
}
