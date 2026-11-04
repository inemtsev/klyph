package klyph.scene

import klyph.app.Phase
import klyph.entity.Health
import klyph.entity.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SceneStackTest {
    @Test
    fun replaceDestroysOwnedEntitiesAndKeepsUnowned() {
        val h = SceneHarness()
        val before = h.world.create()
        var fromEnterCommands = NO_ENTITY
        var fromEnterWorld = NO_ENTITY
        var fromSceneSystem = NO_ENTITY
        var fromAppSystem = NO_ENTITY
        val play = h.scene("play", enter = {
            fromEnterCommands = h.commands.spawn { add(Health, Health(1)) }
            fromEnterWorld = h.world.create()
        }) {
            addSystem(Phase.UPDATE, h.system { if (!h.world.isAlive(fromSceneSystem)) fromSceneSystem = h.commands.spawn() })
        }
        h.addSystem(Phase.UPDATE) { if (!h.world.isAlive(fromAppSystem)) fromAppSystem = h.commands.spawn() }
        val title = h.scene("title")

        h.stack.replace(play)
        h.applyTransitions()
        h.frame()
        for (e in listOf(fromEnterCommands, fromEnterWorld, fromSceneSystem, fromAppSystem)) assertTrue(h.world.isAlive(e))

        h.stack.replace(title)
        h.frame()
        assertFalse(h.world.isAlive(fromEnterCommands))
        assertFalse(h.world.isAlive(fromEnterWorld))
        assertFalse(h.world.isAlive(fromSceneSystem))
        assertTrue(h.world.isAlive(before))
        assertTrue(h.world.isAlive(fromAppSystem))
        assertSame(title, h.stack.currentOrNull)
        assertEquals(1, h.stack.size)
    }

    @Test
    fun transitionsApplyOnlyAtTheEndOfTheFrame() {
        val h = SceneHarness()
        val play = h.scene("play")
        val seen = mutableListOf<Scene?>()
        h.addSystem(Phase.UPDATE) {
            seen += h.stack.currentOrNull
            if (h.stack.isEmpty()) h.stack.push(play)
        }
        h.addSystem(Phase.RENDER) { seen += h.stack.currentOrNull }
        h.frame()
        assertEquals(listOf<Scene?>(null, null), seen)
        assertEquals(listOf("enter play"), h.log)
        assertSame(play, h.stack.currentOrNull)
        assertTrue(play in h.stack)
    }

    @Test
    fun pushFreezesTheSceneBelowAndKeepsItsEntities() {
        val h = SceneHarness()
        var playRuns = 0
        var pauseRuns = 0
        var owned = NO_ENTITY
        val play = h.scene("play", enter = { owned = h.world.create() }) {
            addSystem(Phase.UPDATE, h.system { playRuns++ })
        }
        val pause = h.scene("pause") {
            addSystem(Phase.UPDATE, h.system { pauseRuns++ })
        }
        h.stack.push(play)
        h.applyTransitions()
        h.frame()
        assertEquals(1, playRuns)

        h.stack.push(pause)
        h.frame()
        assertEquals(2, playRuns)
        assertEquals(0, pauseRuns)
        h.frame()
        h.frame()
        assertEquals(2, playRuns)
        assertEquals(2, pauseRuns)
        assertTrue(h.world.isAlive(owned))
        assertTrue(play in h.stack)
        assertSame(pause, h.stack.currentOrNull)
        assertEquals(2, h.stack.size)
    }

    @Test
    fun popRestoresTheSceneBelowWithoutEnteringItAgain() {
        val h = SceneHarness()
        var playRuns = 0
        var pauseEntity = NO_ENTITY
        var playEntity = NO_ENTITY
        val play = h.scene("play", enter = { playEntity = h.world.create() }) {
            addSystem(Phase.UPDATE, h.system { playRuns++ })
        }
        val pause = h.scene("pause", enter = { pauseEntity = h.world.create() })
        h.stack.push(play)
        h.stack.push(pause)
        h.applyTransitions()
        h.frame()
        assertEquals(0, playRuns)

        h.stack.pop()
        h.frame()
        assertEquals(listOf("enter play", "enter pause", "exit pause"), h.log)
        assertFalse(h.world.isAlive(pauseEntity))
        assertTrue(h.world.isAlive(playEntity))
        assertSame(play, h.stack.currentOrNull)
        h.frame()
        assertEquals(1, playRuns)
    }

    @Test
    fun severalTransitionsInOneFrameApplyInOrder() {
        val h = SceneHarness()
        val a = h.scene("a")
        val b = h.scene("b")
        val c = h.scene("c")
        h.stack.replace(a)
        h.stack.push(b)
        h.stack.pop()
        h.stack.push(c)
        assertEquals("SceneStack(scenes=[], queued=4)", h.stack.toString())
        h.frame()
        assertEquals(listOf("enter a", "enter b", "exit b", "enter c"), h.log)
        assertEquals("SceneStack(scenes=[a, c], queued=0)", h.stack.toString())
    }

    @Test
    fun replaceExitsTopFirstAndDestroysAfterTheLastExit() {
        val h = SceneHarness()
        var aEntity = NO_ENTITY
        var cEntity = NO_ENTITY
        val aliveDuringExits = mutableListOf<Boolean>()
        val a = h.scene("a", enter = { aEntity = h.world.create() }, exit = { aliveDuringExits += h.world.isAlive(cEntity) })
        val b = h.scene("b")
        val c = h.scene("c", enter = { cEntity = h.world.create() }, exit = { aliveDuringExits += h.world.isAlive(aEntity) })
        val d = h.scene("d")
        h.stack.push(a)
        h.stack.push(b)
        h.stack.push(c)
        h.applyTransitions()
        h.log.clear()

        h.stack.replace(d)
        h.applyTransitions()
        assertEquals(listOf("exit c", "exit b", "exit a", "enter d"), h.log)
        assertEquals(listOf(true, true), aliveDuringExits)
        assertFalse(h.world.isAlive(aEntity))
        assertFalse(h.world.isAlive(cEntity))
        assertEquals(1, h.stack.size)
    }

    @Test
    fun leavingSceneIsCurrentDuringItsExitAndEnteringSceneDuringItsEnter() {
        val h = SceneHarness()
        val current = mutableListOf<String?>()
        lateinit var a: Scene
        lateinit var b: Scene
        a = h.scene("a", enter = { current += h.stack.currentOrNull?.name }, exit = { current += h.stack.currentOrNull?.name })
        b = h.scene("b", enter = { current += h.stack.currentOrNull?.name }, exit = { current += h.stack.currentOrNull?.name })
        h.stack.push(a)
        h.stack.push(b)
        h.stack.replace(a)
        h.applyTransitions()
        assertEquals(listOf<String?>("a", "b", "b", "a", "a"), current)
    }

    @Test
    fun replaceOfASceneOnTheStackEntersItAgain() {
        val h = SceneHarness()
        val a = h.scene("a")
        h.stack.push(a)
        h.applyTransitions()
        h.stack.replace(a)
        h.applyTransitions()
        assertEquals(listOf("enter a", "exit a", "enter a"), h.log)
        assertSame(a, h.stack.currentOrNull)
    }

    @Test
    fun hooksSeeTheirCommandsApplied() {
        val h = SceneHarness()
        var spawned = NO_ENTITY
        var exitSpawned = NO_ENTITY
        var firstSystemSaw: Int? = null
        val play = h.scene(
            "play",
            enter = { spawned = h.commands.spawn { add(Health, Health(7)) } },
            exit = { exitSpawned = h.commands.spawn { add(Health, Health(3)) } },
        ) {
            addSystem(Phase.INPUT, h.system { if (firstSystemSaw == null) firstSystemSaw = h.world.getOrNull(spawned, Health)?.points })
        }
        h.stack.push(play)
        h.applyTransitions()
        assertTrue(h.commands.isEmpty())
        assertEquals(7, h.world.get(spawned, Health).points)
        h.frame()
        assertEquals(7, firstSystemSaw)

        h.stack.pop()
        h.applyTransitions()
        assertTrue(h.commands.isEmpty())
        assertEquals(3, h.world.get(exitSpawned, Health).points)
        assertEquals(World.NO_OWNER, h.world.ownerOf(exitSpawned))
    }

    @Test
    fun exitCommandsApplyBeforeOwnedEntitiesAreDestroyed() {
        val h = SceneHarness()
        var owned = NO_ENTITY
        var keeper = NO_ENTITY
        val play = h.scene("play", enter = { owned = h.world.create() }, exit = {
            keeper = h.commands.spawn()
            h.commands.setParent(owned, keeper)
        })
        h.stack.push(play)
        h.applyTransitions()
        h.stack.pop()
        h.applyTransitions()
        assertFalse(h.world.isAlive(owned))
        assertTrue(h.world.isAlive(keeper))
        assertEquals(emptyList(), h.world.childrenOf(keeper))
    }

    @Test
    fun transitionQueuedByAHookAppliesInTheSamePass() {
        val h = SceneHarness()
        lateinit var title: Scene
        val boot = h.scene("boot", enter = { h.stack.replace(title) })
        title = h.scene("title")
        h.stack.push(boot)
        h.applyTransitions()
        assertEquals(listOf("enter boot", "exit boot", "enter title"), h.log)
        assertEquals("SceneStack(scenes=[title], queued=0)", h.stack.toString())
    }

    @Test
    fun popOnAnEmptyStackThrows() {
        val h = SceneHarness()
        assertFailsWith<IllegalStateException> { h.stack.pop() }
        h.stack.push(h.scene("a"))
        h.stack.pop()
        assertFailsWith<IllegalStateException> { h.stack.pop() }
        h.stack.replace(h.scene("b"))
        h.stack.pop()
        assertFailsWith<IllegalStateException> { h.stack.pop() }
    }

    @Test
    fun pushingASceneTwiceThrows() {
        val h = SceneHarness()
        val a = h.scene("a")
        h.stack.push(a)
        assertFailsWith<IllegalArgumentException> { h.stack.push(a) }
        h.applyTransitions()
        assertFailsWith<IllegalArgumentException> { h.stack.push(a) }
        h.stack.push(h.scene("b"))
        assertFailsWith<IllegalArgumentException> { h.stack.push(a) }
        h.stack.pop()
        h.stack.pop()
        h.stack.push(a)
        h.applyTransitions()
        assertEquals(listOf("enter a", "enter b", "exit b", "exit a", "enter a"), h.log)
    }

    @Test
    fun sceneSystemsRunAfterAppSystemsOfTheSamePhase() {
        val h = SceneHarness()
        val order = mutableListOf<String>()
        h.addSystem(Phase.UPDATE) { order += "app 1" }
        val play = h.scene("play") {
            addSystem(Phase.UPDATE, h.system { order += "scene 1" })
            addSystem(Phase.LATE_UPDATE, h.system { order += "scene late" })
            addSystem(Phase.UPDATE, h.system { order += "scene 2" })
        }
        h.addSystem(Phase.UPDATE) { order += "app 2" }
        h.addSystem(Phase.LATE_UPDATE) { order += "app late" }
        h.stack.push(play)
        h.applyTransitions()
        h.frame()
        assertEquals(listOf("app 1", "app 2", "scene 1", "scene 2", "app late", "scene late"), order)
    }

    @Test
    fun sceneSystemCannotAddAnAppSystemToTheRunningPhase() {
        val h = SceneHarness()
        val play = h.scene("play") {
            addSystem(Phase.UPDATE, h.system { h.addSystem(Phase.UPDATE) {} })
        }
        h.stack.push(play)
        h.applyTransitions()
        assertFailsWith<IllegalStateException> { h.run(Phase.UPDATE) }
        h.addSystem(Phase.UPDATE) {}
        assertEquals(1, h.schedule.count(Phase.UPDATE))
    }

    @Test
    fun shutdownExitsEverySceneBeforeShutdownSystems() {
        val h = SceneHarness()
        var owned = NO_ENTITY
        val a = h.scene("a", enter = { owned = h.world.create() })
        val b = h.scene("b")
        h.addSystem(Phase.SHUTDOWN) { h.log += "shutdown with ${h.stack.size} scenes" }
        h.stack.push(a)
        h.stack.push(b)
        h.applyTransitions()
        h.shutdown()
        assertEquals(listOf("enter a", "enter b", "exit b", "exit a", "shutdown with 0 scenes"), h.log)
        assertFalse(h.world.isAlive(owned))
        assertTrue(h.stack.isEmpty())
    }

    @Test
    fun shutdownAppliesQueuedTransitionsAndDropsThoseQueuedByExits() {
        val h = SceneHarness()
        val other = h.scene("other")
        val a = h.scene("a", exit = { h.stack.push(other) })
        h.stack.push(a)
        h.shutdown()
        assertEquals(listOf("enter a", "exit a"), h.log)
        assertEquals("SceneStack(scenes=[], queued=0)", h.stack.toString())
    }

    @Test
    fun ownershipFollowsTheRunningSceneNotTheStackTop() {
        val h = SceneHarness()
        var fromApp = NO_ENTITY
        var fromScene = NO_ENTITY
        h.addSystem(Phase.UPDATE) { fromApp = h.world.create() }
        val play = h.scene("play") {
            addSystem(Phase.UPDATE, h.system { fromScene = h.commands.spawn() })
        }
        h.stack.push(play)
        h.applyTransitions()
        h.run(Phase.UPDATE)
        assertEquals(World.NO_OWNER, h.world.ownerOf(fromApp))
        assertEquals(h.stack.currentOwner, h.world.ownerOf(fromScene))
        assertNotEquals(World.NO_OWNER, h.stack.currentOwner)
        assertEquals(World.NO_OWNER, h.world.currentOwner)
    }

    @Test
    fun eachStayOnTheStackGetsANewOwner() {
        val h = SceneHarness()
        val owners = mutableListOf<Int>()
        val a = h.scene("a", enter = { owners += h.world.currentOwner })
        val b = h.scene("b", enter = { owners += h.world.currentOwner })
        h.stack.push(a)
        h.stack.push(b)
        h.stack.replace(a)
        h.applyTransitions()
        assertEquals(3, owners.toSet().size)
        assertFalse(World.NO_OWNER in owners)
    }

    @Test
    fun ownedEntityReparentedToAnUnownedParentIsStillDestroyed() {
        val h = SceneHarness()
        val parent = h.world.create()
        var owned = NO_ENTITY
        val play = h.scene("play", enter = { owned = h.world.create() })
        h.stack.push(play)
        h.applyTransitions()
        h.world.setParent(owned, parent)
        assertNotEquals(World.NO_OWNER, h.world.ownerOf(owned))

        h.stack.pop()
        h.applyTransitions()
        assertFalse(h.world.isAlive(owned))
        assertTrue(h.world.isAlive(parent))
        assertEquals(emptyList(), h.world.childrenOf(parent))
    }

    @Test
    fun unownedChildOfAnOwnedParentIsDestroyedThroughTheCascade() {
        val h = SceneHarness()
        var owned = NO_ENTITY
        var child = NO_ENTITY
        var grandchild = NO_ENTITY
        val play = h.scene("play", enter = { owned = h.world.create() })
        h.addSystem(Phase.UPDATE) {
            if (!h.world.isAlive(child)) {
                child = h.commands.spawn { setParent(owned) }
                grandchild = h.commands.spawn { setParent(child) }
            }
        }
        h.stack.push(play)
        h.applyTransitions()
        h.frame()
        assertEquals(World.NO_OWNER, h.world.ownerOf(child))
        assertEquals(owned, h.world.parentOrNull(child))

        h.stack.pop()
        h.applyTransitions()
        assertFalse(h.world.isAlive(owned))
        assertFalse(h.world.isAlive(child))
        assertFalse(h.world.isAlive(grandchild))
        assertEquals(0, h.world.entityCount)
    }

    @Test
    fun throwingHookDropsTheRestOfTheQueueAndResetsTheOwner() {
        val h = SceneHarness()
        val a = h.scene("a", enter = { error("boom") })
        val b = h.scene("b")
        h.stack.push(a)
        h.stack.push(b)
        assertFailsWith<IllegalStateException> { h.applyTransitions() }
        assertEquals(World.NO_OWNER, h.world.currentOwner)
        assertEquals("SceneStack(scenes=[], queued=0)", h.stack.toString())
        h.stack.push(b)
        h.applyTransitions()
        assertSame(b, h.stack.currentOrNull)
    }

    @Test
    fun throwingEnterRemovesItsSceneAndDestroysWhatItCreated() {
        val h = SceneHarness()
        val unowned = h.world.create()
        var kept = NO_ENTITY
        var created = NO_ENTITY
        var spawned = NO_ENTITY
        val play = h.scene("play", enter = { kept = h.world.create() })
        val broken = h.scene("broken", enter = {
            created = h.world.create()
            spawned = h.commands.spawn { add(Health, Health(1)) }
            h.commands.destroy(unowned)
            error("boom")
        })
        h.stack.push(play)
        h.applyTransitions()

        h.stack.push(broken)
        h.stack.pop()
        assertFailsWith<IllegalStateException> { h.applyTransitions() }
        assertEquals("SceneStack(scenes=[play], queued=0)", h.stack.toString())
        assertFalse(broken in h.stack)
        assertFalse(h.world.isAlive(created))
        assertFalse(h.world.isAlive(spawned))
        assertTrue(h.world.isAlive(kept))
        assertTrue(h.world.isAlive(unowned), "The commands the failing action queued are dropped.")
        assertTrue(h.commands.isEmpty())
        assertEquals(2, h.world.entityCount)
        assertEquals(listOf("enter play", "enter broken"), h.log)

        // The stack keeps working after the failure.
        h.stack.push(h.scene("next"))
        h.applyTransitions()
        assertEquals(2, h.stack.size)
    }

    @Test
    fun throwingExitStillRemovesEveryLeavingSceneAndItsEntities() {
        val h = SceneHarness()
        val unowned = h.world.create()
        var bottomOwned = NO_ENTITY
        var middleOwned = NO_ENTITY
        var topOwned = NO_ENTITY
        val bottom = h.scene("bottom", enter = { bottomOwned = h.world.create() })
        val middle = h.scene("middle", enter = { middleOwned = h.world.create() }, exit = {
            h.commands.destroy(unowned)
            error("boom")
        })
        val top = h.scene("top", enter = { topOwned = h.world.create() })
        val title = h.scene("title")
        h.stack.push(bottom)
        h.stack.push(middle)
        h.stack.push(top)
        h.applyTransitions()

        h.stack.replace(title)
        assertFailsWith<IllegalStateException> { h.applyTransitions() }
        assertTrue(h.stack.isEmpty())
        assertEquals("SceneStack(scenes=[], queued=0)", h.stack.toString())
        assertFalse(h.world.isAlive(bottomOwned))
        assertFalse(h.world.isAlive(middleOwned))
        assertFalse(h.world.isAlive(topOwned))
        assertTrue(h.world.isAlive(unowned))
        assertEquals(1, h.world.entityCount)
        assertEquals(World.NO_OWNER, h.world.currentOwner)
        // bottom leaves without its onExit, and the replace's push of title is dropped.
        assertEquals(listOf("enter bottom", "enter middle", "enter top", "exit top", "exit middle"), h.log)
    }

    @Test
    fun throwingExitOnPopStillRemovesTheScene() {
        val h = SceneHarness()
        var owned = NO_ENTITY
        val play = h.scene("play")
        val pause = h.scene("pause", enter = { owned = h.world.create() }, exit = { error("boom") })
        h.stack.push(play)
        h.stack.push(pause)
        h.applyTransitions()

        h.stack.pop()
        assertFailsWith<IllegalStateException> { h.applyTransitions() }
        assertSame(play, h.stack.currentOrNull)
        assertFalse(h.world.isAlive(owned))
    }

    @Test
    fun throwingSceneSystemResetsTheOwner() {
        val h = SceneHarness()
        val play = h.scene("play") {
            addSystem(Phase.UPDATE, h.system { error("boom") })
        }
        h.stack.push(play)
        h.applyTransitions()
        assertFailsWith<IllegalStateException> { h.run(Phase.UPDATE) }
        assertEquals(World.NO_OWNER, h.world.currentOwner)
        h.addSystem(Phase.UPDATE) {}
    }

    @Test
    fun emptyStackRunsOnlyAppSystems() {
        val h = SceneHarness()
        var runs = 0
        h.addSystem(Phase.UPDATE) { runs++ }
        h.frame()
        assertEquals(1, runs)
        assertNull(h.stack.currentOrNull)
        assertTrue(h.stack.isEmpty())
        assertEquals(0, h.stack.size)
        assertEquals(World.NO_OWNER, h.stack.currentOwner)
    }
}
