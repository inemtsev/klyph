package klyph.physics

import klyph.math.Vec2
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstraintsTest {
    /** A world with no gravity or damping, where one step is exactly one solve pass. */
    private val world = PhysicsWorld(PhysicsConfig(gravity = Vec2.ZERO, damping = 0f, substeps = 1, iterations = 1, maxStep = 1000f))
    private val ps = world.particles
    private val cs = world.constraints

    private fun solveOnce() = world.step(1f / 60f)

    private fun distance(a: Particle, b: Particle): Float {
        val dx = ps.xOf(a) - ps.xOf(b)
        val dy = ps.yOf(a) - ps.yOf(b)
        return sqrt(dx * dx + dy * dy)
    }

    @Test
    fun aDistanceAtRestLeavesItsParticlesAlone() {
        val a = ps.add(0f, 0f)
        val b = ps.add(10f, 0f)
        cs.distance(a, b)
        repeat(10) { solveOnce() }
        assertEquals(0f, ps.xOf(a))
        assertEquals(10f, ps.xOf(b))
    }

    @Test
    fun aRigidDistanceStretchedPullsBothEndsHalfway() {
        val a = ps.add(0f, 0f)
        val b = ps.add(10f, 0f)
        cs.distance(a, b, rest = 5f)
        solveOnce()
        assertEquals(2.5f, ps.xOf(a), 0.0001f)
        assertEquals(7.5f, ps.xOf(b), 0.0001f)
    }

    @Test
    fun aRigidDistanceCompressedPushesThemApart() {
        val a = ps.add(0f, 0f)
        val b = ps.add(10f, 0f)
        cs.distance(a, b, rest = 20f)
        solveOnce()
        assertEquals(-5f, ps.xOf(a), 0.0001f)
        assertEquals(15f, ps.xOf(b), 0.0001f)
    }

    @Test
    fun theLighterEndMovesMore() {
        val heavy = ps.add(0f, 0f, mass = 3f)
        val light = ps.add(10f, 0f, mass = 1f)
        cs.distance(heavy, light, rest = 6f)
        solveOnce()
        assertEquals(1f, ps.xOf(heavy), 0.0001f, "a quarter of the 4-unit fix")
        assertEquals(7f, ps.xOf(light), 0.0001f, "three quarters")
    }

    @Test
    fun aStaticEndNeverMoves() {
        val anchor = ps.add(0f, 0f, mass = Particles.STATIC)
        val b = ps.add(10f, 0f)
        cs.distance(anchor, b, rest = 4f)
        solveOnce()
        assertEquals(0f, ps.xOf(anchor))
        assertEquals(4f, ps.xOf(b), 0.0001f)
    }

    @Test
    fun aRopeGoesSlackWhenCompressedAndPullsWhenStretched() {
        val a = ps.add(0f, 0f)
        val b = ps.add(10f, 0f)
        val rope = cs.rope(a, b, rest = 20f)
        assertTrue(cs.isRope(rope))
        solveOnce()
        assertEquals(0f, ps.xOf(a), "slack: nothing happens")
        assertEquals(10f, ps.xOf(b))
        val c = ps.add(0f, 10f)
        val d = ps.add(30f, 10f)
        cs.rope(c, d, rest = 20f)
        solveOnce()
        assertEquals(20f, distance(c, d), 0.0001f)
    }

    @Test
    fun aRigidPinHoldsItsParticleAtThePointAndThePointCanMove() {
        val p = ps.add(3f, 4f)
        val pin = cs.pin(p, 0f, 0f)
        solveOnce()
        assertEquals(0f, ps.xOf(p), 0.0001f)
        assertEquals(0f, ps.yOf(p), 0.0001f)
        cs.movePin(pin, 10f, 0f)
        solveOnce()
        assertEquals(10f, ps.xOf(p), 0.0001f)
    }

    @Test
    fun aSoftPinOnlyPullsPartWay() {
        val p = ps.add(10f, 0f)
        cs.pin(p, 0f, 0f, compliance = 0.0001f)
        solveOnce()
        assertTrue(ps.xOf(p) > 0.5f && ps.xOf(p) < 10f, "partway: x = ${ps.xOf(p)}")
    }

    private fun square(clockwise: Boolean): List<Particle> {
        val corners = listOf(0f to 0f, 10f to 0f, 10f to 10f, 0f to 10f).let { if (clockwise) it else it.reversed() }
        return List(4) { ps.add(corners[it].first, corners[it].second) }
    }

    @Test
    fun anAreaAtRestLeavesItsRingAlone() {
        val ring = square(clockwise = true)
        val area = cs.area(ring)
        assertEquals(100f, cs.restAreaOf(area), 0.001f)
        repeat(5) { solveOnce() }
        assertEquals(10f, ps.xOf(ring[1]), 0.0001f)
    }

    @Test
    fun aSquashedAreaGrowsBackWhicheverWayTheRingRuns() {
        for (clockwise in listOf(true, false)) {
            ps.clear()
            cs.clear()
            val ring = square(clockwise)
            val area = cs.area(ring, rest = 200f)
            repeat(20) { solveOnce() }
            assertEquals(200f, cs.currentAreaOf(area), 2f, "clockwise = $clockwise")
        }
    }

    @Test
    fun complianceMeansTheSameSoftnessWhateverTheSubsteps() {
        // A weight hanging from a soft spring settles where the spring's pull matches its weight:
        // stretched by mass * gravity * compliance, however finely the world is stepped.
        val compliance = 0.01f
        fun stretch(substeps: Int, iterations: Int): Float {
            val w = PhysicsWorld(PhysicsConfig(gravity = Vec2(0f, 1500f), substeps = substeps, iterations = iterations, damping = 4f))
            val top = w.particles.add(0f, 0f, mass = Particles.STATIC)
            val weight = w.particles.add(0f, 50f)
            w.constraints.distance(top, weight, compliance)
            repeat(600) { w.step(1f / 60f) }
            return w.particles.yOf(weight) - 50f
        }
        val expected = 1f * 1500f * compliance
        for ((substeps, iterations) in listOf(2 to 1, 16 to 1, 4 to 6)) {
            assertEquals(expected, stretch(substeps, iterations), 0.2f, "$substeps substeps, $iterations iterations")
        }
    }

    @Test
    fun aConstraintOnARemovedParticleIsSkipped() {
        val a = ps.add(0f, 0f)
        val b = ps.add(10f, 0f)
        cs.distance(a, b, rest = 5f)
        ps.remove(a)
        solveOnce()
        assertEquals(10f, ps.xOf(b), "nothing pulls on b")
    }

    @Test
    fun removingAParticleTakesItsConstraintsWithIt() {
        val a = ps.add(0f, 0f)
        val b = ps.add(10f, 0f)
        val c = ps.add(20f, 0f)
        val link = cs.distance(a, b)
        val pin = cs.pin(a)
        val area = cs.area(listOf(a, b, ps.add(0f, 10f)))
        val other = cs.distance(b, c)
        world.particles.remove(a)
        assertFalse(cs.isAlive(link))
        assertFalse(cs.isAlive(pin))
        assertFalse(cs.isAlive(area))
        assertTrue(cs.isAlive(other))
        val reused = ps.add(50f, 50f)
        assertEquals(a, reused)
        solveOnce()
        assertEquals(50f, ps.xOf(reused), "the new particle inherits nothing")
    }

    @Test
    fun aRemovedLinkHandleIsReused() {
        val a = ps.add(0f, 0f)
        val b = ps.add(10f, 0f)
        val first = cs.distance(a, b)
        cs.remove(first)
        assertEquals(0, cs.linkCount)
        assertEquals(first, cs.rope(a, b))
        assertEquals(1, cs.linkCount)
    }

    @Test
    fun strainIsHowFarFromRestAsAShare() {
        val a = ps.add(0f, 0f)
        val b = ps.add(11f, 0f)
        val link = cs.distance(a, b, rest = 10f)
        assertEquals(0.1f, cs.strainOf(link), 0.0001f)
    }

    @Test
    fun constraintsRefuseWhatCannotWork() {
        val a = ps.add(0f, 0f)
        val b = ps.add(10f, 0f)
        assertFailsWith<IllegalArgumentException> { cs.distance(a, a) }
        assertFailsWith<IllegalArgumentException> { cs.distance(a, b, compliance = -1f) }
        assertFailsWith<IllegalArgumentException> { cs.distance(a, Particle(99)) }
        assertFailsWith<IllegalArgumentException> { cs.area(listOf(a, b)) }
    }

    @Test
    fun manyConstraintsGrowTheirStores() {
        val first = ps.add(0f, 0f)
        var previous = first
        repeat(100) {
            val next = ps.add(it + 1f, 0f)
            cs.distance(previous, next)
            cs.pin(next)
            previous = next
        }
        assertEquals(100, cs.linkCount)
        assertEquals(100, cs.pinCount)
        solveOnce()
        assertTrue(abs(ps.xOf(previous) - 100f) < 0.001f)
    }

    @Test
    fun removingAParticleTakesItsAttachmentWithIt() {
        val body = world.rigidBody(Shape.box(20f, 20f), 100f, 100f, kind = BodyKind.STATIC)
        val grain = ps.add(0f, 0f)
        val other = ps.add(5f, 5f)
        val tie = world.attach(grain, body)
        val untouched = world.attach(other, body)
        ps.remove(grain)
        assertFalse(world.attachments.isAlive(tie))
        assertTrue(world.attachments.isAlive(untouched))
        val reused = ps.add(50f, 50f)
        assertEquals(grain, reused)
        repeat(5) { solveOnce() }
        assertEquals(50f, ps.xOf(reused), "the new particle is not pulled to the body")
    }

    @Test
    fun removingAParticleTakesTheJointItIsAnEndOfWithIt() {
        val body = world.rigidBody(Shape.box(20f, 20f), 100f, 100f, kind = BodyKind.STATIC)
        val bob = ps.add(100f, 130f)
        val keep = world.rigidBody(Shape.box(20f, 20f), 300f, 100f, kind = BodyKind.STATIC)
        val hinge = world.joints.hinge(body, bob, x = 100f, y = 110f)
        val other = world.joints.hinge(body, keep, x = 200f, y = 100f)
        ps.remove(bob)
        assertFalse(world.joints.isAlive(hinge))
        assertTrue(world.joints.isAlive(other))
    }
}
