package klyph.collision

/**
 * The outline a [Collider] or a [Collisions] query tests for overlap, centered on its position.
 * Sizes are in world units before the entity's scale. Shapes are immutable; give a collider a
 * new shape to resize it.
 *
 * @sample samples.collision.Shapes.shapes
 */
public sealed interface Shape {
    /**
     * Creates an axis-aligned rectangle [width] by [height] world units, centered on its position.
     * Both sizes must be positive. The entity's rotation does not turn it.
     *
     * Two boxes are equal when their sizes are. [toString] gives `Box(width=16.0, height=8.0)`.
     *
     * @throws IllegalArgumentException if [width] or [height] is not positive.
     *
     * @sample samples.collision.Shapes.shapes
     */
    public class Box(
        /** The width in world units, before scale. */
        public val width: Float,
        /** The height in world units, before scale. */
        public val height: Float,
    ) : Shape {
        init {
            require(width > 0f) { "Box width $width is not positive." }
            require(height > 0f) { "Box height $height is not positive." }
        }

        override fun equals(other: Any?): Boolean = other is Box && other.width == width && other.height == height

        override fun hashCode(): Int = 31 * width.hashCode() + height.hashCode()

        override fun toString(): String = "Box(width=$width, height=$height)"
    }

    /**
     * Creates a circle of [radius] world units around its position. The radius must be positive.
     *
     * Two circles are equal when their radii are. [toString] gives `Circle(radius=8.0)`.
     *
     * @throws IllegalArgumentException if [radius] is not positive.
     *
     * @sample samples.collision.Shapes.shapes
     */
    public class Circle(
        /** The radius in world units, before scale. */
        public val radius: Float,
    ) : Shape {
        init {
            require(radius > 0f) { "Circle radius $radius is not positive." }
        }

        override fun equals(other: Any?): Boolean = other is Circle && other.radius == radius

        override fun hashCode(): Int = radius.hashCode()

        override fun toString(): String = "Circle(radius=$radius)"
    }
}
