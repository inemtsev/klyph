package klyph.entity

/**
 * Position, rotation and scale of an entity in world units. Rotation is in radians,
 * counter-clockwise.
 */
public class Transform(
    public var x: Float = 0f,
    public var y: Float = 0f,
    public var rotation: Float = 0f,
    public var scaleX: Float = 1f,
    public var scaleY: Float = 1f,
) {
    override fun toString(): String = "Transform(x=$x, y=$y, rotation=$rotation, scale=$scaleX x $scaleY)"

    /** Component key for [Transform]. */
    public companion object : ComponentType<Transform>("Transform")
}
