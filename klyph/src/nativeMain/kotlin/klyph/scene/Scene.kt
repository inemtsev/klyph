package klyph.scene

import klyph.app.App
import klyph.app.Phase
import klyph.app.System

/**
 * Represents one screen or mode of a game, such as a title screen, a level or a pause menu: the
 * systems that run while it is on top of [App.scenes], an action that runs when it enters the
 * stack and one that runs when it leaves.
 *
 * Build one with [buildScene]. A scene is immutable and can enter the stack again after it left,
 * for example a title screen shown after every game over; each time it enters, its `onEnter`
 * action runs again. Two scenes are equal only if they are the same object.
 *
 * Entities spawned by the scene's `onEnter` action or by its systems, directly or through
 * [App.commands], belong to it and are destroyed when it leaves the stack. See [SceneStack].
 *
 * [toString] gives the name and the number of systems, for example `Scene(name=play, systems=4)`.
 *
 * @sample samples.scene.Scenes.buildScene
 */
public class Scene internal constructor(
    /**
     * The name given to [buildScene], used in [toString] and for debugging.
     *
     * @sample samples.scene.Scenes.buildScene
     */
    public val name: String,
    private val systems: Array<List<System>>,
    internal val onEnter: (App) -> Unit,
    internal val onExit: (App) -> Unit,
) {
    /** Returns the systems of [phase] in the order they were added. */
    internal fun systemsOf(phase: Phase): List<System> = systems[phase.ordinal]

    override fun toString(): String = "Scene(name=$name, systems=${systems.sumOf { it.size }})"
}

/**
 * Creates a [Scene] named [name] from the actions and systems that [builder] adds.
 *
 * A scene without an `onEnter` or `onExit` action does nothing when it enters or leaves the stack.
 *
 * @throws IllegalStateException if [builder] sets the `onEnter` or the `onExit` action twice.
 * @throws IllegalArgumentException if [builder] adds a system to [Phase.STARTUP] or
 *   [Phase.SHUTDOWN].
 *
 * @sample samples.scene.Scenes.buildScene
 */
public fun buildScene(name: String, builder: SceneBuilder.() -> Unit): Scene =
    SceneBuilder(name).apply(builder).build()
