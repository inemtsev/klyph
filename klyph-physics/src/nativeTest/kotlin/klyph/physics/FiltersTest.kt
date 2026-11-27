package klyph.physics

import klyph.collision.CollisionLayers
import klyph.math.Vec2
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who collides with whom, sensors that only notice, and the events that say what touched. */
class FiltersTest {
    private val dt = TestBodies.DT
    private val tile = TestBodies.TILE

    /** A room 40 tiles wide and 30 high with a floor along its bottom two rows: its top at y = 448. */
    private fun room(config: PhysicsConfig = PhysicsConfig()) = PhysicsWorld(config).apply {
        solids = SolidGrid(40, 30, tile).apply {
            for (c in 0 until columns) for (r in 28 until 30) fill(c, r)
            for (r in 0 until 30) {
                fill(0, r)
                fill(columns - 1, r)
            }
        }
    }

    private val floor = 28 * 16f

    private fun steps(world: PhysicsWorld, seconds: Float, each: (Int) -> Unit = {}) = repeat((seconds / dt).toInt()) {
        world.step(dt)
        each(it)
    }

    private companion object {
        val RED = CollisionLayers.of(1)
        val BLUE = CollisionLayers.of(2)
        val GATE = CollisionLayers.of(3)
        val NOT_GATE = allBut(GATE)

        /** Every layer but [layers]. */
        fun allBut(layers: CollisionLayers) = CollisionLayers(CollisionLayers.ALL.bits and layers.bits.inv())
    }

    /** A touch as `"a b"`, each side as its kind and handle. */
    private fun key(touch: Touch) = "${touch.a} ${touch.b}"


    // --- filtering ---

    @Test
    fun filteredRigidPairsNeverCollide() {
        val world = room()
        val bodies = world.rigidBodies
        val random = Random(34)
        val categories = arrayOf(CollisionLayers.DEFAULT, RED, BLUE)
        repeat(80) {
            val shape = if (it % 2 == 0) Shape.box(10f + random.nextFloat() * 20f, 10f + random.nextFloat() * 20f) else Shape.circle(5f + random.nextFloat() * 10f)
            val b = world.rigidBody(shape, 60f + random.nextFloat() * 520f, 40f + random.nextFloat() * 300f, angle = random.nextFloat() * 6f)
            // a mask of any of the three, but never nothing; the level holds them whatever the mask
            var mask = CollisionLayers.NONE
            for (c in categories) if (random.nextBoolean()) mask = mask + c
            if (mask == CollisionLayers.NONE) mask = categories[random.nextInt(3)]
            bodies.setLayers(b, categories[random.nextInt(3)], mask)
        }
        var contacts = 0
        steps(world, 4f) {
            val c = world.rigidContacts
            for (k in 0 until c.count) {
                if (c.bodyB(k) < 0) continue
                val a = RigidBody(c.bodyA(k))
                val b = RigidBody(c.bodyB(k))
                contacts++
                assertTrue(PhysicsLayers.collide(bodies.layersOf(a), bodies.maskOf(a), bodies.layersOf(b), bodies.maskOf(b)), "bodies $a and $b collided, but their layers say they don't")
            }
        }
        assertTrue(contacts > 100, "the pile touched itself: $contacts contacts")
    }

    @Test
    fun aBodyFallsThroughAPlankItFiltersOutAndLandsOnOneItDoesNot() {
        val world = room()
        val bodies = world.rigidBodies
        val plank = world.rigidBody(Shape.box(400f, 8f), 320f, 300f, kind = BodyKind.STATIC)
        bodies.setLayers(plank, GATE, CollisionLayers.ALL)
        val through = world.rigidBody(Shape.box(20f, 20f), 250f, 200f)
        bodies.setLayers(through, RED, NOT_GATE)
        val stopped = world.rigidBody(Shape.box(20f, 20f), 390f, 200f)
        steps(world, 2f)
        assertEquals(floor - 10f, bodies.yOf(through), 1f, "the red box fell through the plank to the floor")
        assertEquals(300f - 4f - 10f, bodies.yOf(stopped), 1f, "the other one landed on it")
    }

