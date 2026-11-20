package klyph.physics.demo

import klyph.physics.Particle
import klyph.physics.PhysicsWorld
import klyph.physics.Ring
import klyph.physics.SoftBody
import klyph.physics.centerXOf
import klyph.physics.length
import klyph.physics.maxXOf
import klyph.physics.maxYOf
import klyph.physics.minXOf
import klyph.physics.minYOf
import klyph.physics.restLength
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/** Runs the scenes made of blobs without a window and checks nothing breaks. */
class SceneTest {
    /** The step the app takes, to the last bit: klyph's 1/60 s is 16 666 666 ns, a hair under `1f / 60f`. */
    private val dt = 16_666_666 / 1e9f
    private val roomWidth = Scenes.COLS * Scenes.TILE
    private val roomHeight = Scenes.ROWS * Scenes.TILE

    @Test
    fun everyBlobSceneRunsForTenSecondsWithNothingLostOrTorn() {
        for (scene in Scenes.all) {
            val run = scene.create() as? BlobRun ?: continue
            check(scene.name, run, steps = 600)
        }
    }

    @Test
    fun theBlobGardenRunsForTenMinutesWithNothingLostOrTorn() {
        check(BlobGardenScene.name, BlobGardenScene.create() as BlobRun, steps = 36_000)
    }

    @Test
    fun theBlobPileSettlesWithNoBodyInsideAnother() {
        val run = PileScene.create() as PileScene.Run
        repeat(1200) { run.step(dt) }
        val bodies = run.world.softBodies
        val ps = run.world.particles
        assertTrue(bodies.size == PileScene.COUNT, "all of them fell: ${bodies.size}")
        for (a in run.blobs) for (b in run.blobs) {
            if (a == b) continue
            val ring = bodies.ringOf(b)
            for ((i, p) in bodies.particlesOf(a).withIndex()) {
                if (!contains(ring, ps.xOf(p), ps.yOf(p))) continue
                val depth = sqrt(nearestPoint(ring, ps.xOf(p), ps.yOf(p), nearest))
                assertTrue(depth < 1.5f, "particle $i of body $a is $depth inside body $b")
            }
        }
        for (b in run.blobs) assertTrue(abs(bodies.meanVelocityYOf(b)) < 30f, "body $b has settled: ${bodies.meanVelocityYOf(b)}")
    }

    @Test
    fun inGripAndBounceIceSlidesGripHoldsAndEachBallBouncesAsItsRestitutionSays() {
        val run = GripAndBounceScene.create() as GripAndBounceScene.Run
        val bodies = run.world.softBodies
        val ps = run.world.particles
        val start = run.blobs.map { bodies.centerXOf(it) }
        val dropY = ps.yOf(run.balls[0])
        val floor = (Scenes.ROWS - 2) * Scenes.TILE
        val resting = floor - ps.radiusOf(run.balls[0])
        // the highest each ball gets after it first bounces
        val landed = BooleanArray(3)
        val apex = FloatArray(3) { Float.MAX_VALUE }
        // a third of a second in, before either slider has reached the bottom of its ramp
        var early = emptyList<Float>()
        repeat(GripAndBounceScene.EVERY) { step ->
            run.step(dt)
            if (step == 19) early = run.blobs.indices.map { bodies.centerXOf(run.blobs[it]) - start[it] }
            for (i in 0 until 3) {
                val p = run.balls[i]
                // landed once it's on its way up: at 900 units/s a step can skip right past the floor's level
                if (run.world.velocityYOf(p) < -1f) landed[i] = true
                if (landed[i]) apex[i] = minOf(apex[i], ps.yOf(p))
            }
        }
        val slid = run.blobs.indices.map { bodies.centerXOf(run.blobs[it]) - start[it] }
        assertTrue(abs(slid[2]) < 1f, "grip holds: ${slid[2]}")
        assertTrue(slid[1] > 10f, "the default slides: ${slid[1]}")
        assertTrue(early[0] > early[1] * 1.3f, "ice slides faster: ${early[0]} against ${early[1]} after a third of a second")
        val drop = resting - dropY
        val heights = apex.map { if (it == Float.MAX_VALUE) 0f else resting - it }
        assertTrue(heights[0] < 0.05f * drop, "restitution 0 doesn't bounce: ${heights[0]}")
        for (i in 1..2) {
            val e = GripAndBounceScene.RESTITUTIONS[i]
            // e² of the drop, less what the air (the world's damping) takes on the way down and up
            assertTrue(heights[i] in e * e * drop * 0.65f..e * e * drop * 1.02f, "ball $i bounces ${heights[i]} of $drop, e² is ${e * e}")
        }
        // and back they go
        run.step(dt)
        assertTrue(abs(bodies.centerXOf(run.blobs[0]) - start[0]) < 1f, "put back after ${GripAndBounceScene.EVERY} steps")
    }

