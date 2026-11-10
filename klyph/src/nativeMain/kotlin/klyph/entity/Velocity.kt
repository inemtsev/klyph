package klyph.entity

import klyph.scene.SceneStack

/**
 * A component that moves its entity's [Transform] by [dx] and [dy] world units per second, such as
 * a bullet, a falling rock or a score popup that rises.
 *
 * In every fixed step, after the [klyph.app.Phase.FIXED_UPDATE] systems have run and their
 * commands have been applied, the engine adds [dx] and [dy] times the fixed step length to the
 * entity's [Transform], so an entity with a velocity moves exactly as one a `FIXED_UPDATE` system
 * moves by `velocity * time.deltaSeconds`. A system that changes the velocity in `FIXED_UPDATE`,
 * for example to apply gravity or steer, therefore affects the move of the same step. The move
 * happens after the step's `Interpolated` record, so a marked entity is drawn blended between its
 * last two steps. An entity without a [Transform] does not move, and an entity owned by a scene
 * frozen below the top of `App.scenes` stands still. The velocity is in the parent's space for an
 * entity with a parent, because it changes the local [Transform].
 *
 * [toString] gives both components, for example `Velocity(dx=0.0, dy=-40.0)`.
 *
 * @sample samples.entity.Velocities.velocity
 */
public class Velocity(
    /** Horizontal speed in world units per second; positive moves right. */
    public var dx: Float = 0f,
    /** Vertical speed in world units per second; positive moves down the screen, because y points down. */
    public var dy: Float = 0f,
) {
    /** Returns `Velocity(dx=0.0, dy=-40.0)`. The format may change. */
    override fun toString(): String = "Velocity(dx=$dx, dy=$dy)"

    /** Component key for [Velocity]. */
    public companion object : ComponentType<Velocity>("Velocity")
}

/**
 * Moves the [Transform] of every entity that has a [Velocity] by its velocity times [deltaSeconds].
 * When [scenes] is given, an entity owned by a scene frozen below its top does not move. The
 * engine calls it once per fixed step, after the `FIXED_UPDATE` systems, with the step length.
 * Allocates nothing.
 */
internal fun World.applyVelocities(deltaSeconds: Float, scenes: SceneStack? = null) {
    if (count(Velocity) == 0) return
    val frozen = if (scenes != null && scenes.hasFrozen) scenes else null
    forEach(Velocity, Transform) { entity, velocity, transform ->
        if (frozen != null && frozen.isFrozen(ownerAt(entity.index))) return@forEach
        transform.x += velocity.dx * deltaSeconds
        transform.y += velocity.dy * deltaSeconds
    }
}
