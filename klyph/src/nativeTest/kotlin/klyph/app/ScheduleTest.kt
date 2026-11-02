package klyph.app

import klyph.entity.Commands
import klyph.entity.ComponentType
import klyph.entity.Entity
import klyph.entity.World
import klyph.scene.buildScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// App needs a window, so these tests drive the schedule through forEach, which run is built on.
class ScheduleTest {
    @Test
    fun visitsSystemsOfAPhaseInRegistrationOrder() {
        val schedule = Schedule()
        val a = System {}
        val b = System {}
        val c = System {}
        schedule.add(Phase.UPDATE, a)
        schedule.add(Phase.UPDATE, b)
        schedule.add(Phase.UPDATE, c)
        val seen = mutableListOf<System>()
        schedule.forEach(Phase.UPDATE) { seen += it }
        assertEquals(listOf(a, b, c), seen)
    }

    @Test
    fun phasesAreIndependent() {
        val schedule = Schedule()
        val update = System {}
        val render = System {}
        schedule.add(Phase.UPDATE, update)
        schedule.add(Phase.RENDER, render)
        val seen = mutableListOf<System>()
        schedule.forEach(Phase.RENDER) { seen += it }
        assertEquals(listOf(render), seen)
        schedule.forEach(Phase.INPUT) { seen += it }
        assertEquals(listOf(render), seen)
        schedule.forEach(Phase.UPDATE) { seen += it }
        assertEquals(listOf(render, update), seen)
    }

    @Test
    fun addingToTheRunningPhaseThrows() {
        val schedule = Schedule()
        schedule.add(Phase.UPDATE) {}
        assertFailsWith<IllegalStateException> { schedule.forEach(Phase.UPDATE) { schedule.add(Phase.UPDATE) {} } }
        assertEquals(1, schedule.count(Phase.UPDATE))
    }

    @Test
    fun addingToAnotherPhaseWhileRunningIsAllowed() {
        val schedule = Schedule()
        schedule.add(Phase.STARTUP) {}
        schedule.forEach(Phase.STARTUP) { schedule.add(Phase.UPDATE) {} }
        assertEquals(1, schedule.count(Phase.UPDATE))
    }

    @Test
    fun phaseIsAddableAgainAfterASystemThrows() {
        val schedule = Schedule()
        schedule.add(Phase.UPDATE) {}
        assertFailsWith<IllegalStateException> { schedule.forEach(Phase.UPDATE) { error("boom") } }
        schedule.add(Phase.UPDATE) {}
        assertEquals(2, schedule.count(Phase.UPDATE))
    }

    @Test
    fun exceptionsPropagateAndStopThePhase() {
        val schedule = Schedule()
        schedule.add(Phase.UPDATE) {}
        schedule.add(Phase.UPDATE) {}
        var visited = 0
        assertFailsWith<UnsupportedOperationException> {
            schedule.forEach(Phase.UPDATE) {
                visited++
                throw UnsupportedOperationException("boom")
            }
        }
        assertEquals(1, visited)
    }

    @Test
    fun countReportsSystemsPerPhase() {
        val schedule = Schedule()
        for (phase in Phase.entries) assertEquals(0, schedule.count(phase))
        schedule.add(Phase.FIXED_UPDATE) {}
        schedule.add(Phase.FIXED_UPDATE) {}
        schedule.add(Phase.LATE_UPDATE) {}
        assertEquals(2, schedule.count(Phase.FIXED_UPDATE))
        assertEquals(1, schedule.count(Phase.LATE_UPDATE))
        assertEquals(0, schedule.count(Phase.UPDATE))
    }

    @Test
    fun runRunsTheSceneSystemsAfterTheAppSystemsWithTheSceneAsOwner() {
        val schedule = Schedule()
        val world = World()
        val commands = Commands(world)
        val owners = mutableListOf<String>()
        val app = System {}
        val sceneSystem = System {}
        schedule.add(Phase.UPDATE, app)
        val scene = buildScene("play") { addSystem(Phase.UPDATE, sceneSystem) }
        schedule.run(Phase.UPDATE, world, commands, scene, owner = 5) { system ->
            val name = if (system === app) "app" else "scene"
            owners += "$name ${world.create().let(world::ownerOf)}"
        }
        assertEquals(listOf("app 0", "scene 5"), owners)
        assertEquals(World.NO_OWNER, world.currentOwner)
    }

    @Test
    fun runAppliesCommandsAfterEverySystemAndKeepsTheOwnerForTheApplication() {
        val schedule = Schedule()
        val world = World()
        val commands = Commands(world)
        val seen = mutableListOf<Boolean>()
        val spawn = System {}
        val look = System {}
        val scene = buildScene("play") {
            addSystem(Phase.UPDATE, spawn)
            addSystem(Phase.UPDATE, look)
        }
        var spawned = Entity.of(1_000_000, 0)
        schedule.run(Phase.UPDATE, world, commands, scene, owner = 2) { system ->
            if (system === spawn) spawned = commands.spawn { add(Marker, Marker()) } else seen += world.has(spawned, Marker)
        }
        assertEquals(listOf(true), seen)
        assertEquals(2, world.ownerOf(spawned))
    }

    @Test
    fun runWithoutASceneRunsOnlyTheAppSystems() {
        val schedule = Schedule()
        val world = World()
        var runs = 0
        schedule.add(Phase.UPDATE) {}
        schedule.run(Phase.UPDATE, world, Commands(world), scene = null, owner = World.NO_OWNER) { runs++ }
        assertEquals(1, runs)
    }

    private class Marker {
        override fun toString(): String = "Marker"

        companion object : ComponentType<Marker>("Marker")
    }
}