    @Test
    fun theRopeBridgeCarriesItsBlobAndTheRopesKeepTheirLength() {
        val run = RopeSwingScene.create() as RopeSwingScene.Run
        repeat(600) { run.step(dt) }
        val world = run.world
        val bodies = world.softBodies
        val blob = run.blobs[0]
        val lowest = run.bridge.particles.maxOf { world.particles.yOf(it) }
        assertTrue(bodies.maxYOf(blob) < lowest + 4f, "the blob is on the bridge, not under it: ${bodies.maxYOf(blob)} against $lowest")
        // The bridge carries a blob almost 30 times as heavy as one of its links, and stretches a little
        // under it; the 1% a rope keeps at rest is checked in the engine's tests.
        for ((name, strand, most) in listOf(Triple("rope", run.rope, 0.02f), Triple("chain", run.chain, 0.02f), Triple("bridge", run.bridge, 0.04f))) {
            val stretch = strand.length(world) / strand.restLength(world) - 1f
            assertTrue(stretch < most, "the $name stretched ${stretch * 100}%")
        }
    }

    @Test
    fun inTheClothTheBallTearsThroughTheNetAndTheNetIsHungBackUp() {
        val run = ClothScene.create() as ClothScene.Run
        repeat(ClothScene.EVERY) { run.step(dt) }
        val ps = run.world.particles
        ps.forEach { p -> assertTrue(ps.xOf(p).isFinite() && ps.yOf(p).isFinite(), "particle $p") }
        assertTrue(run.world.constraints.tornCount > 0, "the net tore")
        val floor = (Scenes.ROWS - 2) * Scenes.TILE
        assertTrue(ps.yOf(run.ball) > floor - 12f, "and the ball went through it, down to the floor: ${ps.yOf(run.ball)}")
        run.step(dt)
        assertTrue(run.world.constraints.tornCount == 0, "a fresh net, whole again")
    }

    @Test
    fun theFountainReusesItsHandlesSoItsStoreStopsGrowing() {
        val run = FountainScene.create() as FountainScene.Run
        val ps = run.world.particles
        // Every handle ever handed out: a particle is added before a step and lives through it, so
        // looking after every step sees them all. How many there are is how big the store has grown.
        val handles = HashSet<Particle>()
        fun step() {
            run.step(dt)
            ps.forEach { handles += it }
        }
        repeat(600) { step() }
        val end = handles.size
        assertTrue(ps.size in 100..end, "about a second and a half of particles alive: ${ps.size}")
        repeat(600) { step() }
        assertTrue(handles.size <= end + FountainScene.PER_STEP * 10, "the store stopped growing: ${handles.size} after $end")
        ps.forEach { p -> assertTrue(ps.xOf(p).isFinite() && ps.yOf(p).isFinite()) }
    }

    @Test
    fun theSpringsSwingForAMinuteWithoutBlowingUp() {
        val run = SpringsScene.create() as SpringsScene.Run
        repeat(3600) { run.step(dt) }
        val ps = run.world.particles
        ps.forEach { p -> assertTrue(ps.xOf(p).isFinite() && ps.yOf(p).isFinite(), "particle $p") }
        ps.forEach { p -> assertTrue(ps.yOf(p) in 0f..roomHeight, "particle $p is still in the room: y = ${ps.yOf(p)}") }
    }

    @Test
    fun inTheSpringsTheStiffChainSticksOutTheSoftOneDroopsAndThePlainOneHangs() {
        val run = SpringsScene.create() as SpringsScene.Run
        repeat(300) { run.step(dt) }
        val ps = run.world.particles
        val drop = run.chains.map { ps.yOf(it.last()) - ps.yOf(it.first()) }
        val length = (run.chains[0].size - 1) * 8f
        assertTrue(drop[0] < 0.25f * length, "stiff: the tip is ${drop[0]} below the clamp")
        assertTrue(drop[1] > drop[0] + 5f && drop[1] < drop[2], "soft: ${drop[1]}")
        assertTrue(drop[2] > 0.7f * length, "plain: ${drop[2]}")
    }