    @Test
    fun aBlobAndSandFallThroughAPlankTheyFilterOut() {
        val world = room()
        val plank = world.rigidBody(Shape.box(500f, 8f), 320f, 300f, kind = BodyKind.STATIC)
        world.rigidBodies.setLayers(plank, GATE, CollisionLayers.ALL)
        val through = world.softBody(TestBodies.dome(), 200f, 250f)
        world.softBodies.setLayers(through, RED, NOT_GATE)
        val stopped = world.softBody(TestBodies.dome(), 440f, 250f)
        val ps = world.particles
        val grain = ps.add(320f, 200f, radius = 2f, bodyCollision = BodyCollision.COLLIDE)
        ps.setLayers(grain, RED, NOT_GATE)
        steps(world, 3f)
        assertTrue(world.softBodies.maxYOf(through) > floor - 3f, "the red blob reached the floor: ${world.softBodies.maxYOf(through)}")
        assertTrue(world.softBodies.maxYOf(stopped) < 300f, "the other sits on the plank: ${world.softBodies.maxYOf(stopped)}")
        assertTrue(ps.yOf(grain) > floor - 3f, "the red grain reached the floor: ${ps.yOf(grain)}")
        assertEquals(RED, world.softBodies.layersOf(through))
        assertEquals(NOT_GATE, world.softBodies.maskOf(through))
    }

    @Test
    fun twoBlobsThatFilterEachOtherOutPassThrough() {
        val world = room()
        val below = world.softBody(TestBodies.dome(), 320f, floor)
        world.softBodies.setLayers(below, BLUE, allBut(RED))
        val above = world.softBody(TestBodies.dome(), 320f, floor - 120f)
        world.softBodies.setLayers(above, RED, CollisionLayers.ALL)
        steps(world, 2f)
        assertTrue(world.softBodies.maxYOf(above) > floor - 3f, "the top blob sank through the other to the floor: ${world.softBodies.maxYOf(above)}")
    }

    @Test
    fun passingSolidsThingsFallThroughTheLevel() {
        val world = room()
        val box = world.rigidBody(Shape.box(20f, 20f), 200f, 300f)
        world.rigidBodies.setSolidCollision(box, SolidCollision.PASS)
        val blob = world.softBody(TestBodies.dome(), 320f, 330f)
        world.softBodies.setSolidCollision(blob, SolidCollision.PASS)
        val grain = world.particles.add(450f, 300f, radius = 2f, bodyCollision = BodyCollision.COLLIDE)
        world.particles.setSolidCollision(grain, SolidCollision.PASS)
        assertEquals(SolidCollision.PASS, world.rigidBodies.solidCollisionOf(box))
        assertEquals(SolidCollision.PASS, world.softBodies.solidCollisionOf(blob))
        assertEquals(SolidCollision.PASS, world.particles.solidCollisionOf(grain))
        steps(world, 1.5f)
        assertTrue(world.rigidBodies.yOf(box) > 30 * tile, "the box fell out of the bottom: ${world.rigidBodies.yOf(box)}")
        assertTrue(world.softBodies.minYOf(blob) > 30 * tile, "the blob fell out of the bottom: ${world.softBodies.minYOf(blob)}")
        assertTrue(world.particles.yOf(grain) > 30 * tile, "the grain fell out of the bottom: ${world.particles.yOf(grain)}")
    }

    @Test
    fun aBulletPassesAWallItFiltersOut() {
        val world = PhysicsWorld(PhysicsConfig(gravity = Vec2(0f, 0f)))
        val bodies = world.rigidBodies
        val wall = world.rigidBody(Shape.box(1f, 400f), 300f, 0f, kind = BodyKind.STATIC)
        bodies.setLayers(wall, GATE, CollisionLayers.ALL)
        val bullet = world.rigidBody(Shape.circle(2f), 0f, 0f)
        bodies.setLayers(bullet, RED, NOT_GATE)
        bodies.setMotion(bullet, Motion.BULLET)
        bodies.setVelocity(bullet, 5000f, 0f)
        repeat(10) { world.step(dt) }
        assertTrue(bodies.xOf(bullet) > 600f, "it went on through: ${bodies.xOf(bullet)}")
    }

