package klyph.physics.demo

import klyph.physics.HitKind
import klyph.physics.Overlaps
import klyph.physics.PhysicsWorld
import klyph.physics.RigidBody
import klyph.physics.SolidGrid
import klyph.physics.SolidShape
import klyph.physics.centerXOf
import klyph.physics.centerYOf
import klyph.physics.isFinite
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Every scene, run for ten seconds with no window: nothing goes NaN, nothing ends up inside a wall. */
class HeadlessTest {
    private val dt = 16_666_666 / 1e9f
    private val roomWidth = Scenes.COLS * Scenes.TILE
    private val roomHeight = Scenes.ROWS * Scenes.TILE

    /** How far into a solid a particle's centre may be before it counts as through it: a contact being solved, not a tunnel. */
    private val slack = 2f

    /**
     * How deep ([x], [y]) is inside the tile at ([col], [row]) of [grid]: its distance to the nearest
     * face it could be pushed out of, a slope's surface or a side with no tile beyond it. 0 or less
     * when it's outside.
     */
    private fun depth(grid: SolidGrid, col: Int, row: Int, x: Float, y: Float): Float {
        val solid = grid.atOrNull(col, row) ?: return 0f
        fun open(dc: Int, dr: Int) = grid.atOrNull(col + dc, row + dr) == null
        val diagonal = sqrt(0.5f)
        var nearest = Float.MAX_VALUE
        when (solid.shape) {
            SolidShape.BOX -> {
                if (open(0, -1)) nearest = minOf(nearest, y - solid.top)
                if (open(-1, 0)) nearest = minOf(nearest, x - solid.left)
                if (open(1, 0)) nearest = minOf(nearest, solid.right - x)
                if (open(0, 1)) nearest = minOf(nearest, solid.bottom - y)
            }
            // under a slope from the top-left down to the bottom-right: the solid is below-left of it
            SolidShape.FALLING -> {
                nearest = (y - solid.top - (x - solid.left)) * diagonal
                if (open(-1, 0)) nearest = minOf(nearest, x - solid.left)
                if (open(0, 1)) nearest = minOf(nearest, solid.bottom - y)
            }
            SolidShape.RISING -> {
                nearest = (y - solid.top - (solid.right - x)) * diagonal
                if (open(1, 0)) nearest = minOf(nearest, solid.right - x)
                if (open(0, 1)) nearest = minOf(nearest, solid.bottom - y)
            }
        }
        return if (nearest == Float.MAX_VALUE) grid.tileSize else nearest
    }

    private fun checkWorld(name: String, world: PhysicsWorld) {
        val ps = world.particles
        val grid = world.solids
        ps.forEach { p ->
            val x = ps.xOf(p)
            val y = ps.yOf(p)
            assertTrue(x.isFinite() && y.isFinite(), "$name: particle $p is at ($x, $y)")
            if (grid == null || ps.inverseMassOf(p) == 0f) return@forEach
            assertTrue(x in 0f..roomWidth && y in 0f..roomHeight, "$name: particle $p left the room, to ($x, $y)")
            // in every tile it touches, since one on a tile's edge or corner is in all of them, and only
            // as deep as the shallowest
            var deep = Float.MAX_VALUE
            for (dx in -1..1 step 2) for (dy in -1..1 step 2) {
                val col = floor((x + dx * 0.01f) / grid.tileSize).toInt()
                val row = floor((y + dy * 0.01f) / grid.tileSize).toInt()
                deep = minOf(deep, depth(grid, col, row, x, y))
            }
            assertTrue(deep < slack, "$name: particle $p at ($x, $y) is $deep deep in a solid")
        }
        val bodies = world.softBodies
        bodies.forEach { body -> assertTrue(bodies.isFinite(body), "$name: body $body went NaN") }
        val rigid = world.rigidBodies
        rigid.forEach { b ->
            val x = rigid.xOf(b)
            val y = rigid.yOf(b)
            assertTrue(x.isFinite() && y.isFinite() && rigid.angleOf(b).isFinite(), "$name: rigid body $b is at ($x, $y)")
            if (grid == null || rigid.inverseMassOf(b) == 0f) return@forEach
            assertTrue(x in 0f..roomWidth && y in 0f..roomHeight, "$name: rigid body $b left the room, to ($x, $y)")
            // its middle in a tile, deeper than a contact being solved: through a wall
            val deep = depth(grid, floor(x / grid.tileSize).toInt(), floor(y / grid.tileSize).toInt(), x, y)
            assertTrue(deep < slack, "$name: rigid body $b at ($x, $y) is $deep deep in a solid")
        }
    }

