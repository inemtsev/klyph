package samples.physics

import klyph.physics.Particles
import klyph.physics.chain
import klyph.physics.cloth
import klyph.physics.rope
import klyph.physics.PhysicsWorld
import samples.Sample
import samples.assertPrints
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ConstraintSamples {
    @Sample
    fun distance() {
        val world = PhysicsWorld()
        val anchor = world.particles.add(x = 0f, y = 0f, mass = Particles.STATIC)
        val bob = world.particles.add(x = 50f, y = 0f)
        // A rigid stick: the bob swings on it like a pendulum, always 50 from the anchor.
        val stick = world.constraints.distance(anchor, bob)
        repeat(30) { world.step(1.seconds / 60) }
        assertPrints(world.constraints.restLengthOf(stick), "50.0")
        assertTrue(world.constraints.strainOf(stick) in -0.01f..0.01f)
    }

    @Sample
    fun rope() {
        val world = PhysicsWorld()
        val anchor = world.particles.add(x = 0f, y = 0f, mass = Particles.STATIC)
        val weight = world.particles.add(x = 0f, y = 20f)
        // At most 50 long, slack when pushed: the weight falls until the rope goes taut.
        val rope = world.constraints.rope(anchor, weight, rest = 50f)
        repeat(60) { world.step(1.seconds / 60) }
        assertPrints(world.constraints.isRope(rope), "true")
        assertTrue(world.particles.yOf(weight) in 49f..51f)
    }

    @Sample
    fun pin() {
        val world = PhysicsWorld()
        val p = world.particles.add(x = 10f, y = 10f)
        // Held where it is now; move the pin and it follows.
        val pin = world.constraints.pin(p)
        world.constraints.movePin(pin, 30f, 10f)
        repeat(10) { world.step(1.seconds / 60) }
        assertTrue(world.particles.xOf(p) in 29f..31f)
        assertPrints(world.constraints.particleOf(pin) == p, "true")
    }

    @Sample
    fun area() {
        val world = PhysicsWorld()
        val ring = listOf(
            world.particles.add(x = 0f, y = 0f),
            world.particles.add(x = 20f, y = 0f),
            world.particles.add(x = 20f, y = 20f),
            world.particles.add(x = 0f, y = 20f),
        )
        // Keeps the four covering 400 units squared, however they are pushed about.
        val area = world.constraints.area(ring)
        assertPrints(world.constraints.restAreaOf(area), "400.0")
    }

    @Sample
    fun bend() {
        val world = PhysicsWorld()
        val a = world.particles.add(x = 0f, y = 0f, mass = Particles.STATIC)
        val b = world.particles.add(x = 20f, y = 0f, mass = Particles.STATIC)
        val c = world.particles.add(x = 40f, y = 0f)
        world.constraints.distance(b, c)
        // Keeps the angle at b straight, so c sticks out instead of hanging down.
        val bend = world.constraints.bend(a, b, c)
        repeat(30) { world.step(1.seconds / 60) }
        assertTrue(world.particles.yOf(c) in -1f..1f)
        assertTrue(world.constraints.angleOf(bend) in -0.05f..0.05f)
    }

    @Sample
    fun setTear() {
        val world = PhysicsWorld()
        val anchor = world.particles.add(x = 0f, y = 0f, mass = Particles.STATIC)
        val weight = world.particles.add(x = 0f, y = 10f, mass = 50f)
        val thread = world.constraints.distance(anchor, weight, compliance = 0.01f)
        // Breaks once stretched past one and a half times its rest length.
        world.constraints.setTear(thread, 1.5f)
        repeat(60) { world.step(1.seconds / 60) }
        assertPrints(world.constraints.isAlive(thread), "false")
        assertPrints(world.constraints.tornCount, "1")
    }
}

class StrandSamples {
    @Sample
    fun rope() {
        val world = PhysicsWorld()
        val rope = world.rope(fromX = 0f, fromY = 0f, toX = 100f, toY = 0f, segments = 10)
        assertPrints(rope, "Strand(particles=11, links=10, bends=0)")
        // Its start is pinned where it was made; the rest falls and hangs.
        assertPrints(world.particles.inverseMassOf(rope.first), "0.0")
        assertPrints(rope.restLength(world), "100.0")
    }

    @Sample
    fun chain() {
        val world = PhysicsWorld()
        val chain = world.chain(fromX = 0f, fromY = 0f, toX = 0f, toY = 60f, segments = 6)
        assertPrints(chain.bends.size, "5")
    }

    @Sample
    fun cloth() {
        val world = PhysicsWorld()
        // A curtain 12 particles across and 8 down, hanging from its top row.
        val curtain = world.cloth(left = 0f, top = 0f, columns = 12, rows = 8, spacing = 6f)
        assertPrints(curtain, "Cloth(12x8)")
        assertPrints(world.particles.inverseMassOf(curtain.particleAt(5, 0)), "0.0")
        assertPrints(world.particles.inverseMassOf(curtain.particleAt(5, 7)) > 0f, "true")
    }
}
