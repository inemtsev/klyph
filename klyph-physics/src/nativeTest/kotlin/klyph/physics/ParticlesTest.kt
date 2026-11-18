package klyph.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParticlesTest {
    private val particles = Particles(initialCapacity = 2)

    @Test
    fun aNewParticleIsAliveAtRestWhereItWasPut() {
        val p = particles.add(3f, 4f, mass = 2f, radius = 1.5f)
        assertTrue(particles.isAlive(p))
        assertEquals(3f, particles.xOf(p))
        assertEquals(4f, particles.yOf(p))
        assertEquals(0f, particles.movedXOf(p))
        assertEquals(0.5f, particles.inverseMassOf(p))
        assertEquals(1.5f, particles.radiusOf(p))
        assertEquals(1, particles.size)
    }

    @Test
    fun theStoreGrowsByDoublingAndKeepsWhatItHad() {
        val handles = (0 until 100).map { particles.add(it.toFloat(), -it.toFloat()) }
        assertEquals((0 until 100).toList(), handles.map { it.index })
        for (p in handles) {
            assertEquals(p.index.toFloat(), particles.xOf(p))
            assertEquals(-p.index.toFloat(), particles.yOf(p))
        }
        assertEquals(100, particles.size)
    }

    @Test
    fun aRemovedHandleIsHandedOutAgain() {
        val a = particles.add(0f, 0f)
        val b = particles.add(1f, 0f)
        particles.remove(a)
        assertFalse(particles.isAlive(a))
        assertEquals(1, particles.size)
        val c = particles.add(5f, 5f)
        assertEquals(a, c, "the free handle is reused")
        assertTrue(particles.isAlive(c))
        assertEquals(5f, particles.xOf(c))
        assertEquals(2, particles.end, "no new slot was needed")
        assertTrue(particles.isAlive(b))
    }

    @Test
    fun removingADeadParticleTwiceDoesNotHandItOutTwice() {
        val a = particles.add(0f, 0f)
        particles.remove(a)
        particles.remove(a)
        val b = particles.add(0f, 0f)
        val c = particles.add(0f, 0f)
        assertTrue(b != c)
    }

    @Test
    fun aStaticParticleHasNoInverseMass() {
        val p = particles.add(0f, 0f, mass = Particles.STATIC)
        assertEquals(0f, particles.inverseMassOf(p))
    }

    @Test
    fun aParticleNeedsAMassAndARadiusThatMakeSense() {
        assertFailsWith<IllegalArgumentException> { particles.add(0f, 0f, mass = 0f) }
        assertFailsWith<IllegalArgumentException> { particles.add(0f, 0f, radius = -1f) }
    }

    @Test
    fun movingKeepsTheVelocityAndSettingTheVelocityKeepsThePosition() {
        val p = particles.add(0f, 0f)
        particles.setVelocity(p.index, 240f, -480f, h = 1f / 240f)
        assertEquals(1f, particles.movedXOf(p), 0.0001f)
        assertEquals(-2f, particles.movedYOf(p), 0.0001f)
        particles.moveTo(p, 10f, 10f)
        assertEquals(10f, particles.xOf(p))
        assertEquals(1f, particles.movedXOf(p), 0.0001f)
        assertEquals(-2f, particles.movedYOf(p), 0.0001f)
    }

    @Test
    fun clearingStartsTheHandlesAgain() {
        particles.add(0f, 0f)
        particles.add(0f, 0f)
        particles.clear()
        assertEquals(0, particles.size)
        assertEquals(0, particles.add(0f, 0f).index)
    }

    @Test
    fun aParticleIsPlacedWithTheSpeedOfWhereItCameFrom() {
        val p = particles.add(0f, 0f)
        particles.set(p.index, 10f, 20f, previousX = 4f, previousY = 25f)
        assertEquals(10f, particles.xOf(p))
        assertEquals(6f, particles.movedXOf(p), 0.001f)
        assertEquals(-5f, particles.movedYOf(p), 0.001f)
    }

    @Test
    fun pushingAParticleOutMovesItAlongTheNormalAndDropsOnlyTheSpeedGoingIn() {
        val p = particles.add(0f, 0f)
        // arriving at (50, 82) from (45, 70): 5 across and 12 down, into a floor whose normal points up
        particles.set(p.index, 50f, 82f, previousX = 45f, previousY = 70f)
        particles.pushOut(p.index, nx = 0f, ny = -1f, depth = 2f)
        assertEquals(50f, particles.xOf(p), 0.001f)
        assertEquals(80f, particles.yOf(p), 0.001f)
        assertEquals(5f, particles.movedXOf(p), 0.001f, "the sideways speed is kept")
        assertEquals(0f, particles.movedYOf(p), 0.001f, "the speed into the floor is gone")
    }

    @Test
    fun pushingOutAParticleThatIsAlreadyLeavingKeepsItsSpeed() {
        val p = particles.add(0f, 0f)
        particles.set(p.index, 50f, 82f, previousX = 50f, previousY = 90f)
        particles.pushOut(p.index, nx = 0f, ny = -1f, depth = 2f)
        assertEquals(-8f, particles.movedYOf(p), 0.001f)
    }
}