    @Test
    fun everySceneRunsSixHundredStepsWithNothingNaNOrThroughAWall() {
        for (scene in Scenes.all) {
            val run = scene.create()
            repeat(600) {
                run.step(dt)
                if (it % 60 == 59) checkWorld("${scene.name} at step ${it + 1}", run.world)
            }
        }
    }

    @Test
    fun everySandGrainStaysInTheHourglassAndMostOfThemPourThrough() {
        val run = SandScene.create()
        val world = run.world
        repeat(900) { run.step(dt) }
        val ps = world.particles
        val left = (SandScene.LEFT_WALL + 1) * Scenes.TILE
        val right = SandScene.RIGHT_WALL * Scenes.TILE
        val neck = (SandScene.NECK_ROW + 1) * Scenes.TILE
        var below = 0
        ps.forEach { p ->
            assertTrue(ps.xOf(p) in left..right, "grain $p got out of the hourglass, to (${ps.xOf(p)}, ${ps.yOf(p)})")
            if (ps.yOf(p) > neck) below++
        }
        assertEquals(SandScene.GRAINS, ps.size)
        assertTrue(below > SandScene.GRAINS * 9 / 10, "after 15 seconds most have poured through: $below")
    }

    @Test
    fun inStacksTheTowerAndPyramidStandEveryDominoFallsAndTheBallKnocksTheTowerDown() {
        val run = StacksScene.create() as StacksScene.Run
        val bodies = run.world.rigidBodies
        val startX = (run.tower + run.pyramid).map { bodies.xOf(it) }
        val startY = (run.tower + run.pyramid).map { bodies.yOf(it) }
        val held = (StacksScene.RELEASE_AT / dt).toInt() - 1
        repeat(held) { run.step(dt) }
        assertFalse(run.released, "the ball is still held out")
        for ((k, b) in (run.tower + run.pyramid).withIndex()) {
            assertEquals(startX[k], bodies.xOf(b), 1f, "box $b stays where it was stacked")
            assertEquals(startY[k], bodies.yOf(b), 1f, "box $b keeps its height")
            assertTrue(bodies.isAsleep(b), "box $b has fallen asleep")
        }
        repeat(60) { run.step(dt) }
        assertFalse(bodies.isAsleep(run.tower.last()), "the ball has woken the tower")
        repeat(900 - held - 60) { run.step(dt) }
        val pyramidFrom = run.tower.size
        for ((k, b) in run.pyramid.withIndex()) {
            assertEquals(startX[pyramidFrom + k], bodies.xOf(b), 1f, "pyramid box $b stands: the ball misses it")
            assertEquals(startY[pyramidFrom + k], bodies.yOf(b), 1f)
        }
        val top = run.tower.last()
        assertTrue(bodies.yOf(top) > startY[run.tower.size - 1] + 60f, "the ball knocked the tower's top down: it's at ${bodies.yOf(top)}")
        for (d in run.dominoes) assertTrue(bodies.angleOf(d) > 0.6f, "domino $d fell: it's at ${bodies.angleOf(d)}")
    }

