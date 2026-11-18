package klyph.physics.demo

import klyph.math.Rect
import klyph.physics.PhysicsWorld
import klyph.physics.DebugDraw
import klyph.render.Canvas
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One scene of the sandbox: a name, a one-line blurb, and a way to start a fresh run of it. The
 * sandbox calls [create] when the scene is chosen and again on every reset, so a run never has to
 * know how to put itself back.
 */
interface Scene {
    /** Shown big in the corner, such as "Blob Garden". */
    val name: String

    /** One line under the name: what to look at, or what to try. */
    val blurb: String

    /** A fresh run of this scene, at its start. */
    fun create(): SceneRun
}

/**
 * A scene that is running: the physics it owns, moved one fixed step at a time. A step is the run's
 * own [beforeStep], then the [world]'s step, then its [afterStep]: in the app `addPhysics` takes the
 * middle, as it would in a game.
 */
interface SceneRun {
    /** The run's physics. A run may swap it for a new one in [beforeStep]. */
    val world: PhysicsWorld

    /** What the run does each fixed step of [dt] seconds before the physics moves: steering, spawning, motors. */
    fun beforeStep(dt: Float) {}

    /** What the run does after the physics moved: reading what happened, such as hits and sensors. */
    fun afterStep(dt: Float) {}

    /** One whole fixed step of [dt] seconds, without an app: [beforeStep], the world's step, [afterStep]. */
    fun step(dt: Float) {
        beforeStep(dt)
        world.step(stepOf(dt))
        afterStep(dt)
    }

    /** Adds this scene's own kind of thing at ([x], [y]), such as a blob or a ball: what a right click does. */
    fun spawn(x: Float, y: Float) {}

    /** Draws the run on [canvas] with [debug]; [view] is the part of the world the camera shows. */
    fun draw(canvas: Canvas, debug: DebugDraw, view: Rect) {}

    /** How many particles, bodies and contacts the run has now, for the HUD. */
    val particleCount: Int get() = 0
    val bodyCount: Int get() = 0
    val contactCount: Int get() = 0
}

/**
 * [seconds] as the [Duration] the physics steps by. The world turns it back into the same `Float`, to
 * the last bit, for every step the sandbox takes, so a run moves exactly as it did on `Float` seconds.
 */
internal fun stepOf(seconds: Float): Duration = seconds.toDouble().seconds
