package klyph.physics

import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * Checks that a step allocates nothing. Kotlin/Native has no allocation counter, but its garbage
 * collector reports how many objects each collection freed and how many it kept: collect, run the
 * block, collect again, and every object the block made is either freed by that second collection or
 * kept by it. A step that allocated even one object would show a few hundred of them over a few hundred
 * steps. The regular collection and the heap target are pushed out of the way while the block runs,
 * so no collection falls in the middle of it, and the test fails if one did anyway.
 */
@OptIn(NativeRuntimeApi::class, ExperimentalStdlibApi::class)
class AllocationTest {
    /**
     * Objects allocated while [block] runs, after running it twice so everything it grows has grown. Reading
     * the collector's statistics makes a few dozen objects of its own, so what an empty block counts is
     * taken off.
     */
    private fun measure(block: () -> Unit): Long {
        repeat(2) { block() }
        return count(block) - count {}
    }

    private fun count(block: () -> Unit): Long {
        val interval = GC.regularGCInterval
        val target = GC.targetHeapBytes
        val autotune = GC.autotune
        GC.autotune = false
        GC.regularGCInterval = 24.hours
        GC.targetHeapBytes = 1L shl 34
        try {
            GC.collect()
            val before = checkNotNull(GC.lastGCInfo)
            val keptBefore = before.sweepStatistics.values.sumOf { it.keptCount }
            block()
            GC.collect()
            val after = checkNotNull(GC.lastGCInfo)
            assertTrue(after.epoch == before.epoch + 1, "A collection ran while the block did, so it allocated.")
            val swept = after.sweepStatistics.values.sumOf { it.sweptCount }
            val keptAfter = after.sweepStatistics.values.sumOf { it.keptCount }
            return swept + (keptAfter - keptBefore)
        } finally {
            GC.regularGCInterval = interval
            GC.targetHeapBytes = target
            GC.autotune = autotune
        }
    }

    @Test
    fun aStepAllocatesNothing() {
        val world = PhysicsWorld()
        val ps = world.particles
        val cs = world.constraints
        val line = List(500) { ps.add(it.toFloat(), 0f) }
        for (p in 1 until 300) cs.distance(line[p - 1], line[p], compliance = 0.001f)
        for (p in 300 until 400) cs.rope(line[p - 1], line[p])
        for (p in 2 until 300) cs.bend(line[p - 2], line[p - 1], line[p], compliance = 0.01f)
        // a cloth whose links can tear, so the tear check runs every step
        world.cloth(600f, 0f, columns = 10, rows = 8, spacing = 6f, tearAt = 3f)
        cs.pin(line[0])
        cs.area(List(50) { ps.add(400f + 10f * kotlin.math.cos(it * 0.1257f), 10f * kotlin.math.sin(it * 0.1257f)) })
        // a floor and a moving solid under all of it, some particles round, so collisions run too
        world.solids = SolidGrid(40, 10, 16f).apply {
            for (c in 0 until columns) fill(c, 9)
            fill(5, 8, SolidShape.RISING)
            add(Solid.moving(100f, 100f, 140f, 120f))
        }
        repeat(50) { ps.add(200f + it, 50f, radius = 2f) }
        // blobs and colliding grains, so the broadphase and body collisions run too
        repeat(6) { world.softBody(SoftBodyShape.semiDisc(13f, 14, 8, 34f), 150f + it * 30f, 100f) }
        repeat(60) { ps.add(160f + it % 20 * 5f, 20f + it / 20 * 5f, radius = 2f, bodyCollision = BodyCollision.COLLIDE) }
        // and a hand dragging one of the blobs about
        world.grab(150f, 90f)
        var frame = 0
        val objects = measure {
            repeat(300) {
                frame++
                world.moveGrab(150f + (frame % 120) * 2f, 60f)
                world.step(1f / 60f)
            }
        }
        assertTrue(objects < 20, "300 steps allocated $objects objects")
    }

    @Test
    fun aStepOfRigidBodiesAmongBlobsAndGrainsAllocatesNothing() {
        val world = PhysicsWorld()
        world.solids = SolidGrid(40, 30, 16f).apply {
            for (c in 0 until columns) fill(c, 29)
            for (r in 0 until 30) {
                fill(0, r)
                fill(columns - 1, r)
            }
            fill(10, 28, SolidShape.RISING)
            add(Solid.moving(400f, 300f, 460f, 316f))
        }
        // a stack, a pile of every kind of shape, and a kinematic paddle sweeping through them
        val stack = List(6) { world.rigidBody(Shape.box(20f, 20f), 100f, 454f - it * 20f) }
        val shapes = arrayOf(Shape.box(16f, 16f), Shape.circle(8f), Shape.capsule(20f, 5f), Shape.polygon(-8f, 6f, 0f, -8f, 8f, 6f))
        repeat(40) { world.rigidBody(shapes[it % shapes.size], 250f + (it % 8) * 20f, 200f + (it / 8) * 24f, angle = it * 0.4f) }
        val paddle = world.rigidBody(Shape.box(60f, 8f), 300f, 380f, kind = BodyKind.KINEMATIC)
        world.rigidBodies.setAngularVelocity(paddle, 1f)
        // blobs and grains among them, and a rope tied to a crate, so the soft-rigid coupling runs too
        repeat(4) { world.softBody(SoftBodyShape.semiDisc(13f, 14, 8, 34f), 200f + it * 60f, 440f) }
        repeat(30) { world.particles.add(260f + it % 10 * 5f, 100f + it / 10 * 5f, radius = 2f, bodyCollision = BodyCollision.COLLIDE) }
        val knot = world.particles.add(100f, 330f)
        world.attach(knot, stack[0])
        // and joints of every kind, with limits, motors and a weld that breaks
        val arm = world.rigidBody(Shape.box(40f, 6f), 500f, 100f)
        val hinge = world.joints.hinge(arm, JointEnd.WORLD, 480f, 100f)
        world.joints.setLimits(hinge, -1f, 1f)
        world.joints.setMotor(hinge, 3f, 1e5f)
        val lift = world.rigidBody(Shape.box(30f, 6f), 560f, 100f)
        val slider = world.joints.slider(lift, JointEnd.WORLD, 560f, 100f, 0f, 1f)
        world.joints.setLimits(slider, -60f, 0f)
        world.joints.setMotor(slider, 20f, 1e5f)
        world.joints.weld(world.rigidBody(Shape.box(10f, 10f), 600f, 60f), JointEnd.WORLD, 600f, 55f, breakForce = 1e4f)
        world.joints.distance(arm, knot, 510f, 100f, 100f, 330f)
        val objects = measure { repeat(200) { world.step(1f / 60f) } }
        assertTrue(objects < 20, "200 steps allocated $objects objects")
    }

    @Test
    fun buildingTheBroadphaseAllocatesNothingOnceItHasGrown() {
        val bp = Broadphase()
        val random = kotlin.random.Random(1)
        val xs = FloatArray(2000) { random.nextFloat() * 1000f }
        val ys = FloatArray(2000) { random.nextFloat() * 1000f }
        val objects = measure {
            repeat(100) {
                bp.clear()
                for (i in xs.indices) bp.addMoving(i, xs[i], ys[i], 2f, 3f, 2f)
                bp.build()
            }
        }
        assertTrue(objects < 20, "100 builds allocated $objects objects")
    }
}
