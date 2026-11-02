package klyph.scene

import klyph.app.App
import klyph.app.Phase
import klyph.app.System

/**
 * Receiver of the block passed to [buildScene]. Sets the actions a scene runs when it enters and
 * leaves the stack, and adds the systems it runs while it is on top.
 *
 * Use it only inside that block: what it receives afterwards does not change the scene already
 * built. [toString] gives the scene's name and the number of systems added so far, for example
 * `SceneBuilder(name=play, systems=2)`.
 */
public class SceneBuilder internal constructor(private val name: String) {
    private val systems = Array(Phase.entries.size) { ArrayList<System>() }
    private var enter: ((App) -> Unit)? = null
    private var exit: ((App) -> Unit)? = null

    /**
     * Sets the action that runs each time the scene enters the stack, at the end of the frame in
     * which `push` or `replace` was called and before any of its systems runs. Entities it spawns,
     * directly or through [App.commands], belong to the scene. The engine applies [App.commands]
     * right after it, so the scene's first systems see those entities with their components.
     *
     * @throws IllegalStateException if the action was already set.
     *
     * @sample samples.scene.Scenes.buildScene
     */
    public fun onEnter(action: (App) -> Unit) {
        check(enter == null) { "Scene $name already has an onEnter action." }
        enter = action
    }

    /**
     * Sets the action that runs each time the scene leaves the stack, before the entities it owns
     * are destroyed, so it can still read them. Entities it spawns belong to no scene. The engine
     * applies [App.commands] right after it.
     *
     * @throws IllegalStateException if the action was already set.
     *
     * @sample samples.scene.Scenes.buildScene
     */
    public fun onExit(action: (App) -> Unit) {
        check(exit == null) { "Scene $name already has an onExit action." }
        exit = action
    }

    /**
     * Adds [system] to [phase] of the scene. While the scene is on top of the stack, [system] runs
     * each time [phase] runs, after every App-level system of [phase] and after the scene's own
     * systems added to [phase] before it.
     *
     * [Phase.STARTUP] and [Phase.SHUTDOWN] run once for the whole app, not per scene, so they are
     * rejected; use [onEnter] and [onExit] instead.
     *
     * @throws IllegalArgumentException if [phase] is [Phase.STARTUP] or [Phase.SHUTDOWN].
     *
     * @sample samples.scene.Scenes.buildScene
     */
    public fun addSystem(phase: Phase, system: System) {
        require(phase != Phase.STARTUP && phase != Phase.SHUTDOWN) {
            "Scene $name cannot have a $phase system; use onEnter or onExit instead."
        }
        systems[phase.ordinal].add(system)
    }

    /** Returns a scene with a copy of what this builder holds. */
    internal fun build(): Scene =
        Scene(name, Array(systems.size) { systems[it].toList() }, enter ?: {}, exit ?: {})

    override fun toString(): String = "SceneBuilder(name=$name, systems=${systems.sumOf { it.size }})"
}
