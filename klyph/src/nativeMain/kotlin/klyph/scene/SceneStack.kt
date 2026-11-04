package klyph.scene

import klyph.app.App
import klyph.app.Phase
import klyph.entity.Commands
import klyph.entity.World

/**
 * Holds the scenes of an app as a stack. Only the scene on top, [currentOrNull], runs its systems.
 * A pushed scene freezes the one below: its systems do not run, its particle emitters, sprite
 * animations and colliders stand still, its entities keep rendering. Frozen emitters neither
 * advance nor emit, so their particles stay where they are; frozen animations keep their current
 * frame; frozen colliders are left out of collision detection, so they are in no `Overlap` event
 * and no collision query finds them. An entity a scene owns is frozen whatever its parent is, and
 * an entity of no scene is never frozen. App-level systems added with [App.addSystem] run whatever
 * the stack holds, before the top scene's systems of the same phase.
 *
 * [replace], [push] and [pop] do not change the stack right away. They queue a transition, and the
 * engine applies the queued transitions in the order they were queued at the end of the frame,
 * after the sprite pass, so every system of a frame sees the same scene. Transitions queued in the
 * `setup` block of `runApp` or in [Phase.STARTUP] apply after STARTUP, before the first frame.
 * [currentOrNull], [size], [isEmpty] and [contains] describe the stack as it stands, without the
 * queued transitions.
 *
 * Applying a transition:
 * - A scene leaves the stack by running its `onExit` action while it is still [currentOrNull],
 *   then [App.commands] is applied, then it is removed. When several scenes leave at once, as on
 *   [replace], they do so top first, and the entities of all of them are destroyed after the last
 *   one has left.
 * - The entities a leaving scene owns are destroyed with their descendants, as `World.destroy`
 *   does. A scene owns the entities created while its `onEnter` action or one of its systems ran,
 *   directly with `World.create` or through [App.commands]. Entities created by App-level systems,
 *   by an `onExit` action, by the `setup` block or by the engine belong to no scene and stay.
 *   Moving an entity to another parent does not change its owner, but an entity of no scene that
 *   is a child of an owned entity is destroyed with its parent.
 * - A scene enters the stack by being pushed, so it is [currentOrNull] when its `onEnter` action
 *   runs, then [App.commands] is applied.
 *
 * A transition queued by an `onEnter` or `onExit` action applies in the same pass, after the
 * transitions already queued.
 *
 * When an action throws, the transitions queued after the one it belongs to are dropped, the
 * commands it queued but did not get applied are dropped, and the exception propagates:
 * - If an `onEnter` action throws, its scene is removed from the stack again and the entities it
 *   created are destroyed, so the stack and the world are as they were just before it entered.
 *   What the action did to anything else, such as resources, audio or entities it changed or
 *   destroyed directly through the world, is not undone.
 * - If an `onExit` action throws, its scene and every other scene that was leaving in the same
 *   transition are still removed from the stack and lose their entities, as if they had left
 *   normally. The scenes still below it that had yet to leave on [replace] or at the end of the app
 *   leave without running their `onExit` actions.
 *
 * When the app ends, after the last frame, every scene leaves the stack top first as on [replace],
 * before the [Phase.SHUTDOWN] systems run, so `onExit` actions still find the app fully working.
 * Transitions queued by those actions are dropped.
 *
 * [toString] gives the scene names bottom to top and the number of queued transitions, for
 * example `SceneStack(scenes=[play, pause], queued=0)`.
 *
 * Not thread-safe. Samples are exempt because the stack only changes inside a running app.
 */
public class SceneStack internal constructor() {
    private val scenes = ArrayList<Scene>()

    /** Owner ids of [scenes], index for index; the first [size] entries are in use. */
    private var owners = IntArray(4)

    /** The stack as it will be once every queued transition has applied. */
    private val planned = ArrayList<Scene>()
    private val transitions = ArrayList<Transition>()
    private var nextOwner = World.NO_OWNER + 1
    private var isApplying = false

    /** The scene on top of the stack, whose systems run, or `null` if the stack is empty. */
    public val currentOrNull: Scene? get() = scenes.lastOrNull()

    /** Number of scenes on the stack. */
    public val size: Int get() = scenes.size

    /** Returns `true` if no scene is on the stack. */
    public fun isEmpty(): Boolean = scenes.isEmpty()

    /** Returns `true` if [scene] is on the stack, on top or below. */
    public operator fun contains(scene: Scene): Boolean = scene in scenes

    /**
     * Queues emptying the stack and then pushing [scene], so [scene] becomes the only scene. Every
     * scene on the stack at that point leaves it, top first, including [scene] itself if it was
     * there, in which case it enters again.
     */
    public fun replace(scene: Scene) {
        planned.clear()
        planned += scene
        transitions += Transition(REPLACE, scene)
    }

    /**
     * Queues pushing [scene] on top of the stack. The scene that was on top stays, frozen, until
     * [scene] is popped.
     *
     * A scene can be on the stack only once, so [scene] must not be on the stack once the
     * transitions queued before this call have applied.
     *
     * @throws IllegalArgumentException if [scene] is on the stack, or will be once the queued
     *   transitions apply.
     */
    public fun push(scene: Scene) {
        require(scene !in planned) { "Scene ${scene.name} is already on the stack." }
        planned += scene
        transitions += Transition(PUSH, scene)
    }

    /**
     * Queues removing the scene on top of the stack. The scene below it, if any, becomes the top
     * again and its systems run from the next frame on; its `onEnter` action does not run again.
     *
     * @throws IllegalStateException if the stack is empty, or will be once the queued transitions
     *   apply.
     */
    public fun pop() {
        check(planned.isNotEmpty()) { "Scene stack has no scene to pop." }
        planned.removeAt(planned.lastIndex)
        transitions += Transition(POP, null)
    }