    @Test
    fun narrowMasksKeepThingsInTheLevel() {
        val world = room()
        val box = world.rigidBody(Shape.box(20f, 20f), 200f, 300f)
        world.rigidBodies.setLayers(box, RED, RED)
        val blob = world.softBody(TestBodies.dome(), 320f, 330f)
        world.softBodies.setLayers(blob, RED, CollisionLayers.NONE)
        val grain = world.particles.add(450f, 300f, radius = 2f, bodyCollision = BodyCollision.COLLIDE)
        world.particles.setLayers(grain, RED, BLUE)
        steps(world, 1.5f)
        assertEquals(floor - 10f, world.rigidBodies.yOf(box), 1f, "the box landed on the floor")
        assertTrue(world.softBodies.maxYOf(blob) < floor + 3f, "the blob landed on the floor: ${world.softBodies.maxYOf(blob)}")
        assertTrue(world.particles.yOf(grain) < floor, "the grain landed on the floor: ${world.particles.yOf(grain)}")
    }

    @Test
    fun layersAreOneOrMoreOfAllThirtyTwo() {
        val world = room()
        val b = world.rigidBody(Shape.circle(5f), 100f, 100f)
        val p = world.particles.add(100f, 100f)
        assertFailsWith<IllegalArgumentException> { world.rigidBodies.setLayers(b, CollisionLayers.NONE, CollisionLayers.ALL) }
        assertFailsWith<IllegalArgumentException> { world.particles.setLayers(p, CollisionLayers.NONE, CollisionLayers.ALL) }
        world.rigidBodies.setLayers(b, CollisionLayers.of(30), CollisionLayers.ALL)
        world.particles.setLayers(p, CollisionLayers.of(31), CollisionLayers.ALL)
        assertEquals(CollisionLayers.of(30), world.rigidBodies.layersOf(b))
        assertEquals(CollisionLayers.of(31), world.particles.layersOf(p))
    }

    @Test
    fun bodiesOnLayersThirtyAndThirtyOneStandOnTheFloorAndOnEachOther() {
        val world = room()
        val bodies = world.rigidBodies
        val below = world.rigidBody(Shape.box(40f, 20f), 320f, floor - 10f)
        bodies.setLayers(below, CollisionLayers.of(30), CollisionLayers.of(31))
        val above = world.rigidBody(Shape.box(20f, 20f), 320f, floor - 60f)
        bodies.setLayers(above, CollisionLayers.of(31), CollisionLayers.of(30))
        steps(world, 2f)
        assertEquals(floor - 10f, bodies.yOf(below), 1f, "the lower box stands on the floor")
        assertEquals(floor - 30f, bodies.yOf(above), 1f, "the upper one stands on it")
    }

    // --- the level ---

    @Test
    fun settingTheSolidCollisionAgainLeavesABodyAsleep() {
        val world = room()
        val bodies = world.rigidBodies
        val crate = world.rigidBody(Shape.box(20f, 20f), 320f, floor - 10f)
        steps(world, 3f)
        assertTrue(bodies.isAsleep(crate), "the crate fell asleep on the floor")
        bodies.setSolidCollision(crate, SolidCollision.COLLIDE)
        assertTrue(bodies.isAsleep(crate), "setting what it already was leaves it asleep")
        bodies.setSolidCollision(crate, SolidCollision.PASS)
        assertFalse(bodies.isAsleep(crate), "a change wakes it")
        steps(world, 1.5f)
        assertTrue(bodies.yOf(crate) > 30 * tile, "and it falls out of the level: ${bodies.yOf(crate)}")
    }

    @Test
    fun aSleepingBodySetBackToCollideInTheFloorIsPushedOut() {
        val world = room(PhysicsConfig(gravity = Vec2(0f, 0f)))
        val bodies = world.rigidBodies
        // sunk 4 units into the floor; one sunk most of the way in stays there, as it would had it never passed
        val crate = world.rigidBody(Shape.box(20f, 20f), 320f, floor - 6f)
        bodies.setSolidCollision(crate, SolidCollision.PASS)
        steps(world, 3f)
        assertTrue(bodies.isAsleep(crate), "the crate fell asleep in the floor")
        bodies.setSolidCollision(crate, SolidCollision.COLLIDE)
        assertFalse(bodies.isAsleep(crate), "a change wakes it")
        steps(world, 1f)
        assertTrue(bodies.yOf(crate) <= floor - 10f + 1f, "it was pushed out of the floor: ${bodies.yOf(crate)}")
    }

