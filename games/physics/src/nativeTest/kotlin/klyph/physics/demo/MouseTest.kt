package klyph.physics.demo

import klyph.physics.Particle
import klyph.physics.PhysicsWorld
import klyph.physics.RigidBody
import klyph.physics.centerXOf
import klyph.physics.centerYOf
import klyph.physics.maxEdgeStretchOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** The mouse in every scene: the left button grabs and drags, the right one spawns. */
class MouseTest {
    private val dt = 16_666_666 / 1e9f
    private val roomWidth = Scenes.COLS * Scenes.TILE
    private val roomHeight = Scenes.ROWS * Scenes.TILE

    /** The particle that can move nearest the middle of the room: something to aim the mouse at. */
    private fun movableNearTheMiddle(world: PhysicsWorld): Particle? {
        val ps = world.particles
        var best: Particle? = null
        var bestSquared = Float.MAX_VALUE
        ps.forEach { p ->
            if (ps.inverseMassOf(p) == 0f) return@forEach
            val dx = ps.xOf(p) - roomWidth / 2f
            val dy = ps.yOf(p) - roomHeight / 2f
            if (dx * dx + dy * dy < bestSquared) {
                best = p
                bestSquared = dx * dx + dy * dy
            }
        }
        return best
    }

    /** The dynamic rigid body nearest the middle of the room, or `null`. */
    private fun bodyNearTheMiddle(world: PhysicsWorld): RigidBody? {
        val bodies = world.rigidBodies
        var best: RigidBody? = null
        var bestSquared = Float.MAX_VALUE
        bodies.forEach { b ->
            if (bodies.inverseMassOf(b) == 0f) return@forEach
            val dx = bodies.xOf(b) - roomWidth / 2f
            val dy = bodies.yOf(b) - roomHeight / 2f
            if (dx * dx + dy * dy < bestSquared) {
                best = b
                bestSquared = dx * dx + dy * dy
            }
        }
        return best
    }

    private fun assertAllFinite(name: String, world: PhysicsWorld) {
        val bodies = world.rigidBodies
        bodies.forEach { b ->
            assertTrue(bodies.xOf(b).isFinite() && bodies.yOf(b).isFinite() && bodies.angleOf(b).isFinite(), "$name: body $b went to (${bodies.xOf(b)}, ${bodies.yOf(b)})")
        }
        val ps = world.particles
        ps.forEach { p ->
            assertTrue(ps.xOf(p).isFinite() && ps.yOf(p).isFinite(), "$name: particle $p went to (${ps.xOf(p)}, ${ps.yOf(p)})")
        }
    }

    @Test
    fun inEverySceneTheMouseGrabsSomethingAndFlingingItAboutBreaksNothing() {
        val sandbox = Sandbox(Scenes.all)
        for (i in 0 until sandbox.sceneCount) {
            sandbox.select(i)
            val name = sandbox.scene.name
            repeat(60) { sandbox.fixedStep(dt) }
            val world = sandbox.run.world
            val ps = world.particles
            val p = movableNearTheMiddle(world)
            val b = bodyNearTheMiddle(world)
            // a scene of rigid bodies only has no particles: grab one of its bodies by its middle
            val grabbed = if (p != null) sandbox.grab(ps.xOf(p), ps.yOf(p)) else sandbox.grab(world.rigidBodies.xOf(b!!), world.rigidBodies.yOf(b))
            assertTrue(grabbed, "$name: nothing grabbed at particle $p or body $b")
            // a mouse thrown about the whole window, somewhere new every frame
            val random = Random(i)
            repeat(240) {
                sandbox.moveGrab(random.nextFloat() * roomWidth, random.nextFloat() * roomHeight)
                sandbox.fixedStep(dt)
                assertAllFinite(name, world)
            }
            sandbox.release()
            assertNull(world.hand.particleOrNull, "$name: let go")
            assertNull(world.hand.bodyOrNull, "$name: let go")
            repeat(180) { sandbox.fixedStep(dt) }
            assertAllFinite(name, world)
            val bodies = world.softBodies
            bodies.forEach { body ->
                // 2.5, not 1: squeezed at the bottom of a pile, a body can be held stretched to twice its
                // edges by friction, grabbed or not (see the pile's own test in CollisionTest)
                assertTrue(bodies.maxEdgeStretchOf(body) < 2.5f, "$name: body $body is still torn, ${bodies.maxEdgeStretchOf(body)}")
                val x = bodies.centerXOf(body)
                val y = bodies.centerYOf(body)
                assertTrue(x in 0f..roomWidth && y in 0f..roomHeight, "$name: body $body was dragged out of the room, to ($x, $y)")
            }
        }
    }

    @Test
    fun aRightClickAddsSomethingInEveryScene() {
        val sandbox = Sandbox(Scenes.all)
        for (i in 0 until sandbox.sceneCount) {
            sandbox.select(i)
            sandbox.fixedStep(dt)
            val world = sandbox.run.world
            val before = world.particles.size + world.rigidBodies.size
            sandbox.spawn(roomWidth / 2f, 60f)
            assertTrue(world.particles.size + world.rigidBodies.size > before, "${sandbox.scene.name}: nothing spawned")
            repeat(120) { sandbox.fixedStep(dt) }
            assertAllFinite(sandbox.scene.name, world)
        }
    }

    @Test
    fun nothingIsSpawnedInsideAWallAndNoMoreThanTheLimit() {
        val sandbox = Sandbox(Scenes.all)
        sandbox.select(Scenes.all.indexOf(RopeSwingScene))
        val world = sandbox.run.world
        val bodies = world.softBodies.size
        sandbox.spawn(8f, 100f)
        assertEquals(bodies, world.softBodies.size, "not inside the left wall")
        repeat(100) { sandbox.spawn(40f + it % 30 * 18f, 40f) }
        assertEquals(bodies + Scenes.MAX_SPAWNED, world.softBodies.size)
    }

    @Test
    fun theScriptedHandGrabsLiftsSwingsAndLetsGo() {
        assertNull(ScriptedHand.parse(null))
        assertNull(ScriptedHand.parse("320"))
        assertNull(ScriptedHand.parse("a,b"))
        assertEquals(320f, ScriptedHand.parse(" 320 , 200 ")!!.x)
        val sandbox = Sandbox(Scenes.all)
        sandbox.select(Scenes.all.indexOf(ClothScene))
        // the curtain's free bottom edge, which can be lifted
        val hand = ScriptedHand(280f, 216f)
        var heldAt100: Particle? = null
        var lowestY = Float.MAX_VALUE
        while (sandbox.stepCount < ScriptedHand.RELEASE_AT + 30) {
            hand.drive(sandbox)
            sandbox.fixedStep(dt)
            val world = sandbox.run.world
            val held = world.hand.particleOrNull
            if (sandbox.stepCount == 100L) heldAt100 = held
            if (held != null) lowestY = minOf(lowestY, world.particles.yOf(held))
        }
        assertNotNull(heldAt100, "it held something")
        assertTrue(lowestY < 216f - ScriptedHand.LIFT / 2f, "and lifted it: up to $lowestY")
        assertNull(sandbox.run.world.hand.particleOrNull, "and let go")
    }
}