    @Test
    fun inCratesAndBlobsTheCushionHoldsItsCrateTheBlobFellsTheTowerTheSwingScattersTheBlobsAndTheTrampolineBounces() {
        val run = CratesAndBlobsScene.create() as CratesAndBlobsScene.Run
        val bodies = run.world.rigidBodies
        val soft = run.world.softBodies
        val floor = (Scenes.ROWS - 2) * Scenes.TILE
        val crateHalf = CratesAndBlobsScene.CRATE / 2f
        val targetsAt = run.targets.map { soft.centerXOf(it) }
        // the crate dropped on the trampoline: how low it went, and how far back up after
        var lowest = 0f
        var bounced = 0f
        repeat(600) {
            run.step(dt)
            val jumper = run.jumper ?: return@repeat
            val y = bodies.yOf(jumper)
            if (y > lowest) lowest = y
            bounced = maxOf(bounced, lowest - y)
        }
        val bottom = bodies.yOf(run.crate) + crateHalf
        assertTrue(bottom < floor - 10f, "the cushion holds its crate up: its bottom at $bottom")
        assertEquals(CratesAndBlobsScene.CUSHION_X, bodies.xOf(run.crate), 6f, "on top of it")
        val top = run.tower.last()
        assertTrue(bodies.yOf(top) > floor - CratesAndBlobsScene.TOWER * CratesAndBlobsScene.TOWER_CRATE + 30f, "the tower was knocked down: its top crate at ${bodies.yOf(top)}")
        var scattered = 0
        for ((k, t) in run.targets.withIndex()) if (kotlin.math.abs(soft.centerXOf(t) - targetsAt[k]) > 20f) scattered++
        assertTrue(scattered >= 2, "the swinging crate scattered the blobs: $scattered of ${run.targets.size}")
        val ps = run.world.particles
        val tie = run.tie
        val dx = run.world.attachments.pointXOf(tie) - ps.xOf(run.rope.last)
        val dy = run.world.attachments.pointYOf(tie) - ps.yOf(run.rope.last)
        assertTrue(dx * dx + dy * dy < 1f, "the rope stays tied to its crate")
        val trampoline = CratesAndBlobsScene.TRAMPOLINE_Y
        assertTrue(lowest > trampoline, "the trampoline stretched under the crate: it went down to $lowest")
        assertTrue(bounced > 15f, "and threw it back up $bounced")
        val jumper = run.jumper!!
        val y = bodies.yOf(jumper)
        assertTrue(y in trampoline - 20f..trampoline + 60f, "it rests on the trampoline at last, at $y")
        assertTrue(bodies.xOf(jumper) in CratesAndBlobsScene.TRAMPOLINE_LEFT..CratesAndBlobsScene.TRAMPOLINE_RIGHT)
    }

    @Test
    fun inMachinesTheCarDrivesBothWaysTheShelfBreaksTheLiftGoesDownAndUpTheMillTurnsAndTheSeesawThrows() {
        val run = MachinesScene.create() as MachinesScene.Run
        val bodies = run.world.rigidBodies
        val joints = run.world.joints
        var furthest = 0f
        var turnedBack = false
        var lowest = 0f
        var rose = false
        var swinging = 0
        var springGave = 0f
        // the most the car leaned: turning round with all its torque, it rears up, but no further than this
        var tilt = 0f
        val ballFrom = bodies.yOf(run.ball)
        var ballHighest = ballFrom
        val placeEvery = (MachinesScene.PLACE_EVERY / dt).toInt()
        repeat(MachinesScene.PLACED * placeEvery + 120) { step ->
            run.step(dt)
            furthest = maxOf(furthest, bodies.xOf(run.chassis))
            if (run.direction < 0f) turnedBack = true
            val down = -joints.positionOf(run.slider)
            if (down > lowest) lowest = down
            if (lowest > MachinesScene.LIFT_TRAVEL - 2f && down < lowest - 30f) rose = true
            if (kotlin.math.abs(bodies.angularVelocityOf(run.lower)) > 1f) swinging++
            for (shock in run.shocks) springGave = maxOf(springGave, kotlin.math.abs(joints.positionOf(shock)))
            ballHighest = minOf(ballHighest, bodies.yOf(run.ball))
            tilt = maxOf(tilt, kotlin.math.abs(bodies.angleOf(run.chassis)))
            // three crates on the shelf and it holds; the fourth breaks it
            if (step == (MachinesScene.PLACED) * placeEvery - 10) assertTrue(joints.isAlive(run.weld), "it bears three crates")
        }
        assertFalse(joints.isAlive(run.weld), "the fourth crate broke the shelf")
        assertTrue(furthest > MachinesScene.TURN_LEFT_AT && turnedBack, "the car drove right, and turned back: as far as $furthest")
        assertTrue(lowest > MachinesScene.LIFT_TRAVEL - 2f && rose, "the lift went all the way down, $lowest, and back up")
        assertTrue(swinging > 300, "the pendulum keeps swinging: fast in $swinging steps")
        // the car stays on its wheels: it rears up as it turns round, but never tips over (on its side is 1.57)
        assertTrue(tilt < 1f, "the car never tipped over: it leaned $tilt")
        assertTrue(springGave > 1f, "its springs gave, on the bump and under its weight: $springGave")
        for (shock in run.shocks) {
            assertTrue(kotlin.math.abs(joints.positionOf(shock)) <= MachinesScene.TRAVEL + 0.5f, "a strut stays in its travel: ${joints.positionOf(shock)}")
        }
        // the motor turns the mill at its speed: the other way to its joint's angle, as its second end is the world
        assertEquals(MachinesScene.MILL_SPEED, kotlin.math.abs(bodies.angularVelocityOf(run.mill)), 0.1f, "the mill turns")
        assertTrue(run.heavy != null && ballFrom - ballHighest > 60f, "the crate on the seesaw threw the ball ${ballFrom - ballHighest} up")
    }

