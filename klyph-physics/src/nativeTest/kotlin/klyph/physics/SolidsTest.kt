package klyph.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SolidsTest {
    private val particles = Particles()
    private val events = Events(particles)
    private var p = Particle(-1)

    /** Makes the only particle, at ([x], [y]) having come from ([fromX], [fromY]), [radius] wide. */
    private fun particle(x: Float, y: Float, fromX: Float = x, fromY: Float = y, radius: Float = 0f) {
        particles.clear()
        p = particles.add(x, y, radius = radius)
        particles.set(p.index, x, y, fromX, fromY)
    }

    private fun resolve(grid: SolidGrid) {
        events.beginStep()
        grid.collide(particles, events)
    }

    /** A 10 x 10 grid with a floor of full tiles on row 5, whose top is y = 80. */
    private fun floorGrid(): SolidGrid = TestBodies.grid().apply { for (c in 0 until columns) fill(c, 5) }

    @Test
    fun aParticleFallingIntoTheFloorLandsOnTopKeepingItsSidewaysSpeed() {
        val grid = floorGrid()
        particle(40f, 84f, fromX = 35f, fromY = 70f)
        resolve(grid)
        assertEquals(80f, particles.yOf(p), 0.001f)
        assertEquals(40f, particles.xOf(p), 0.001f)
        assertEquals(0f, particles.movedYOf(p), 0.001f)
        assertEquals(5f, particles.movedXOf(p), 0.001f)
        assertTrue(events.isOnFloor(p))
        assertTrue(events.isOnFloor(p))
    }

    @Test
    fun aParticleHittingTheSideOfABlockIsPushedSidewaysAndIsNotOnTheFloor() {
        val grid = TestBodies.grid().apply { fill(5, 3) }
        particle(82f, 56f, fromX = 78f, fromY = 56f)
        resolve(grid)
        assertEquals(80f, particles.xOf(p), 0.001f)
        assertEquals(56f, particles.yOf(p), 0.001f)
        assertFalse(events.isOnFloor(p))
    }

    @Test
    fun aParticleThatStartedInsideLeavesThroughTheNearestFace() {
        val grid = TestBodies.grid().apply { fill(5, 3) }
        particle(94f, 56f)
        resolve(grid)
        assertEquals(96f, particles.xOf(p), 0.001f)
    }

    @Test
    fun aParticleOutsideEverythingIsLeftAlone() {
        val grid = floorGrid()
        particle(40f, 60f, fromX = 41f, fromY = 59f)
        resolve(grid)
        assertEquals(40f, particles.xOf(p))
        assertEquals(60f, particles.yOf(p))
        assertFalse(events.isOnFloor(p))
    }

    @Test
    fun theSeamBetweenTwoFloorTilesDoesNotPushSideways() {
        val grid = floorGrid()
        particle(95.5f, 82f)
        resolve(grid)
        assertEquals(95.5f, particles.xOf(p), 0.001f)
        assertEquals(80f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aTaggedTileReportsItsTagWhenLandedOn() {
        val grid = TestBodies.grid().apply { fill(4, 5, tags = TestBodies.PAD) }
        particle(72f, 82f, fromY = 70f)
        resolve(grid)
        assertTrue(events.floorTagsOf(p) == TestBodies.PAD)
        assertTrue(events.isOnFloor(p))
    }

    @Test
    fun aRisingSlopePushesAlongItsSurfaceNormal() {
        // a ramp in the ground: a tile below it and one beside its tall (right) side
        val grid = TestBodies.grid().apply { fill(2, 2, SolidShape.RISING); fill(2, 3); fill(3, 2) }
        particle(44f, 44f)
        resolve(grid)
        assertEquals(40f, particles.xOf(p), 0.001f)
        assertEquals(40f, particles.yOf(p), 0.001f)
        assertTrue(events.isOnFloor(p))
    }

    @Test
    fun aFallingSlopeMirrorsIt() {
        val grid = TestBodies.grid().apply { fill(2, 2, SolidShape.FALLING); fill(2, 3); fill(1, 2) }
        particle(36f, 44f)
        resolve(grid)
        assertEquals(40f, particles.xOf(p), 0.001f)
        assertEquals(40f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aParticleThatHitsAFloatingSlopeFromBelowGoesBackDownNotOnTop() {
        // a rising slope in mid-air, cell (2, 2): x 32..48, y 32..48; hit from below, near its bottom
        val grid = TestBodies.grid().apply { fill(2, 2, SolidShape.RISING) }
        particle(44f, 46f, fromY = 50f)
        resolve(grid)
        assertEquals(44f, particles.xOf(p), 0.001f)
        assertEquals(48f, particles.yOf(p), 0.001f, "out through the open bottom, two units away, not onto the surface")
        assertFalse(events.isOnFloor(p), "a ceiling is not a floor")
    }

    @Test
    fun aParticleNearTheTallSideOfAFloatingSlopeLeavesThroughThatSide() {
        val grid = TestBodies.grid().apply { fill(2, 2, SolidShape.FALLING) }
        // a falling slope's tall side is its left one, at x = 32
        particle(33f, 44f, fromX = 30f)
        resolve(grid)
        assertEquals(32f, particles.xOf(p), 0.001f)
        assertEquals(44f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aSlopeBuriedBelowAndBesideStillPushesUpItsSurfaceFromDeepInside() {
        // a ramp in the ground: tiles below it and beside its tall side, so its surface is the only way out
        val grid = TestBodies.grid().apply { fill(2, 2, SolidShape.RISING); fill(2, 3); fill(3, 2) }
        // one unit above the bottom, where the bottom face would be the nearest if it were open
        particle(44f, 47f)
        resolve(grid)
        assertTrue(particles.yOf(p) < 47f, "pushed up, y = ${particles.yOf(p)}")
        assertEquals(44f - particles.xOf(p), 47f - particles.yOf(p), 0.001f, "up and left alike: along the surface's normal")
    }

    @Test
    fun aParticleJustUnderARampSeamIsPushedAlongTheSlopeNotSidewaysThroughTheCorner() {
        // two rising tiles, (2, 3) and (3, 2), meeting at a seam at (48, 48), with ground under and beside
        val grid = TestBodies.grid().apply {
            fill(2, 3, SolidShape.RISING)
            fill(3, 2, SolidShape.RISING)
            for (c in 2..4) fill(c, 4)
            fill(3, 3)
            fill(4, 3)
            fill(4, 2)
        }
        // a hair under the surface, just right of the seam: inside the full tile (3, 3) under the slope
        particle(49f, 48.2f)
        resolve(grid)
        assertEquals(49f - particles.xOf(p), 48.2f - particles.yOf(p), 0.001f, "up and left alike: along the slope's normal")
        assertEquals(1.2f * 0.70710677f, (49f - particles.xOf(p)) * 1.4142135f, 0.001f, "onto the slope's line, 0.85 away")
    }

    @Test
    fun aHalfTileStubOnlyFillsTheTopHalfOfItsTile() {
        val grid = TestBodies.grid().apply { fillTopHalf(4, 4) }
        particle(70f, 74f)
        resolve(grid)
        // y 64..72 is solid; 74 is below the stub, in the open half of the tile.
        assertEquals(74f, particles.yOf(p))
        particle(70f, 70f, fromY = 75f)
        resolve(grid)
        assertEquals(72f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aMovingSolidPushesToo() {
        val grid = TestBodies.grid()
        grid.add(Solid.moving(left = 20f, top = 20f, right = 52f, bottom = 52f))
        particle(36f, 50f)
        resolve(grid)
        assertEquals(52f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aBoxBesideTheTallEdgeOfARisingSlopeCannotBeLeftThroughThatFace() {
        val grid = TestBodies.grid().apply { fill(4, 5, SolidShape.RISING); fill(5, 5) }
        // inside the box, half a unit from the face it shares with the slope's full-height edge
        particle(80.5f, 82f)
        resolve(grid)
        assertEquals(80.5f, particles.xOf(p), 0.001f)
        assertEquals(80f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aTileIsNeverLeftThroughTheEdgeOfTheGridEvenWhenThatIsTheNearestFace() {
        // a wall tile at the grid's left edge with the floor beside it; outside the grid there is nothing to land on
        val grid = TestBodies.grid().apply { fill(0, 5); fill(1, 5) }
        particle(2f, 84f)
        resolve(grid)
        assertEquals(2f, particles.xOf(p), 0.001f, "not pushed out of the world, two units to the left")
        assertEquals(80f, particles.yOf(p), 0.001f, "out through the open top instead")
    }

    @Test
    fun aTileBuriedOnEveryOtherSideStillDoesNotPushOutOfTheGrid() {
        // the corner where a wall meets the floor: the top and right faces are buried, the left one is the world's edge
        val grid = TestBodies.grid().apply { fill(0, 4); fill(0, 5); fill(1, 5); fill(0, 6) }
        particle(2f, 88f)
        resolve(grid)
        assertTrue(particles.xOf(p) >= 2f, "never toward the world's edge, even as a last resort: x = ${particles.xOf(p)}")
        assertEquals(16f, particles.xOf(p), 0.001f, "out to the corner where the floor meets the wall, the nearest air")
        assertEquals(80f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aParticleJustInsideAnInnerCornerLeavesThroughTheCornerNotThroughTheWall() {
        // a step, (4, 5), against the foot of a wall, (5, 4) and (5, 5): tile (5, 5)'s top and left faces
        // are both buried, but the cell diagonally across its top-left corner, (4, 4), is air
        val grid = TestBodies.grid().apply { fill(5, 4); fill(5, 5); fill(4, 5) }
        // half a unit into the wall, right where the step's top meets it, having come from the step
        particle(80.5f, 80.01f, fromX = 79.5f)
        resolve(grid)
        assertEquals(80f, particles.xOf(p), 0.001f, "back out to the corner, not shoved 15.5 units through the wall")
        assertEquals(80f, particles.yOf(p), 0.001f)
        assertFalse(grid.isSolidAt(particles.xOf(p), particles.yOf(p)))
    }

    @Test
    fun aCornerIsNeverAWayOutWhenAnOpenFaceIsCloser() {
        val grid = floorGrid()
        // near a seam between floor tiles, the open top is closer than the corner above the seam
        particle(95.9f, 80.5f)
        resolve(grid)
        assertEquals(95.9f, particles.xOf(p), 0.001f)
        assertEquals(80f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aBoxBesideTheLowEdgeOfAFallingSlopeCanBeLeftThroughThatFace() {
        val grid = TestBodies.grid().apply { fill(4, 5, SolidShape.FALLING); fill(5, 5) }
        // a falling slope meets the box only at its bottom corner, so that face is open air above it
        particle(80.5f, 82f)
        resolve(grid)
        assertEquals(80f, particles.xOf(p), 0.001f)
        assertEquals(82f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aRoundParticleRestsARadiusAboveTheFloor() {
        val grid = floorGrid()
        particle(40f, 79f, fromY = 75f, radius = 3f)
        resolve(grid)
        assertEquals(77f, particles.yOf(p), 0.001f)
        assertEquals(0f, particles.movedYOf(p), 0.001f)
        assertTrue(events.isOnFloor(p))
    }

    @Test
    fun aRoundParticleIsPushedOffAWallItsCentreHasNotReached() {
        // the block (5, 3) is x 80..96; the centre is outside it, in the air tile beside it, but its radius reaches in
        val grid = TestBodies.grid().apply { fill(5, 3) }
        particle(78f, 56f, fromX = 76f, radius = 4f)
        resolve(grid)
        assertEquals(76f, particles.xOf(p), 0.001f)
        assertEquals(56f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aRoundParticleRestsARadiusAboveARisingSlopesSurface() {
        val grid = TestBodies.grid().apply { fill(2, 2, SolidShape.RISING); fill(2, 3); fill(3, 2) }
        particle(40f, 39f, radius = 2f)
        resolve(grid)
        // the surface through (40, 40) runs at 45 degrees: the centre ends up 2 units off it, along its normal
        val off = ((particles.xOf(p) - 32f) - (48f - particles.yOf(p))) * 0.70710677f
        assertEquals(-2f, off, 0.01f)
    }

    @Test
    fun aStaticParticleIsNeverPushed() {
        val grid = floorGrid()
        particles.clear()
        p = particles.add(40f, 84f, mass = Particles.STATIC)
        resolve(grid)
        assertEquals(84f, particles.yOf(p))
    }

    @Test
    fun aMovingSolidIsNotATileAndATileIsNotMoving() {
        assertTrue(Solid.moving(0f, 0f, 10f, 10f).isMoving)
        assertFalse(TestBodies.grid().fill(2, 3).isMoving)
        assertFalse(TestBodies.grid().fillTopHalf(2, 3).isMoving)
    }

    @Test
    fun aPointIsInsideASolidOnlyPastItsSurfaceByMoreThanTheMargin() {
        val box = TestBodies.grid().apply { fill(2, 2) }
        assertTrue(box.isSolidAt(40f, 40f))
        assertFalse(box.isSolidAt(32f, 40f), "a point on the surface is outside")
        assertFalse(box.isSolidAt(10f, 40f))
        assertFalse(box.isSolidAt(33f, 40f, margin = 2f), "inside by 1 is not inside by more than 2")
        assertTrue(box.isSolidAt(36f, 40f, margin = 2f))
    }

    @Test
    fun aStubIsOnlySolidInItsTopHalfAndASlopeOnlyBelowItsSurface() {
        val stub = TestBodies.grid().apply { fillTopHalf(4, 4) }
        assertTrue(stub.isSolidAt(70f, 68f))
        assertFalse(stub.isSolidAt(70f, 76f))
        val rising = TestBodies.grid().apply { fill(2, 2, SolidShape.RISING) }
        assertTrue(rising.isSolidAt(44f, 44f), "below the rising surface")
        assertFalse(rising.isSolidAt(36f, 36f), "above it")
        val falling = TestBodies.grid().apply { fill(2, 2, SolidShape.FALLING) }
        assertTrue(falling.isSolidAt(36f, 44f), "below the falling surface")
        assertFalse(falling.isSolidAt(44f, 36f), "above it")
    }

    @Test
    fun aMovingSolidIsFoundToo() {
        val grid = TestBodies.grid()
        grid.add(Solid.moving(20f, 20f, 52f, 52f))
        assertTrue(grid.isSolidAt(36f, 36f))
        assertFalse(grid.isSolidAt(60f, 36f))
    }

    @Test
    fun particlesOutsideTheGridDoNotCrashAndStillMeetEdgeTiles() {
        val grid = TestBodies.grid().apply { fill(0, 1); fill(9, 1) }
        // Far outside on every side, including negative coordinates that would alias rows if unchecked.
        for ((x, y) in listOf(-10f to 24f, -40f to -40f, 400f to 24f, 40f to 400f)) {
            particle(x, y, radius = 2f)
            resolve(grid)
            assertEquals(x, particles.xOf(p))
            assertEquals(y, particles.yOf(p))
        }
        // Just past the right edge of column 9 (x = 160), reaching in by its radius: still hits tile (9, 1)
        // (and leaves through its open top, since the right face is on the grid edge).
        particle(161f, 22f, radius = 3f)
        resolve(grid)
        assertEquals(13f, particles.yOf(p), 0.001f)
    }

    @Test
    fun aMovingSolidStillPushesInOpenAirAndGridEditsRefreshProximity() {
        val grid = TestBodies.grid()
        grid.add(Solid.moving(left = 60f, top = 60f, right = 100f, bottom = 100f))
        particle(80f, 98f, radius = 2f)
        resolve(grid)
        assertEquals(102f, particles.yOf(p), 0.001f, "moving solid pushes even when all grid tiles are empty")

        grid.fill(2, 2)
        particle(40f, 31f, radius = 2f)
        resolve(grid)
        assertEquals(30f, particles.yOf(p), 0.001f, "newly filled tile pushes")

        grid.removeAt(2, 2)
        particle(40f, 31f, radius = 2f)
        resolve(grid)
        assertEquals(31f, particles.yOf(p), 0.001f, "removed tile no longer pushes")
    }

    @Test
    fun aParticleAsWideAsANonPowerOfTwoTileStillHitsAtTheCellBoundary() {
        // tile = 24f: 119.999992f * (1f / 24f) rounds up to 5.0f in float32 while floor(x / 24f) is 4.
        // With radius = 24f, x - r = 95.999992f reaches into column 3 (right = 96f).
        val grid = SolidGrid(columns = 10, rows = 4, tileSize = 24f).apply { fill(3, 1) }
        particle(119.999992f, 36f, radius = 24f)
        resolve(grid)
        assertTrue(particles.xOf(p) >= 120f, "pushed clear of column 3: x=${particles.xOf(p)}")
    }
}
