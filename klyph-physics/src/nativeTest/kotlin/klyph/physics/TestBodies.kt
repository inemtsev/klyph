package klyph.physics

/**
 * The body and world the tests here are built from: a dome with the shape and weight of the slime
 * this module grew out of, in a world of 16-unit tiles stepped 60 times a second in four substeps.
 */
object TestBodies {
    const val RADIUS = 13.3f
    const val HEIGHT_SCALE = 1.3f
    const val ARC_SEGMENTS = 14
    const val BASE_SEGMENTS = 8
    const val CORE_MASS = 34f

    /** The step the demo takes: klyph's 1/60 s, 16 666 666 ns. */
    const val DT = 16_666_666 / 1e9f
    const val GRAVITY = 1500f
    const val TILE = 16f

    /** The step as a [kotlin.time.Duration], for [PhysicsWorld.step]. */
    val STEP: kotlin.time.Duration = kotlin.time.Duration.parse("16666666ns")

    /** A [Solid.tags] for the tests that need one. */
    val PAD: SolidTags = SolidTags.of(0)

    fun dome(): SoftBodyShape = SoftBodyShape.semiDisc(RADIUS, ARC_SEGMENTS, BASE_SEGMENTS, CORE_MASS, HEIGHT_SCALE)

    /** An empty 10 x 10 grid of tiles. */
    fun grid(): SolidGrid = SolidGrid(10, 10, TILE)
}
