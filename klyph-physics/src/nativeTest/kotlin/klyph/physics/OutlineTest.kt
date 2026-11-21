package klyph.physics

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlineTest {
    private val world = PhysicsWorld()
    private val bodies = world.softBodies
    private val ps = world.particles

    private fun body(): SoftBody = world.softBody(TestBodies.dome(), centerX = 100f, baseY = 50f)

    private fun ring(b: SoftBody): Ring = bodies.ringOf(b)

    private val left = FloatArray(TestBodies.ARC_SEGMENTS + TestBodies.BASE_SEGMENTS)
    private val right = FloatArray(TestBodies.ARC_SEGMENTS + TestBodies.BASE_SEGMENTS)

    private fun filledArea(b: SoftBody): Float {
        var covered = 0f
        for (row in floor(bodies.minYOf(b)).toInt() until ceil(bodies.maxYOf(b)).toInt()) {
            val count = Outline.spans(ring(b), row + 0.5f, left, right)
            for (k in 0 until count) covered += right[k] - left[k]
        }
        return covered
    }

    /** Swaps two stretches of the outline's particles, which twists it into a bow-tie across the dome. */
    private fun fold(b: SoftBody) {
        for (i in 0 until 4) {
            val a = 2 + i
            val c = 9 - i
            val pa = bodies.particlesOf(b)[a]
            val pc = bodies.particlesOf(b)[c]
            val x = ps.xOf(pa)
            val y = ps.yOf(pa)
            ps.moveTo(pa, ps.xOf(pc), ps.yOf(pc))
            ps.moveTo(pc, x, y)
        }
    }

    @Test
    fun aRowThroughTheMiddleOfTheDomeIsOneStretchAroundTheCentre() {
        val b = body()
        assertEquals(1, Outline.spans(ring(b), 40f, left, right))
        assertTrue(left[0] < 100f && right[0] > 100f)
    }

    @Test
    fun rowsAboveAndBelowTheBodyHaveNoStretch() {
        val b = body()
        assertEquals(0, Outline.spans(ring(b), 10f, left, right))
        assertEquals(0, Outline.spans(ring(b), 60f, left, right))
    }

    @Test
    fun theRowsOfAFilledBodyAddUpToItsArea() {
        val b = body()
        assertTrue(abs(filledArea(b) - bodies.areaOf(b)) < bodies.areaOf(b) * 0.05f, "rows cover ${filledArea(b)} of ${bodies.areaOf(b)}")
    }

    @Test
    fun anOutlineFoldedOverItselfIsStillFilledSolid() {
        val b = body()
        val before = filledArea(b)
        fold(b)
        assertTrue(filledArea(b) > before * 0.6f, "only ${filledArea(b)} of $before is filled after the fold")
    }

    @Test
    fun theMiddleOfTheBodyIsInsideAndThePointsAroundItAreNot() {
        val b = body()
        assertTrue(Outline.contains(ring(b), 100f, 44f))
        assertTrue(!Outline.contains(ring(b), 100f, 20f))
        assertTrue(!Outline.contains(ring(b), 60f, 48f))
        assertTrue(!Outline.contains(ring(b), 100f, 60f))
    }

    @Test
    fun insideMeansTheSameThingToTheFillAndToWhatPushesAgainstTheBodyEvenWhenItIsFolded() {
        val b = body()
        fold(b)
        var checked = 0
        var y = floor(bodies.minYOf(b)) - 2f
        while (y < ceil(bodies.maxYOf(b)) + 2f) {
            val yc = floor(y) + 0.5f
            val count = Outline.spans(ring(b), yc, left, right)
            var x = bodies.minXOf(b) - 2f
            while (x < bodies.maxXOf(b) + 2f) {
                val inSpan = (0 until count).any { x >= left[it] && x < right[it] }
                // points on a stretch's edge may fall either way by rounding, so stay off them
                val nearEdge = (0 until count).any { abs(x - left[it]) < 0.01f || abs(x - right[it]) < 0.01f }
                if (!nearEdge) {
                    assertEquals(inSpan, Outline.contains(ring(b), x, yc), "disagree at ($x, $yc)")
                    checked++
                }
                x += 0.7f
            }
            y += 1f
        }
        assertTrue(checked > 200, "only $checked points compared")
    }

    @Test
    fun theNearestPointOnTheOutlineOfAPointInsideIsOnTheOutline() {
        val b = body()
        val point = FloatArray(2)
        // 4 units above the flat base in the middle: the base is the nearest edge
        val distanceSquared = Outline.nearestPoint(ring(b), 100f, 46f, point)
        assertEquals(100f, point[0], 0.01f)
        assertEquals(50f, point[1], 0.01f)
        assertEquals(16f, distanceSquared, 0.01f)
    }
}