    @Test
    fun bulletsThatPassSolidsCrossAWallOfThem() {
        val world = room(PhysicsConfig(gravity = Vec2(0f, 0f)))
        for (r in 0 until 28) world.solids!!.fill(20, r)
        val bodies = world.rigidBodies
        val stopped = world.rigidBody(Shape.circle(2f), 100f, 200f)
        val crossed = world.rigidBody(Shape.circle(2f), 100f, 260f)
        bodies.setSolidCollision(crossed, SolidCollision.PASS)
        val stoppedGrain = world.particles.add(100f, 320f, radius = 1f)
        val crossedGrain = world.particles.add(100f, 380f, radius = 1f)
        world.particles.setSolidCollision(crossedGrain, SolidCollision.PASS)
        for (b in listOf(stopped, crossed)) {
            bodies.setMotion(b, Motion.BULLET)
            bodies.setVelocity(b, vx = 6_000f, vy = 0f)
        }
        for (p in listOf(stoppedGrain, crossedGrain)) {
            world.particles.setMotion(p, Motion.BULLET)
            world.setVelocity(p, vx = 6_000f, vy = 0f)
        }
        repeat(3) { world.step(dt) }
        val wall = 20 * tile
        assertTrue(bodies.xOf(stopped) < wall, "the bullet stopped at the wall: ${bodies.xOf(stopped)}")
        assertTrue(bodies.xOf(crossed) > wall + tile, "the one passing solids went through: ${bodies.xOf(crossed)}")
        assertTrue(world.particles.xOf(stoppedGrain) < wall, "the bullet grain stopped at the wall: ${world.particles.xOf(stoppedGrain)}")
        val crossedX = world.particles.xOf(crossedGrain)
        assertTrue(crossedX > wall + tile, "the one passing solids went through: $crossedX")
    }

    @Test
    fun particlesThatPassSolidsFallThroughAMovingSolid() {
        val world = room()
        world.solids!!.add(Solid.moving(left = 280f, top = 200f, right = 360f, bottom = 216f))
        val held = world.particles.add(300f, 150f, radius = 2f)
        val passed = world.particles.add(340f, 150f, radius = 2f)
        world.particles.setSolidCollision(passed, SolidCollision.PASS)
        steps(world, 1f)
        assertTrue(world.particles.yOf(held) < 200f, "one rests on the moving solid: ${world.particles.yOf(held)}")
        assertTrue(world.particles.yOf(passed) > 216f, "the other fell through it: ${world.particles.yOf(passed)}")
    }

    @Test
    fun aSlotHandedOutAgainCollidesWithTheSolids() {
        val world = room()
        val box = world.rigidBody(Shape.box(20f, 20f), 200f, 300f)
        world.rigidBodies.setSolidCollision(box, SolidCollision.PASS)
        world.rigidBodies.remove(box)
        val next = world.rigidBody(Shape.box(20f, 20f), 200f, 300f)
        assertEquals(box, next, "the new box took the old one's slot")
        assertEquals(SolidCollision.COLLIDE, world.rigidBodies.solidCollisionOf(next))
        val drop = world.particles.add(300f, 300f)
        world.particles.setSolidCollision(drop, SolidCollision.PASS)
        world.particles.remove(drop)
        val nextDrop = world.particles.add(300f, 300f)
        assertEquals(drop, nextDrop, "the new particle took the old one's slot")
        assertEquals(SolidCollision.COLLIDE, world.particles.solidCollisionOf(nextDrop))
    }

    // --- sensors ---

    /** A static sensor, a box [width] by [height] with its middle at ([x], [y]). */
    private fun sensor(world: PhysicsWorld, x: Float, y: Float, width: Float = 200f, height: Float = 40f): RigidBody =
        world.rigidBody(Shape.box(width, height), x, y, kind = BodyKind.STATIC).also { world.rigidBodies.setResponse(it, ContactResponse.SENSE) }