    /** The owner id of [currentOrNull], or [World.NO_OWNER] if the stack is empty. */
    internal val currentOwner: Int get() = if (scenes.isEmpty()) World.NO_OWNER else owners[scenes.size - 1]

    /**
     * `true` while a scene is frozen below the top, so [isFrozen] can return `true`. The engine
     * steps read it once per frame and skip the per-entity check when it is `false`.
     */
    internal val hasFrozen: Boolean get() = scenes.size > 1

    /**
     * Returns `true` if [owner] is the owner id of a scene below the top, whose entities stand
     * still as the class description says. [World.NO_OWNER] is never frozen. Scans the scenes below
     * the top, which are rarely more than one or two.
     */
    internal fun isFrozen(owner: Int): Boolean {
        if (owner == World.NO_OWNER) return false
        val owners = owners
        for (k in 0 until scenes.size - 1) if (owners[k] == owner) return true
        return false
    }

    /**
     * Applies every queued transition in order through [host], including those queued while they
     * apply, as the class documentation describes. The engine calls it at the end of every frame
     * and once after [Phase.STARTUP].
     *
     * If an action throws, the transitions not yet applied are dropped and the stack and the world
     * are left as the class description says.
     */
    internal fun applyTransitions(host: SceneHost) {
        check(!isApplying) { "Scene transitions are already being applied." }
        if (transitions.isEmpty()) return
        isApplying = true
        try {
            var i = 0
            while (i < transitions.size) {
                val transition = transitions[i++]
                when (transition.kind) {
                    PUSH -> enter(transition.scene!!, host)
                    POP -> exitTop(host)
                    REPLACE -> {
                        exitAll(host)
                        enter(transition.scene!!, host)
                    }
                }
            }
        } finally {
            settle()
        }
    }

    /**
     * Applies the queued transitions, then makes every scene leave the stack top first, as the
     * engine does once after the last frame. Transitions queued by the `onExit` actions are dropped.
     */
    internal fun exitEveryScene(host: SceneHost) {
        applyTransitions(host)
        isApplying = true
        try {
            exitAll(host)
        } finally {
            settle()
        }
    }

    /**
     * Makes every scene leave top first, then destroys the entities they own. If an `onExit` action
     * throws, the scenes still on the stack are removed without running theirs, and the entities of
     * every scene that was on the stack are destroyed before the exception propagates.
     */
    private fun exitAll(host: SceneHost) {
        val count = scenes.size
        if (count == 0) return
        // Top first, the order the scenes leave in.
        val leaving = IntArray(count) { owners[count - 1 - it] }
        try {
            while (scenes.isNotEmpty()) leave(host)
        } finally {
            scenes.clear()
            for (owner in leaving) host.world.destroyOwnedBy(owner)
        }
    }

    /** Makes the top scene leave as [exitAll] does for one scene, then destroys its entities. */
    private fun exitTop(host: SceneHost) {
        val owner = owners[scenes.size - 1]
        try {
            leave(host)
        } finally {
            host.world.destroyOwnedBy(owner)
        }
    }

    /**
     * Runs the top scene's `onExit` action, applies the commands and removes the scene. The scene is
     * removed even if the action or the commands throw, and then the commands still queued are
     * dropped.
     */
    private fun leave(host: SceneHost) {
        val scene = scenes[scenes.lastIndex]
        host.world.currentOwner = World.NO_OWNER
        var left = false
        try {
            host.exit(scene)
            flush(host.commands)
            left = true
        } finally {
            scenes.removeAt(scenes.lastIndex)
            if (!left) host.commands.discard()
        }
    }

    /**
     * Pushes [scene] with a new owner id and runs its `onEnter` action as that owner. If the action
     * or its commands throw, the commands still queued are dropped, the scene is removed and the
     * entities it created are destroyed before the exception propagates.
     */
    private fun enter(scene: Scene, host: SceneHost) {
        val world = host.world
        val owner = nextOwner++
        if (scenes.size == owners.size) owners = owners.copyOf(owners.size * 2)
        owners[scenes.size] = owner
        scenes += scene
        world.currentOwner = owner
        var entered = false
        try {
            host.enter(scene)
            flush(host.commands)
            entered = true
        } finally {
            world.currentOwner = World.NO_OWNER
            if (!entered) {
                host.commands.discard()
                scenes.removeAt(scenes.lastIndex)
                world.destroyOwnedBy(owner)
            }
        }
    }

    /** Drops the queue and makes [planned] match the stack, after a pass or an exception. */
    private fun settle() {
        transitions.clear()
        planned.clear()
        planned.addAll(scenes)
        isApplying = false
    }

    private fun flush(commands: Commands) {
        if (!commands.isEmpty()) commands.apply()
    }

    override fun toString(): String =
        scenes.joinToString(prefix = "SceneStack(scenes=[", postfix = "], queued=${transitions.size})") { it.name }

    private class Transition(val kind: Int, val scene: Scene?) {
        override fun toString(): String = "Transition(kind=$kind, scene=$scene)"
    }

    private companion object {
        const val PUSH = 0
        const val POP = 1
        const val REPLACE = 2
    }
}

/**
 * Runs the actions of the scenes a [SceneStack] applies transitions for, with the world and the
 * command queue they work on. The app runs them with itself as the argument; tests, which have no
 * app, run actions of their own.
 */
internal interface SceneHost {
    /** The world whose entities the scenes own. */
    val world: World

    /** The queue applied after every action. */
    val commands: Commands

    /** Runs the `onEnter` action of [scene]. */
    fun enter(scene: Scene)

    /** Runs the `onExit` action of [scene]. */
    fun exit(scene: Scene)
}
