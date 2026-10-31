package klyph.app

/**
 * Holds the systems of each [Phase] in registration order and runs them.
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

    /** Runs every system of [phase] against [app] in the order they were added. */
    fun run(phase: Phase, app: App) {
        forEach(phase) { it.run(app) }
    }

    /**
     * Calls [action] on every system of [phase] in the order they were added, with [phase] marked
     * as running so [add] rejects it. [run] is built on it; tests use it to run without an [App].
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