    /** Every touch that came and went over the run, as `"in a b"` and `"out a b"` in order. */
    private fun sensorLog(world: PhysicsWorld, seconds: Float): List<String> {
        val log = ArrayList<String>()
        val e = world.events
        steps(world, seconds) {
            for (i in 0 until e.sensorsEntered.size) log += "in ${e.sensorsEntered[i].b}"
            for (i in 0 until e.sensorsExited.size) log += "out ${e.sensorsExited[i].b}"
        }
        return log
    }

    @Test
    fun aSensorNeverPushesAndSaysOnceWhatCameAndWent() {
        val world = room()
        val bodies = world.rigidBodies
        val s = sensor(world, 320f, 250f)
        val box = world.rigidBody(Shape.box(20f, 20f), 320f, 150f)
        val log = sensorLog(world, 2f)
        assertEquals(listOf("in ${TouchSide.of(box)}", "out ${TouchSide.of(box)}"), log)
        assertEquals(floor - 10f, bodies.yOf(box), 1f, "it fell straight through to the floor")
        assertEquals(320f, bodies.xOf(box), 0.01f, "without being pushed aside")
        assertEquals(250f, bodies.yOf(s), 0f)
        assertEquals(ContactResponse.SENSE, bodies.responseOf(s))
    }

    @Test
    fun aSensorNoticesBlobsAndLooseParticles() {
        val world = room()
        sensor(world, 320f, 250f)
        val blob = world.softBody(TestBodies.dome(), 280f, 180f)
        val grain = world.particles.add(380f, 150f, radius = 2f, bodyCollision = BodyCollision.COLLIDE)
        val log = sensorLog(world, 2f)
        assertEquals(1, log.count { it == "in ${TouchSide.of(blob)}" }, "$log")
        assertEquals(1, log.count { it == "out ${TouchSide.of(blob)}" }, "$log")
        assertEquals(1, log.count { it == "in ${TouchSide.of(grain)}" }, "$log")
        assertEquals(1, log.count { it == "out ${TouchSide.of(grain)}" }, "$log")
    }

    @Test
    fun aSensorOnlyNoticesWhatItsFilterLetsIn() {
        val world = room()
        val s = sensor(world, 320f, 250f, width = 400f)
        world.rigidBodies.setLayers(s, CollisionLayers.DEFAULT, RED + BLUE)
        val red = world.rigidBody(Shape.box(20f, 20f), 200f, 150f)
        world.rigidBodies.setLayers(red, RED, CollisionLayers.ALL)
        world.rigidBody(Shape.box(20f, 20f), 440f, 150f)
        val log = sensorLog(world, 2f)
        assertEquals(listOf("in ${TouchSide.of(red)}", "out ${TouchSide.of(red)}"), log)
    }

    @Test
    fun aBodyAsleepInASensorStaysInItUntilItIsRemoved() {
        val world = room()
        val bodies = world.rigidBodies
        val s = sensor(world, 320f, floor - 20f)
        val box = world.rigidBody(Shape.box(20f, 20f), 320f, floor - 60f)
        val log = sensorLog(world, 3f)
        assertTrue(bodies.isAsleep(box), "it fell asleep in the sensor")
        assertEquals(listOf("in ${TouchSide.of(box)}"), log)
        bodies.remove(box)
        world.step(dt)
        assertTrue(world.events.sensorsExited.has(TouchSide.of(s), TouchSide.of(box)), "removed, it left")
    }

    @Test
    fun raycastsPassThroughSensors() {
        val world = room()
        sensor(world, 320f, 250f)
        val hit = RayHit()
        assertTrue(world.raycast(320f, 100f, 320f, 600f, hit))
        assertEquals(HitKind.SOLID, hit.kind)
    }

    // --- contact events ---

    /** Every contact that began and ended over the run, as `"+ a b"` and `"- a b"`, with the step it was in. */
    private fun contactLog(world: PhysicsWorld, seconds: Float, each: (Int) -> Unit = {}): List<Pair<Int, String>> {
        val log = ArrayList<Pair<Int, String>>()
        val e = world.events
        steps(world, seconds) { step ->
            for (i in 0 until e.contactsBegun.size) log += step to "+ ${key(e.contactsBegun[i])}"
            for (i in 0 until e.contactsEnded.size) log += step to "- ${key(e.contactsEnded[i])}"
            each(step)
        }
        return log
    }

