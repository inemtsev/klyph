package klyph.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventsTest {
    private val particles = Particles()
    private val events = Events(particles)
    private val a = particles.add(0f, 0f)
    private val b = particles.add(0f, 0f)

    @Test
    fun aFloorTouchIsSeenThisStepButOnlyRememberedAsLastStepAfterTheNextBegins() {
        events.beginStep()
        events.recordFloor(a.index, tag = 0)
        assertTrue(events.isOnFloor(a))
        assertFalse(events.wasOnFloor(a), "this step's touch is not last step's")
        assertFalse(events.isOnFloor(b))
        events.beginStep()
        assertTrue(events.wasOnFloor(a))
        assertFalse(events.isOnFloor(a), "the new step starts with nothing touched")
        events.beginStep()
        assertFalse(events.wasOnFloor(a), "and is forgotten a step later")
    }

    @Test
    fun theTagsOfWhatWasTouchedAddUpForTheStepOnly() {
        events.beginStep()
        events.recordFloor(a.index, tag = 0b01)
        events.recordFloor(a.index, tag = 0b100)
        assertEquals(0b101, events.floorTagsOf(a).bits)
        assertEquals(SolidTags.NONE, events.floorTagsOf(b))
        events.beginStep()
        assertEquals(SolidTags.NONE, events.floorTagsOf(a))
    }

    @Test
    fun aNewParticleInAReusedSlotStartsWithNothingTouched() {
        events.beginStep()
        events.recordFloor(a.index, tag = 1)
        particles.remove(a)
        val c = particles.add(0f, 0f)
        assertEquals(a, c)
        assertFalse(events.isOnFloor(c))
        assertEquals(SolidTags.NONE, events.floorTagsOf(c))
    }

    @Test
    fun aBodyDroppedOnAFloorLandsStaysOnTopAndSaysSo() {
        val world = PhysicsWorld()
        world.solids = SolidGrid(20, 10, TestBodies.TILE).apply { for (c in 0 until columns) fill(c, 8, tags = TestBodies.PAD) }
        val blob = world.softBody(TestBodies.dome(), centerX = 160f, baseY = 60f)
        assertFalse(world.softBodies.isOnFloor(blob))
        // With its base gripping the floor, it sloshes for a few seconds before it's still.
        repeat(240) { world.step(TestBodies.DT) }
        val floor = 8 * TestBodies.TILE
        assertTrue(world.softBodies.isOnFloor(blob))
        assertTrue(world.softBodies.floorTagsOf(blob).intersects(TestBodies.PAD))
        assertTrue(world.softBodies.floorContactCountOf(blob) >= TestBodies.BASE_SEGMENTS, "the base rests on it")
        assertEquals(floor, world.softBodies.maxYOf(blob), 0.5f)
        assertEquals(0f, world.softBodies.meanVelocityYOf(blob), 1f)
        assertTrue(world.events.wasOnFloor(world.softBodies.particlesOf(blob)[0]) || world.events.wasOnFloor(world.softBodies.particlesOf(blob)[TestBodies.ARC_SEGMENTS]))
    }

    @Test
    fun aRoundParticleBouncesOffNothingAndRestsARadiusAboveTheFloor() {
        val world = PhysicsWorld()
        world.solids = SolidGrid(10, 10, TestBodies.TILE).apply { for (c in 0 until columns) fill(c, 8) }
        val p = world.particles.add(80f, 20f, radius = 5f)
        repeat(120) { world.step(TestBodies.DT) }
        assertEquals(8 * TestBodies.TILE - 5f, world.particles.yOf(p), 0.01f)
        assertTrue(world.events.isOnFloor(p))
    }

    @Test
    fun aSoftBodysTouchesAreOnlyWrittenDownInTheLastPassOfASubstep() {
        events.beginPass(last = false)
        assertFalse(events.records(owner = 0))
        assertEquals(0f, events.touchReach(owner = 0))
        events.beginPass(last = true)
        assertTrue(events.records(owner = 0))
        assertEquals(Events.TOUCH_SLOP, events.touchReach(owner = 0))
    }

    @Test
    fun aParticleThatBelongsToNoSoftBodyWritesNothingDown() {
        events.beginPass(last = true)
        assertFalse(events.records(owner = -1))
        assertEquals(0f, events.touchReach(owner = -1))
    }

    @Test
    fun outsideAStepTouchesAreWrittenDownSoAModuleDrivenByHandSeesThem() {
        assertTrue(events.records(owner = 0))
    }

    @Test
    fun aTouchSaidTwiceInOneStepBeginsOnceAndSaidAgainTheNextStepBeginsNothing() {
        events.beginStep()
        events.touch(TouchKind.SOFT_BODY, 0, TouchKind.SOLIDS, -1)
        events.touch(TouchKind.SOLIDS, -1, TouchKind.SOFT_BODY, 0)
        events.endStep()
        assertEquals(1, events.contactsBegun.size)
        events.beginStep()
        events.touch(TouchKind.SOFT_BODY, 0, TouchKind.SOLIDS, -1)
        events.endStep()
        assertEquals(0, events.contactsBegun.size)
        assertEquals(0, events.contactsEnded.size)
    }

    @Test
    fun aWorldStepEndsOnTheLastPassSoTheNextStepStartsRecording() {
        val world = PhysicsWorld(PhysicsConfig(iterations = 3))
        world.step(TestBodies.STEP)
        assertTrue(world.events.records(owner = 0))
    }
}
