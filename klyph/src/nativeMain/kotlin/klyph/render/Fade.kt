package klyph.render

import klyph.entity.Commands
import klyph.entity.ComponentType
import klyph.entity.World
import klyph.platform.withAlpha
import klyph.scene.SceneStack
import klyph.text.Text
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * A component that blends the alpha of its entity's [Sprite] and `Text` colors from where they
 * are to [target] over [duration], after waiting [delay], and then does what [end] says.
 *
 * Every frame, after [klyph.app.Phase.UPDATE], the engine advances [elapsed] by the frame's delta.
 * The first time it does, it notes the alpha each color has then, so a sprite at half alpha fades
 * from half, not from opaque. Once [delay] has passed it sets each alpha to the linear blend of
 * that start and [target] at the fraction of [duration] gone by, keeping red, green and blue. When
 * [duration] has passed it sets [target] exactly and [isFinished] turns `true`; from then on it
 * leaves the colors alone, so a game may change them again. With [FadeEnd.DESTROY] it then
 * destroys the entity through `App.commands`, which apply before particles advance. A text's
 * shadow fades with it, because its alpha follows the text's. An entity with neither component
 * still counts the time and ends as [end] says. An entity owned by a scene frozen below the top of
 * `App.scenes` does not advance its fade.
 *
 * To fade the same entity again, add a new `Fade` in place of the finished one.
 *
 * [toString] gives the progress, for example
 * `Fade(elapsed=0.25s, duration=1s, delay=0s, target=0.0, end=DESTROY)`.
 *
 * @throws IllegalArgumentException if [duration] or [delay] is negative or infinite, or [target]
 *   is outside `0.0..1.0`.
 *
 * @sample samples.render.Fades.fade
 */
public class Fade(
    /** How long the blend from the start alpha to [target] takes, after [delay]. Zero jumps straight to [target]. */
    public val duration: Duration,
    /** The alpha the colors end on, in `0.0..1.0`; 0, the default, fades out. */
    public val target: Float = 0f,
    /** What happens once the fade has finished. */
    public val end: FadeEnd = FadeEnd.HOLD,
    /** How long the colors keep their alpha before the blend starts. */
    public val delay: Duration = Duration.ZERO,
) {
    init {
        require(!duration.isNegative() && duration.isFinite()) { "Fade duration $duration is negative or infinite." }
        require(!delay.isNegative() && delay.isFinite()) { "Fade delay $delay is negative or infinite." }
        require(target in 0f..1f) { "Fade target $target is outside 0.0..1.0." }
    }

    /** The time the engine has advanced this fade by, from zero up to `delay + duration`. */
    public var elapsed: Duration = Duration.ZERO
        private set

    /** Returns `true` once the engine has set the colors to [target], at `delay + duration`. */
    public var isFinished: Boolean = false
        private set

    /** The sprite's alpha when the fade first saw it, or `NaN` before then. */
    private var spriteStart = Float.NaN

    /** The text's alpha when the fade first saw it, or `NaN` before then. */
    private var textStart = Float.NaN

    /**
     * Advances the fade by [delta] and blends the alpha of [sprite] and [text], when not `null`.
     * Returns `true` if the fade finished during this call.
     */
    internal fun advance(delta: Duration, sprite: Sprite?, text: Text?): Boolean {
        if (isFinished) return false
        if (sprite != null && spriteStart.isNaN()) spriteStart = sprite.color.alpha
        if (text != null && textStart.isNaN()) textStart = text.color.alpha
        val total = delay + duration
        elapsed = if (total - elapsed > delta) elapsed + delta else total
        if (elapsed <= delay && elapsed < total) return false
        val fraction = if (elapsed >= total) 1f else ((elapsed - delay) / duration).toFloat()
        if (sprite != null) sprite.color = sprite.color.withAlpha(blend(spriteStart, fraction))
        if (text != null) text.color = text.color.withAlpha(blend(textStart, fraction))
        if (elapsed < total) return false
        isFinished = true
        return true
    }

    private fun blend(start: Float, fraction: Float): Float =
        if (fraction >= 1f) target else (start + (target - start) * fraction).coerceIn(0f, 1f)

    /** Returns `Fade(elapsed=0.25s, duration=1s, delay=0s, target=0.0, end=DESTROY)`. The format may change. */
    override fun toString(): String =
        "Fade(elapsed=${elapsed.toString(DurationUnit.SECONDS, 2)}, duration=$duration, delay=$delay, target=$target, end=$end)"

    /** Component key for [Fade]. */
    public companion object : ComponentType<Fade>("Fade")
}

/** Names what a [Fade] does once it has reached its target. */
public enum class FadeEnd {
    /** Keeps the entity, its colors at the fade's target alpha. */
    HOLD,

    /** Destroys the entity, with its descendants, through `App.commands`. */
    DESTROY,
}

/**
 * Advances the [Fade] of every entity that has one by [delta], blending the alpha of its [Sprite]
 * and [Text], and queues destroying, on [commands], each entity whose [FadeEnd.DESTROY] fade
 * finished. When [scenes] is given, a fade owned by a scene frozen below its top does not advance.
 * The engine calls it once per frame after [klyph.app.Phase.UPDATE], then applies [commands].
 * Allocates nothing.
 */
internal fun World.advanceFades(delta: Duration, commands: Commands, scenes: SceneStack? = null) {
    if (count(Fade) == 0) return
    val frozen = if (scenes != null && scenes.hasFrozen) scenes else null
    forEach(Fade) { entity, fade ->
        if (frozen != null && frozen.isFrozen(ownerAt(entity.index))) return@forEach
        if (fade.advance(delta, getOrNull(entity, Sprite), getOrNull(entity, Text)) && fade.end == FadeEnd.DESTROY) {
            commands.destroy(entity)
        }
    }
}