    @Test
    fun aBoxDroppedOnTheFloorBeginsOnceAndNeverEndsAsItSettlesAndSleeps() {
        val world = room()
        val box = world.rigidBody(Shape.box(20f, 20f), 320f, 300f)
        val log = contactLog(world, 3f).map { it.second }
        assertEquals(listOf("+ ${TouchSide.of(box)} ${TouchSide.SOLIDS}"), log)
        assertTrue(world.rigidBodies.isAsleep(box))
    }

    @Test
    fun aBouncingBallBeginsAndEndsOncePerBounce() {
        val world = room()
        val bodies = world.rigidBodies
        val ball = world.rigidBody(Shape.circle(10f), 320f, 200f, material = Material(restitution = 0.7f))
        val lowest = HashMap<Int, Float>()
        val log = contactLog(world, 3f) { step -> lowest[step] = bodies.yOf(ball) }
        val kinds = log.map { it.second.take(1) }
        // they alternate: in, out, in, out, and the last may stay in
        for ((i, k) in kinds.withIndex()) assertEquals(if (i % 2 == 0) "+" else "-", k, "event $i of $log")
        assertTrue(kinds.count { it == "+" } >= 3, "it bounced a few times: $log")
        // and each one began on the floor, or bounced off it within the step: a step falls 15 at most here
        for ((step, event) in log) if (event.startsWith("+")) assertTrue(lowest[step]!! > floor - 10f - 16f, "began at step $step at ${lowest[step]}, off the floor")
    }

    @Test
    fun aStackBeginsOnceAPairAndFallingAsleepEndsNothing() {
        val world = room()
        val bodies = world.rigidBodies
        val boxes = ArrayList<RigidBody>()
        for (row in 0 until 4) for (k in 0 until 4 - row) boxes += world.rigidBody(Shape.box(24f, 24f), 320f - (3 - row) * 12f + k * 24f, floor - 12f - row * 24f)
        val log = contactLog(world, 3f).map { it.second }
        assertTrue(boxes.all { bodies.isAsleep(it) }, "the stack fell asleep")
        assertTrue(log.none { it.startsWith("-") }, "nothing ended: $log")
        assertEquals(log.toSet().size, log.size, "no pair began twice: $log")
        // 4 on the floor, 3 + 2 + 1 rows each on two below, and side by side the ones that lean
        assertTrue(log.size >= 4 + 2 * 6, "the pairs that hold the stack up all began: $log")
        val top = boxes.last()
        bodies.remove(top)
        world.step(dt)
        assertTrue(world.events.contactsEnded.involves(TouchSide.of(top)), "removed, the top box's touches ended")
        assertEquals(0, world.events.contactsBegun.size)
    }

    @Test
    fun softBodiesBeginTouchingRigidBodiesEachOtherAndTheFloor() {
        val world = room()
        val bodies = world.rigidBodies
        // a wide flat cushion, so the crate stays on it
        val cushion = world.softBody(SoftBodyShape.box(160f, 24f, across = 16, down = 3, coreMass = 20f), 200f, floor)
        val crate = world.rigidBody(Shape.box(20f, 20f), 200f, floor - 60f)
        val other = world.softBody(TestBodies.dome(), 440f, floor)
        val falling = world.softBody(TestBodies.dome(), 440f, floor - 80f)
        val log = contactLog(world, 2f).map { it.second }
        assertTrue("+ ${TouchSide.of(crate)} ${TouchSide.of(cushion)}" in log, "$log")
        val (first, second) = if (other.index < falling.index) other to falling else falling to other
        assertTrue("+ ${TouchSide.of(first)} ${TouchSide.of(second)}" in log, "$log")
        assertTrue("+ ${TouchSide.of(cushion)} ${TouchSide.SOLIDS}" in log, "$log")
        // the crate is still on the cushion, which sags round it, so their touch hasn't ended
        assertTrue(bodies.xOf(crate) in world.softBodies.minXOf(cushion)..world.softBodies.maxXOf(cushion), "the crate is over the cushion")
        assertEquals("+", log.last { it.drop(2) == "${TouchSide.of(crate)} ${TouchSide.of(cushion)}" }.take(1), "$log")
        // each pair begins before it ends, and they take turns
        for (pair in log.map { it.drop(2) }.toSet()) {
            val turns = log.filter { it.drop(2) == pair }.map { it.take(1) }
            for ((i, t) in turns.withIndex()) assertEquals(if (i % 2 == 0) "+" else "-", t, "$pair: $turns")
        }
        // lifted away, the crate stops touching the cushion
        bodies.moveTo(crate, 200f, 100f)
        world.step(dt)
        assertTrue(world.events.contactsEnded.has(TouchSide.of(crate), TouchSide.of(cushion)))
    }