    @Test
    fun inJointsTheCradlePassesItsSwingToTheLastBallAndTheBridgeHoldsTheRagdoll() {
        val run = JointsScene.create() as JointsScene.Run
        val bodies = run.world.rigidBodies
        val joints = run.world.joints
        val rest = FloatArray(JointsScene.BALLS) { JointsScene.CRADLE_X + it * JointsScene.SPACING }
        var lastOut = 0f
        var middleMoved = 0f
        repeat(48) {
            run.step(dt)
            lastOut = maxOf(lastOut, bodies.xOf(run.balls.last()) - rest.last())
            for (i in 1 until JointsScene.BALLS - 1) middleMoved = maxOf(middleMoved, abs(bodies.xOf(run.balls[i]) - rest[i]))
        }
        // pulled out a radian on a rod of 120, it swings 101 out
        assertTrue(lastOut > 80f, "the last ball swung out: $lastOut")
        assertTrue(middleMoved < 10f, "the middle three stayed put: they moved $middleMoved")

        repeat(240) { run.step(dt) }
        val torso = run.ragdolls[0].torso
        val floor = (Scenes.ROWS - 2) * Scenes.TILE
        assertTrue(bodies.xOf(torso) in JointsScene.BRIDGE_LEFT..JointsScene.BRIDGE_RIGHT, "the ragdoll is on the bridge: ${bodies.xOf(torso)}")
        assertTrue(bodies.yOf(torso) in JointsScene.BRIDGE_Y..floor - 40f, "held up by it: ${bodies.yOf(torso)}")
        for (j in run.ragdolls[0].hinges + run.bridgeHinges) {
            val gap = sqrt(sq(joints.firstAnchorXOf(j) - joints.secondAnchorXOf(j)) + sq(joints.firstAnchorYOf(j) - joints.secondAnchorYOf(j)))
            assertTrue(gap < 1f, "hinge $j holds together: its anchors are $gap apart")
        }
    }

    private fun sq(x: Float) = x * x

    @Test
    fun inTheDrumEverythingTumblesAndNothingGetsOut() {
        val run = DrumScene.create() as DrumScene.Run
        val bodies = run.world.rigidBodies
        val ps = run.world.particles
        var highest = Float.MAX_VALUE
        // its ring is flat boxes, so where two meet the wall is 0.7 further out than its radius
        val wall = -1f
        repeat(600) { step ->
            run.step(dt)
            for (c in run.crates) highest = minOf(highest, bodies.yOf(c))
            if (step % 60 == 59) {
                for (c in run.crates) assertTrue(run.inside(bodies.xOf(c), bodies.yOf(c), wall), "crate $c is in the drum, at (${bodies.xOf(c)}, ${bodies.yOf(c)})")
                ps.forEach { p ->
                    assertTrue(run.inside(ps.xOf(p), ps.yOf(p), wall), "particle $p is in the drum, at (${ps.xOf(p)}, ${ps.yOf(p)})")
                }
            }
        }
        assertEquals(DrumScene.SPEED * 600 * dt, bodies.angleOf(run.drum), 0.01f, "the drum turned at its speed")
        assertTrue(highest < DrumScene.CENTER_Y, "a crate was carried up past the drum's middle: as high as $highest")
    }

