package klyph.render

import klyph.entity.ComponentType
import klyph.platform.Color

/**
 * A textured quad of [width] by [height] world units centred on its entity's transform, showing
 * the texture region from ([u0], [v0]) to ([u1], [v1]) tinted by [color].
 */
public class Sprite(
    public var width: Float,
    public var height: Float,
    public var color: Color = Color.WHITE,
    public var u0: Float = 0f,
    public var v0: Float = 0f,
    public var u1: Float = 1f,
    public var v1: Float = 1f,
) {
    override fun toString(): String = "Sprite(size=$width x $height, color=$color, uv=($u0, $v0)-($u1, $v1))"

    /** Component key for [Sprite]. */
    public companion object : ComponentType<Sprite>("Sprite")
}
