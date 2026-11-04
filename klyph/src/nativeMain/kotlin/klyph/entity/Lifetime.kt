package klyph.entity

import klyph.scene.SceneStack
import kotlin.time.Duration

/**
 * A component that destroys its entity once [remaining] has run out, such as a bullet that flies
 * for two seconds or the carrier of a burst of particles.
 *
 * Every frame, after [klyph.app.Phase.UPDATE], the engine takes the frame's delta off [remaining]
 * and, once it reaches zero, destroys the entity through `App.commands`, so its descendants go
 * with it, and applies the commands before particles advance. An entity owned by a scene frozen
 * below the top of `App.scenes` keeps its remaining time. A lifetime of zero destroys the entity
 * in the first frame the engine sees it.
 *
 * Set [remaining] to give the entity more or less time. It must not be negative or infinite.
 *
 * [toString] gives the time left, for example `Lifetime(remaining=1.5s)`.
 *
 * @throws IllegalArgumentException if [duration] is negative or infinite.
 *
 * @sample samples.entity.Lifetimes.lifetime
 */
public class Lifetime(duration: Duration) {
    init {
        requireLifetime(duration)
    }

    /**
     * The time left before the entity is destroyed: the constructor's `duration` at first, then
     * less every frame, down to zero. Setting a negative or infinite value throws
     * [IllegalArgumentException].
     */
    public var remaining: Duration = duration
        set(value) {
            requireLifetime(value)
            field = value
        }

    /** Returns `Lifetime(remaining=1.5s)`. The format may change. */
    override fun toString(): String = "Lifetime(remaining=$remaining)"

    /** Component key for [Lifetime]. */
    public companion object : ComponentType<Lifetime>("Lifetime")
}

private fun requireLifetime(duration: Duration) {
    require(!duration.isNegative() && duration.isFinite()) { "Lifetime $duration is negative or infinite." }
}

/**
 * Takes [delta] off the [Lifetime] of every entity that has one and queues destroying, on
 * [commands], each entity whose lifetime has run out. When [scenes] is given, a lifetime owned by
 * a scene frozen below its top does not count down. The engine calls it once per frame after
 * [klyph.app.Phase.UPDATE], then applies [commands]. Allocates nothing.
 */
internal fun World.advanceLifetimes(delta: Duration, commands: Commands, scenes: SceneStack? = null) {
    if (count(Lifetime) == 0) return
    val frozen = if (scenes != null && scenes.hasFrozen) scenes else null
    forEach(Lifetime) { entity, lifetime ->
        if (frozen != null && frozen.isFrozen(ownerAt(entity.index))) return@forEach
        val left = lifetime.remaining - delta
        if (left.isPositive()) {
            lifetime.remaining = left
        } else {
            lifetime.remaining = Duration.ZERO
            commands.destroy(entity)
        }
    }
}
