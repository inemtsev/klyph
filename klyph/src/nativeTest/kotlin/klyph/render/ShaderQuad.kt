package klyph.render

import kotlin.math.cos
import kotlin.math.sin

/**
 * What `sprite.vert.hlsl` makes of one sprite record, worked out on the CPU for tests, which have
 * no GPU: where each point of the quad lands in the world and which texel it shows. [record] is the
 * 16 floats of one record: x, y, rotation, 0, width, height, originX, originY, u0, v0, u1, v1 and
 * the color. The quad's points are named by their fractions of its size from its top-left corner,
 * the unit square the shader's corner index runs over.
 */
internal class ShaderQuad(private val record: List<Float>) {
    init {
        require(record.size == SPRITE_RECORD_FLOATS) { "A record has $SPRITE_RECORD_FLOATS floats, not ${record.size}." }
    }

    /** Returns the world position, as x and y, of the point of the quad at ([unitX], [unitY]). */
    fun positionOf(unitX: Float, unitY: Float): List<Float> {
        val localX = (unitX - record[6]) * record[4]
        val localY = (unitY - record[7]) * record[5]
        val c = cos(record[2])
        val s = sin(record[2])
        return listOf(localX * c - localY * s + record[0], localX * s + localY * c + record[1])
    }

    /** Returns the texture coordinates, as u and v, that the point of the quad at ([unitX], [unitY]) shows. */
    fun textureCoordinatesOf(unitX: Float, unitY: Float): List<Float> =
        listOf(record[8] + (record[10] - record[8]) * unitX, record[9] + (record[11] - record[9]) * unitY)

    /** Returns the world position, as x and y, where the quad shows the texture coordinates ([u], [v]). */
    fun positionOfTexel(u: Float, v: Float): List<Float> =
        positionOf((u - record[8]) / (record[10] - record[8]), (v - record[9]) / (record[11] - record[9]))

    /** The world positions of the four corners, as x and y each: top-left, top-right, bottom-left, bottom-right. */
    val corners: List<Float>
        get() = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).flatMap { (x, y) -> positionOf(x, y) }

    /** The texture coordinates of the four corners, as u and v each, in the order of [corners]. */
    val cornerTexels: List<Float>
        get() = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).flatMap { (x, y) -> textureCoordinatesOf(x, y) }
}
