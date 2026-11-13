package klyph.scene

import klyph.app.App
import klyph.app.Phase
import klyph.app.System
import klyph.app.Time
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Represents one screen or mode of a game, such as a title screen, a level or a pause menu: the
 * systems that run while it is on top of [App.scenes], an action that runs when it enters the
 * stack and one that runs when it leaves, and a clock, [elapsed], that counts the time it spends on
 * top.
 *
 * Build one with [buildScene]. Its systems and actions never change after that, and it can enter
 * the stack again after it left, for example a title screen shown after every game over; each time
 * it enters, its `onEnter` action runs again and [elapsed] starts over. Two scenes are equal only
 * if they are the same object.
 *
 * Entities spawned by the scene's `onEnter` action or by its systems, directly or through
 * [App.commands], belong to it and are destroyed when it leaves the stack. See [SceneStack].
 *
 * [toString] gives the name and the number of systems, and adds [elapsed] in seconds once it is not
 * zero, for example `Scene(name=play, systems=4)` or `Scene(name=play, systems=4, elapsed=12.5s)`.
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
    /**
     * Returns how long this scene has been the top scene of [App.scenes] since it last entered the
     * stack, including the current frame as [Time.elapsed] does.
     *
     * The clock belongs to the scene, so it counts only the time the scene is on top:
     * - It stands still while another scene is pushed above this one, and goes on from the same
     *   value when that scene is popped.
     * - It starts at zero each time the scene enters the stack, by `push` or by `replace`, also when
     *   the scene has been on the stack before.
     * - A scene that has left the stack keeps its last value until it enters again.
     *
     * The engine adds the duration of the frame, at most 250 milliseconds as for [Time.elapsed],
     * once per frame before [Phase.INPUT], so every phase of a frame, [Phase.FIXED_UPDATE] included,
     * reads the same value. Use it for what should pause with the scene, such as a round timer, a
     * blink or a spin; [Time.elapsed] runs whatever scene is on top.
     *
     * @see elapsedSeconds
     *
     * @sample samples.scene.Scenes.elapsed
     */
    public var elapsed: Duration = Duration.ZERO
        internal set

    /** Returns the systems of [phase] in the order they were added. */
    internal fun systemsOf(phase: Phase): List<System> = systems[phase.ordinal]

    override fun toString(): String {
        val clock = if (elapsed.isPositive()) ", elapsed=${elapsed.toString(DurationUnit.SECONDS, 1)}" else ""
        return "Scene(name=$name, systems=${systems.sumOf { it.size }}$clock)"
    }
}

/**
 * Returns [Scene.elapsed] in seconds, the value to feed to formulas that take seconds, such as a
 * spin or a bob. Like [Scene.elapsed], it stands still while another scene is on top of this one
 * and starts over at zero when the scene enters the stack.
 *
 * @sample samples.scene.Scenes.elapsed
 */
public val Scene.elapsedSeconds: Float
    get() = elapsed.toDouble(DurationUnit.SECONDS).toFloat()

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