    @Test
    fun aWeldThatBreaksIsSaidToOnce() {
        val world = room()
        val box = world.rigidBody(Shape.box(20f, 20f), 320f, 200f)
        val weld = world.joints.weld(box, JointEnd.WORLD, 320f, 190f, breakForce = 100f)
        var breaks = 0
        steps(world, 1f) {
            for (i in 0 until world.events.brokenJointCount) {
                assertEquals(weld, world.events.brokenJointAt(i))
                breaks++
            }
        }
        assertEquals(1, breaks)
        assertFalse(world.joints.isAlive(weld))
    }

    @Test
    fun eventsFireOncePerBeginAndEndInARattlingPile() {
        val world = room()
        val bodies = world.rigidBodies
        val random = Random(35)
        repeat(40) {
            val shape = if (it % 2 == 0) Shape.box(14f + random.nextFloat() * 14f, 14f + random.nextFloat() * 14f) else Shape.circle(7f + random.nextFloat() * 7f)
            world.rigidBody(shape, 120f + random.nextFloat() * 400f, 100f + random.nextFloat() * 280f, material = Material(restitution = random.nextFloat() * 0.6f))
        }
        val blob = world.softBody(TestBodies.dome(), 320f, 120f)
        // what touches, as the events have said so far
        val touching = HashSet<String>()
        var begun = 0
        val e = world.events
        steps(world, 5f) { step ->
            // shaken now and then, so things come apart and together again
            if (step % 60 == 30) bodies.forEach { b -> bodies.setVelocity(b, (random.nextFloat() - 0.5f) * 300f, -300f * random.nextFloat()) }
            val seen = HashSet<String>()
            for (i in 0 until e.contactsBegun.size) {
                val key = key(e.contactsBegun[i])
                assertTrue(seen.add(key), "step $step: $key began twice in one step")
                assertTrue(touching.add(key), "step $step: $key began while it touched")
                begun++
            }
            for (i in 0 until e.contactsEnded.size) {
                val key = key(e.contactsEnded[i])
                assertTrue(touching.remove(key), "step $step: $key ended, but hadn't begun")
            }
            // every pair that pushed in the last substep is touching, as the events say
            val c = world.rigidContacts
            for (k in 0 until c.count) {
                if (c.push(k) <= 0f) continue
                val a = c.bodyA(k)
                val b = c.bodyB(k)
                val key = if (b < 0) {
                    "${TouchSide.of(RigidBody(a))} ${TouchSide.SOLIDS}"
                } else {
                    "${TouchSide.of(RigidBody(minOf(a, b)))} ${TouchSide.of(RigidBody(maxOf(a, b)))}"
                }
                assertTrue(key in touching, "step $step: $key pushed, but the events don't say it touches")
            }
        }
        assertTrue(begun > 100, "plenty began: $begun")
        assertTrue(touching.any { it.contains("${TouchSide.of(blob)}") }, "and the blob is touching something: $touching")
    }