    @Test
    fun inBulletsNoBulletPassesThePanesOrThePlateAndNoSparkTheLedge() {
        val run = BulletsScene.create() as BulletsScene.Run
        val bodies = run.world.rigidBodies
        val ps = run.world.particles
        val ledgeTop = BulletsScene.LEDGE_ROW * Scenes.TILE
        var swung = 0f
        var hits = 0
        repeat(600) {
            run.step(dt)
            for (b in run.bullets) {
                assertTrue(bodies.xOf(b) > Scenes.TILE && bodies.xOf(b) < BulletsScene.PLATE_X, "bullet $b went through, to ${bodies.xOf(b)}")
                if (bodies.xOf(b) > BulletsScene.PANE_X - 10f && kotlin.math.abs(bodies.velocityXOf(b)) < BulletsScene.SPEED / 2f) hits++
            }
            for (p in run.sparks) assertTrue(ps.yOf(p) > ledgeTop, "spark $p went through the ledge, to ${ps.yOf(p)}")
            for (pane in run.panes) swung = maxOf(swung, kotlin.math.abs(bodies.angleOf(pane)))
        }
        assertTrue(run.bullets.size >= 10, "it fired: ${run.bullets.size}")
        assertTrue(hits > 0, "the bullets were stopped")
        assertTrue(swung > 0.05f, "and knocked the panes swinging: $swung")
    }

    @Test
    fun inLaserTheBeamBouncesOffEveryMirrorAndOnlyEverCrossesOpenSpace() {
        val run = LaserScene.create() as LaserScene.Run
        val found = Overlaps()
        val bouncedOff = HashSet<RigidBody>()
        val endedOn = HashSet<HitKind>()
        var mostBounces = 0
        repeat(900) { step ->
            run.step(dt)
            assertTrue(run.beamPoints >= 2, "step $step: no beam")
            // the room is closed: the beam always ends on something
            assertTrue(run.endedOn != HitKind.NOTHING, "step $step: the beam went out of the room")
            for (i in 0 until run.beamPoints - 1) {
                val mx = (run.beamX[i] + run.beamX[i + 1]) / 2f
                val my = (run.beamY[i] + run.beamY[i + 1]) / 2f
                assertEquals(0, run.world.queryPoint(mx, my, found), "step $step: the beam's part $i passes through something at ($mx, $my)")
            }
            for (k in 0 until run.bounces) bouncedOff += run.bouncedOff[k]
            endedOn += run.endedOn
            mostBounces = maxOf(mostBounces, run.bounces)
        }
        assertEquals(run.mirrors.toSet(), bouncedOff, "it bounced off every mirror")
        assertTrue(mostBounces >= 3, "and off several in a row: $mostBounces")
        assertTrue(endedOn.containsAll(listOf(HitKind.RIGID_BODY, HitKind.SOFT_BODY, HitKind.SOLID)), "it ended on a crate, a blob and a wall: $endedOn")
        assertEquals(3, run.inBin.rigidBodyCount, "the box query finds the three crates in the bin")
        assertEquals(1, run.inBin.softBodyCount, "and the blob")
    }

    @Test
    fun inSorterEveryRedThingEndsInTheRedBinAndEveryBlueOneInTheBlue() {
        val run = SorterScene.create() as SorterScene.Run
        repeat(1500) { run.step(dt) }
        assertEquals(0, run.wrongBin, "nothing came into the wrong bin's sensor")
        assertTrue(run.redInRed >= 8 && run.blueInBlue >= 8, "the bins filled: red ${run.redInRed}, blue ${run.blueInBlue}")
        assertTrue(run.touchesBegun > 50, "and things touched: ${run.touchesBegun}")
        val divider = SorterScene.DIVIDER * Scenes.TILE
        for (item in run.items) {
            val x = run.itemX(item)
            val y = run.itemY(item)
            // what has fallen off the ramp is in its own colour's bin
            if (y > SorterScene.BIN_TOP) assertTrue(if (item.red) x < divider else x > divider, "a ${if (item.red) "red" else "blue"} ${item.kind} is in the wrong bin, at ($x, $y)")
        }
    }

    @Test
    fun allThirtyBodiesInTheBlobPileGetThroughTheFunnelIntoTheBin() {
        val run = PileScene.create() as PileScene.Run
        repeat(PileScene.COUNT * PileScene.EVERY + 600) { run.step(dt) }
        val bodies = run.world.softBodies
        assertEquals(PileScene.COUNT, bodies.size)
        val left = PileScene.BIN_LEFT * Scenes.TILE
        val right = (PileScene.BIN_RIGHT + 1) * Scenes.TILE
        for (body in run.blobs) {
            val x = bodies.centerXOf(body)
            val y = bodies.centerYOf(body)
            assertTrue(x in left..right && y > (PileScene.FUNNEL_TOP + PileScene.FUNNEL_ROWS) * Scenes.TILE, "body $body is stuck at ($x, $y)")
        }
    }
}
