package klyph.app

import klyph.entity.Commands
import klyph.entity.World
import klyph.scene.Scene

/**
 * Holds the App-level systems of each [Phase] in registration order and runs them, followed by
 * the systems of the scene on top of the stack.
 *
 * Not thread-safe. Exceptions thrown by a system propagate to the caller of [run].
 */
internal class Schedule {
    // Internal rather than private so the inline forEach can read them.
    internal val systems = Array(Phase.entries.size) { ArrayList<System>() }
    internal val runningDepth = IntArray(Phase.entries.size)

    /**
     * Appends [system] to [phase], after every system already added to it.
     *
     * @throws IllegalStateException if [phase] is running.
     */
    fun add(phase: Phase, system: System) {
        check(runningDepth[phase.ordinal] == 0) { "Cannot add a system to phase $phase while it is running." }
        systems[phase.ordinal].add(system)
    }

    /**
     * Runs every system of [phase] against [app] in the order they were added, then the systems of
     * [phase] of the scene on top of [App.scenes], applying [App.commands] after each one so the next
     * system sees its changes.
     */
    fun run(phase: Phase, app: App) {
        val scenes = app.scenes
        run(phase, app.world, app.commands, scenes.currentOrNull, scenes.currentOwner) { it.run(app) }
    }

    /**
     * Calls [invoke] on every system of [phase] in the order they were added, then on every system of
     * [phase] of [scene], and applies [commands] after each one. While a system of [scene] runs, and
     * while its commands apply, [World.currentOwner] of [world] is [owner], so the entities it
     * creates belong to [scene]; for every other system it is [World.NO_OWNER]. [phase] is marked as
     * running for the whole call, so [add] rejects it.
     *
     * The other [run] is built on it; tests call it to run without an [App].
     */
    inline fun run(phase: Phase, world: World, commands: Commands, scene: Scene?, owner: Int, invoke: (System) -> Unit) {
        forEach(phase) { system -> runOne(system, world, commands, World.NO_OWNER, invoke) }
        if (scene == null) return
        val sceneSystems = scene.systemsOf(phase)
        if (sceneSystems.isEmpty()) return
        val ordinal = phase.ordinal
        runningDepth[ordinal]++
        try {
            for (i in sceneSystems.indices) runOne(sceneSystems[i], world, commands, owner, invoke)
        } finally {
            runningDepth[ordinal]--
        }
    }

    /** Calls [invoke] on [system] and applies [commands] with [owner] as the world's current owner. */
    inline fun runOne(system: System, world: World, commands: Commands, owner: Int, invoke: (System) -> Unit) {
        world.currentOwner = owner
        try {
            invoke(system)
            if (!commands.isEmpty()) commands.apply()
        } finally {
            world.currentOwner = World.NO_OWNER
        }
    }

    /**
     * Calls [action] on every system of [phase] in the order they were added, with [phase] marked
     * as running so [add] rejects it.
     */
    inline fun forEach(phase: Phase, action: (System) -> Unit) {
        val list = systems[phase.ordinal]
        runningDepth[phase.ordinal]++
        try {
            for (i in list.indices) action(list[i])
        } finally {
            runningDepth[phase.ordinal]--
        }
    }

    /** Returns the number of systems added to [phase]. */
    fun count(phase: Phase): Int = systems[phase.ordinal].size

    override fun toString(): String =
        Phase.entries.joinToString(prefix = "Schedule(", postfix = ")") { "$it=${count(it)}" }
}