    @Test
    fun removingABodyAndReusingItsSlotBeforeTheNextStepReportsBothEndAndBegin() {
        val world = room()
        val bodies = world.rigidBodies
        val a = world.rigidBody(Shape.box(40f, 20f), 320f, floor - 10f, kind = BodyKind.STATIC)
        val b = world.rigidBody(Shape.box(20f, 20f), 320f, floor - 30f)
        steps(world, 1f)
        assertEquals(0, world.events.contactsBegun.size)

        bodies.remove(b)
        val c = world.rigidBody(Shape.box(20f, 20f), 320f, floor - 30f)
        assertEquals(b, c, "c reuses b's slot")
        world.step(dt)

        assertTrue(world.events.contactsEnded.has(TouchSide.of(a), TouchSide.of(b)), "removed body's touch ended")
        assertTrue(world.events.contactsBegun.has(TouchSide.of(a), TouchSide.of(c)), "newcomer's touch began")
    }

    @Test
    fun removingABodyInASensorAndReusingItsSlotReportsExitAndEntry() {
        val world = room()
        val bodies = world.rigidBodies
        val s = sensor(world, 320f, floor - 20f)
        val b = world.rigidBody(Shape.box(20f, 20f), 320f, floor - 20f, kind = BodyKind.STATIC)
        world.step(dt)
        assertTrue(world.events.sensorsEntered.has(TouchSide.of(s), TouchSide.of(b)))

        bodies.remove(b)
        val c = world.rigidBody(Shape.box(20f, 20f), 320f, floor - 20f, kind = BodyKind.STATIC)
        assertEquals(b, c, "c reuses b's slot")
        world.step(dt)

        assertTrue(world.events.sensorsExited.has(TouchSide.of(s), TouchSide.of(b)), "removed body exited sensor")
        assertTrue(world.events.sensorsEntered.has(TouchSide.of(s), TouchSide.of(c)), "newcomer entered sensor")
    }

    @Test
    fun clearingBodiesDropsOldTouchesSoNewBodiesBeginFresh() {
        val world = room()
        val bodies = world.rigidBodies
        val a = world.rigidBody(Shape.box(40f, 20f), 320f, floor - 10f, kind = BodyKind.STATIC)
        val b = world.rigidBody(Shape.box(20f, 20f), 320f, floor - 30f)
        steps(world, 1f)

        bodies.clear()
        val nextA = world.rigidBody(Shape.box(40f, 20f), 320f, floor - 10f, kind = BodyKind.STATIC)
        val nextB = world.rigidBody(Shape.box(20f, 20f), 320f, floor - 30f)
        assertEquals(a, nextA)
        assertEquals(b, nextB)
        world.step(dt)

        assertTrue(world.events.contactsBegun.has(TouchSide.of(nextA), TouchSide.of(nextB)))
        assertEquals(0, world.events.contactsEnded.size)
    }

    @Test
    fun removingASoftBodyAndReusingItsSlotReportsBothEndAndBegin() {
        val world = room()
        val first = world.softBody(TestBodies.dome(), 320f, floor)
        steps(world, 0.5f)
        assertEquals(0, world.events.contactsBegun.size)

        world.softBodies.remove(first)
        val second = world.softBody(TestBodies.dome(), 320f, floor)
        assertEquals(first, second, "second reuses first's slot")
        world.step(dt)

        assertTrue(world.events.contactsEnded.has(TouchSide.of(first), TouchSide.SOLIDS))
        assertTrue(world.events.contactsBegun.has(TouchSide.of(second), TouchSide.SOLIDS))
    }

    @Test
    fun removingAParticleInASensorAndReusingItsSlotReportsExitAndEntry() {
        val world = room()
        val s = sensor(world, 320f, floor - 20f)
        val p = world.particles.add(320f, floor - 20f, mass = Particles.STATIC, radius = 3f, bodyCollision = BodyCollision.COLLIDE)
        world.step(dt)
        assertTrue(world.events.sensorsEntered.has(TouchSide.of(s), TouchSide.of(p)))

        world.particles.remove(p)
        val q = world.particles.add(320f, floor - 20f, mass = Particles.STATIC, radius = 3f, bodyCollision = BodyCollision.COLLIDE)
        assertEquals(p, q, "q reuses p's slot")
        world.step(dt)

        assertTrue(world.events.sensorsExited.has(TouchSide.of(s), TouchSide.of(p)))
        assertTrue(world.events.sensorsEntered.has(TouchSide.of(s), TouchSide.of(q)))
    }
}
