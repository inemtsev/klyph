package klyph.render

import klyph.entity.Commands
import klyph.entity.ComponentType
import klyph.entity.Entity
import klyph.entity.World
import klyph.event.Events
import klyph.scene.SceneStack
import kotlin.time.Duration

/**
 * A component that plays [frames] on its entity's [Sprite], showing each frame for
 * [frameDuration] in the order [mode] sets.
 *
 * Every frame, at the start of [klyph.app.Phase.LATE_UPDATE], the engine advances [playhead] by
 * the frame's delta times [speed] and sets the entity's [Sprite.region] to [currentFrame]. An
 * entity with a `SpriteAnimation` but no [Sprite] is not animated. Pause, restart or seek through
 * [playhead], and change the playback rate with [speed].
 *
 * When an [AnimationMode.ONCE] animation has shown its last frame for the full [frameDuration],
 * the engine publishes one [AnimationFinished] event for the entity, which `LATE_UPDATE` and
 * `RENDER` systems read in the same frame, and with [AnimationEnd.DESTROY] it also destroys the
 * entity through `App.commands` before the first `LATE_UPDATE` system runs. [end] has no effect on
 * the other modes, which never finish. An animation restarted after it finished publishes again
 * when it next finishes.
 *
 * [frames] must not be empty and [frameDuration] must be positive and finite. The frames may come
 * from different textures. The animation keeps the [frames] list it is given instead of copying
 * it, so many animations can share one list without allocating; that list must not change
 * afterwards.
 *
 * [toString] gives the current frame, the frame count, the mode and the speed, for example
 * `SpriteAnimation(frame=2/6, mode=LOOP, speed=1.0, end=HOLD)`.
 *
 * Has no sample because creating a [Texture] needs a GPU device, which samples cannot open.
 *
 * @throws IllegalArgumentException if [frames] is empty, or [frameDuration] is zero, negative or
 *   infinite.
 */
public class SpriteAnimation(
    /** The regions shown in turn: the list passed to the constructor, kept rather than copied. */
    public val frames: List<TextureRegion>,
    frameDuration: Duration,
    mode: AnimationMode = AnimationMode.LOOP,
    /** What happens to the entity once an [AnimationMode.ONCE] animation has finished. */
    public val end: AnimationEnd = AnimationEnd.HOLD,
) {
    init {
        require(this.frames.isNotEmpty()) { "Sprite animation frame list is empty." }
    }

    /** The playhead over [frames], with a frame count of `frames.size`. */
    public val playhead: Animation = Animation(this.frames.size, frameDuration, mode)

    /** The region to show now: the frame at [Animation.frameIndex] of [playhead]. */
    public val currentFrame: TextureRegion
        get() = frames[playhead.frameIndex]

    /** The [Animation.finishCount] of [playhead] the engine has published [AnimationFinished] for. */
    internal var reportedFinishes: Int = 0

    /** Returns `SpriteAnimation(frame=2/6, mode=LOOP, speed=1.0, end=HOLD)`. The format may change. */
    override fun toString(): String =
        "SpriteAnimation(frame=${playhead.frameIndex}/${frames.size}, mode=${playhead.mode}, speed=${playhead.speed}, end=$end)"

    /** Component key for [SpriteAnimation]. */
    public companion object : ComponentType<SpriteAnimation>("SpriteAnimation")
}

/**
 * Playback rate of the animation, the [Animation.speed] of its [SpriteAnimation.playhead]: 1, the
 * default, shows each frame for its frame duration, 2 plays twice as fast and 0 holds the current
 * frame. Set it every frame to match the animation to motion, such as a walk cycle that speeds up
 * with the character. Must be finite and not negative.
 *
 * @throws IllegalArgumentException if set to a negative or infinite value or `NaN`.
 *
 * @sample samples.render.Animations.speed
 */
public var SpriteAnimation.speed: Float
    get() = playhead.speed
    set(value) {
        playhead.speed = value
    }

/**
 * Creates a sprite animation that plays every region of [sheet] in index order, showing each one
 * for [frameDuration] in the order [mode] sets.
 *
 * The animation shares [SpriteSheet.regions] instead of copying it, so creating one allocates no
 * frame list.
 *
 * Has no sample because creating a [Texture] needs a GPU device, which samples cannot open.
 *
 * @throws IllegalArgumentException if [sheet] has no regions, or [frameDuration] is zero, negative
 *   or infinite.
 */
public fun SpriteAnimation(
    sheet: SpriteSheet,
    frameDuration: Duration,
    mode: AnimationMode = AnimationMode.LOOP,
    end: AnimationEnd = AnimationEnd.HOLD,
): SpriteAnimation = SpriteAnimation(sheet.regions, frameDuration, mode, end)

/** Names what happens to an entity once its [AnimationMode.ONCE] [SpriteAnimation] has finished. */
public enum class AnimationEnd {
    /** Keeps the entity, showing the last frame. */
    HOLD,

    /** Destroys the entity, with its descendants, through `App.commands`. */
    DESTROY,
}

/**
 * Published once when the [AnimationMode.ONCE] [SpriteAnimation] of [entity] has shown its last
 * frame for the full frame duration, at the start of [klyph.app.Phase.LATE_UPDATE], whatever its
 * [SpriteAnimation.end]. With [AnimationEnd.DESTROY] the entity is already gone when systems read
 * the event, so check `World.isAlive` before touching its components.
 *
 * @sample samples.render.AnimationEvents.animationFinished
 */
public class AnimationFinished(
    /** The entity whose animation finished. */
    public val entity: Entity,
) {
    override fun equals(other: Any?): Boolean = other is AnimationFinished && other.entity == entity

    override fun hashCode(): Int = entity.hashCode()

    override fun toString(): String = "AnimationFinished(entity=$entity)"
}

/**
 * Advances every [SpriteAnimation] whose entity also has a [Sprite] by [delta] and shows its
 * current frame in that sprite. When [scenes] is given, an animation owned by a scene frozen below
 * its top does not advance and its sprite keeps the frame it shows. An animation that is finished
 * and not yet reported publishes [AnimationFinished] into [events] and, with
 * [AnimationEnd.DESTROY], queues destroying its entity on [commands]; without [events] or
 * [commands] that part is skipped. The engine calls it at the start of LATE_UPDATE with
 * `App.scenes`, `App.events` and `App.commands`, then applies the commands.
 */
internal fun World.advanceSpriteAnimations(
    delta: Duration,
    scenes: SceneStack? = null,
    events: Events? = null,
    commands: Commands? = null,
) {
    val frozen = if (scenes != null && scenes.hasFrozen) scenes else null
    forEach(SpriteAnimation, Sprite) { entity, spriteAnimation, sprite ->
        if (frozen != null && frozen.isFrozen(ownerAt(entity.index))) return@forEach
        val animation = spriteAnimation.playhead
        animation.advance(delta)
        sprite.region = spriteAnimation.currentFrame
        if (animation.finishCount != spriteAnimation.reportedFinishes) {
            spriteAnimation.reportedFinishes = animation.finishCount
            events?.publish(AnimationFinished(entity))
            if (spriteAnimation.end == AnimationEnd.DESTROY) commands?.destroy(entity)
        }
    }
}
