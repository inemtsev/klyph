package klyph.entity

/**
 * Position, rotation and scale of an entity in world units. Rotation is in radians; a positive
 * rotation turns the entity clockwise on screen, because y points down.
 *
 * @sample samples.entity.Components.transform
 */
public class Transform(
    /** Horizontal position in world units. */
    public var x: Float = 0f,
    /** Vertical position in world units. */
    public var y: Float = 0f,
    /** Rotation in radians; positive turns clockwise on screen, because y points down. */
    public var rotation: Float = 0f,
    /**
     * Horizontal scale factor. It scales the entity's sprite, text and collider; set
     * `Collider.scalesWithTransform` to `false` to keep it out of the collider.
     */
    public var scaleX: Float = 1f,
    /**
     * Vertical scale factor. It scales the entity's sprite, text and collider; set
     * `Collider.scalesWithTransform` to `false` to keep it out of the collider.
     */
    public var scaleY: Float = 1f,
) {
    override fun toString(): String = "Transform(x=$x, y=$y, rotation=$rotation, scale=${scaleX}x$scaleY)"

    /** Component key for [Transform]. */
    public companion object : ComponentType<Transform>("Transform")
}
