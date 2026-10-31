package klyph.render

import klyph.entity.ComponentType
import klyph.entity.World
import kotlin.time.Duration

/**
 * A component that plays [frames] on its entity's [Sprite], showing each frame for
 * [frameDuration] in the order [mode] sets.
 *
 * Every frame, at the start of [klyph.app.Phase.LATE_UPDATE], the engine advances [animation] by
 * the frame's delta and sets the entity's [Sprite.region] to [currentFrame]. An entity with a
 * `SpriteAnimation` but no [Sprite] is not animated. Pause, restart or seek through [animation].
 *
 * [frames] must not be empty and [frameDuration] must be positive and finite. The frames may come
 * from different textures.
 *
 * [toString] gives the current frame, the frame count and the mode, for example
 * `SpriteAnimation(frame=2/6, mode=LOOP)`.
 *
 * Has no sample because creating a [Texture] needs a GPU device, which samples cannot open.
 *
 * @throws IllegalArgumentException if [frames] is empty, or [frameDuration] is zero, negative or
 *   infinite.
 */
public class SpriteAnimation(
    frames: List<TextureRegion>,
    frameDuration: Duration,
    mode: AnimationMode = AnimationMode.LOOP,
) {
    /** The regions shown in turn, a copy of the list passed to the constructor. */
    public val frames: List<TextureRegion> = frames.toList()

    init {
        require(this.frames.isNotEmpty()) { "Sprite animation frame list is empty." }
    }

    /** The playhead over [frames], with a frame count of `frames.size`. */
    public val animation: Animation = Animation(this.frames.size, frameDuration, mode)

    /** The region to show now: the frame at [Animation.frameIndex] of [animation]. */
    public val currentFrame: TextureRegion
        get() = frames[animation.frameIndex]

    /** Returns `SpriteAnimation(frame=2/6, mode=LOOP)`. The format may change. */
    override fun toString(): String =
        "SpriteAnimation(frame=${animation.frameIndex}/${frames.size}, mode=${animation.mode})"

    /** Component key for [SpriteAnimation]. */
    public companion object : ComponentType<SpriteAnimation>("SpriteAnimation")
}

/**
 * Creates a sprite animation that plays every region of [sheet] in index order, showing each one
 * for [frameDuration] in the order [mode] sets.
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
): SpriteAnimation = SpriteAnimation(sheet.regions, frameDuration, mode)

/**
 * Advances every [SpriteAnimation] whose entity also has a [Sprite] by [delta] and shows its
 * current frame in that sprite. The engine calls it at the start of LATE_UPDATE.
 */
internal fun World.advanceSpriteAnimations(delta: Duration) {
    forEach(SpriteAnimation, Sprite) { _, spriteAnimation, sprite ->
        spriteAnimation.animation.advance(delta)
        sprite.region = spriteAnimation.currentFrame
    }
}