    /**
     * Steps [run] [steps] times. Every step, every blob must be finite and inside the room. A blob may
     * snag on a corner and stretch for a moment, but not stay stretched: that is tearing. Its core may
     * lag outside its skin on a hard landing, but not stay out: then it went through a wall alone.
     */
    private fun check(name: String, run: BlobRun, steps: Int) {
        // Room for every body a run may add while it runs, as the pile does.
        val stretchedFor = IntArray(MAX_BODIES)
        val coreOutFor = IntArray(MAX_BODIES)
        repeat(steps) { step ->
            (run as SceneRun).step(dt)
            val bodies = run.world.softBodies
            for ((i, body) in run.blobs.withIndex()) {
                val where = "$name, blob $i, step $step"
                assertTrue(bodies.isFinite(body), "$where: not finite")
                val inRoom = bodies.minXOf(body) >= 0f && bodies.maxXOf(body) <= roomWidth && bodies.minYOf(body) >= 0f && bodies.maxYOf(body) <= roomHeight
                assertTrue(inRoom, "$where: left the room")
                val stretch = bodies.maxEdgeStretchOf(body)
                stretchedFor[i] = if (stretch > MAX_STRETCH) stretchedFor[i] + 1 else 0
                assertTrue(stretchedFor[i] <= MAX_STRETCHED_STEPS, "$where: torn, stretched $stretch times for ${stretchedFor[i]} steps")
                coreOutFor[i] = if (coreDistanceOutside(run.world, body) > MAX_CORE_OUTSIDE) coreOutFor[i] + 1 else 0
                assertTrue(coreOutFor[i] <= MAX_STRETCHED_STEPS, "$where: the core left its skin for ${coreOutFor[i]} steps")
            }
        }
    }

    private val nearest = FloatArray(2)

    /** How far the core of [body] is outside its outline, or 0 when it's inside. */
    private fun coreDistanceOutside(world: PhysicsWorld, body: SoftBody): Float {
        val core = world.softBodies.coreOf(body)
        val x = world.particles.xOf(core)
        val y = world.particles.yOf(core)
        val ring = world.softBodies.ringOf(body)
        if (contains(ring, x, y)) return 0f
        return sqrt(nearestPoint(ring, x, y, nearest))
    }

    /**
     * Whether the point ([px], [py]) is inside [ring], by winding number, as the engine decides it: a
     * point the outline winds around at all is inside, so a folded part of a body is solid.
     */
    private fun contains(ring: Ring, px: Float, py: Float): Boolean {
        var winding = 0
        for (i in 0 until ring.size) {
            val j = if (i + 1 == ring.size) 0 else i + 1
            val y0 = ring.yAt(i)
            val y1 = ring.yAt(j)
            if ((y0 <= py) != (y1 <= py)) {
                val crossingX = ring.xAt(i) + (py - y0) / (y1 - y0) * (ring.xAt(j) - ring.xAt(i))
                if (crossingX > px) winding += if (y1 > y0) 1 else -1
            }
        }
        return winding != 0
    }

    /** Finds the point on [ring] nearest ([px], [py]), writes it to `out[0]` and `out[1]`, and returns its squared distance. */
    private fun nearestPoint(ring: Ring, px: Float, py: Float, out: FloatArray): Float {
        var best = Float.MAX_VALUE
        for (e in 0 until ring.size) {
            val f = if (e + 1 == ring.size) 0 else e + 1
            val ex = ring.xAt(f) - ring.xAt(e)
            val ey = ring.yAt(f) - ring.yAt(e)
            val lengthSquared = ex * ex + ey * ey
            val t = if (lengthSquared < 1e-9f) 0f else (((px - ring.xAt(e)) * ex + (py - ring.yAt(e)) * ey) / lengthSquared).coerceIn(0f, 1f)
            val cx = ring.xAt(e) + ex * t
            val cy = ring.yAt(e) + ey * t
            val d = (px - cx) * (px - cx) + (py - cy) * (py - cy)
            if (d < best) {
                best = d
                out[0] = cx
                out[1] = cy
            }
        }
        return best
    }

    private companion object {
        const val MAX_BODIES = 64

        /** A core this far outside its skin is no longer just lagging behind it. */
        const val MAX_CORE_OUTSIDE = 3f

        /** An outline edge more than this many times its rest length is over-stretched. */
        const val MAX_STRETCH = 2.5f

        /** Half a second: longer than this over-stretched, or with its core out, and a body is broken, not just bumped. */
        const val MAX_STRETCHED_STEPS = 30
    }
}
