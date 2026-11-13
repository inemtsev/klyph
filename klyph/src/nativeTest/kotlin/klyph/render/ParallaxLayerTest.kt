package klyph.render

import klyph.entity.Entity
import klyph.entity.Interpolated
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.recordPreviousTransforms
import klyph.math.Rect
import klyph.math.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParallaxLayerTest {
    private val region = Texture(8, 8).asRegion()

    /** A camera that sees 800 by 600 world units around ([x], [y]) at zoom 1, the size most tests use. */
    private fun camera(x: Float = 0f, y: Float = 0f): Camera = Camera(x = x, y = y, viewportWidth = 800f, viewportHeight = 600f)

    /** Adds an entity with a 100 by 50 sprite at ([x], [y]) and [layer], and returns it. */
    private fun World.layer(layer: ParallaxLayer, x: Float = 0f, y: Float = 0f, rotation: Float = 0f): Entity {
        val entity = create()
        add(entity, Transform, Transform(x, y, rotation))
        add(entity, Sprite, Sprite(region, width = 100f, height = 50f))
        add(entity, ParallaxLayer, layer)
        return entity
    }

    /**
     * Returns the records the sprite pass writes for [entity] seen through [camera], one per copy, as
     * the plan makes them: the record of the sprite, moved to each copy.
     */
    private fun World.recordsOf(entity: Entity, camera: Camera, alpha: Float = 1f): List<List<Float>> {
        val plan = ParallaxPlan()
        plan.update(this, camera, alpha)
        val slot = plan.layerSlots?.get(entity.index) ?: -1
        if (slot < 0) return emptyList()
        return (0 until plan.copiesOf(slot)).map { copy ->
            if (copy == 0) plan.fill(this, slot)
            plan.placeCopy(slot, copy)
            plan.template.toList()
        }
    }

    private fun World.positionsOf(entity: Entity, camera: Camera, alpha: Float = 1f): List<Pair<Float, Float>> =
        recordsOf(entity, camera, alpha).map { it[0] to it[1] }

    private fun Camera.screenOf(position: Pair<Float, Float>): Vec2 = worldToScreen(Vec2(position.first, position.second))

    @Test
    fun aFactorOfOneKeepsTheSpriteWhereItsTransformIs() {
        val world = World()
        val entity = world.layer(ParallaxLayer(1f), x = 100f, y = 50f)
        val camera = camera(x = 300f, y = 200f).apply { offsetX = 6f; offsetY = -4f }
        assertEquals(listOf(100f to 50f), world.positionsOf(entity, camera))
    }

    @Test
    fun aFactorOfZeroSticksToTheScreenWhereverTheCameraIs() {
        val world = World()
        val entity = world.layer(ParallaxLayer(0f), x = 100f, y = 50f)
        val camera = camera(x = 300f, y = 200f)
        // The transform is an offset from the view's center: the camera's position plus (100, 50).
        assertEquals(listOf(400f to 250f), world.positionsOf(entity, camera))

        for ((x, y) in listOf(0f to 0f, -1234f to 56f, 9000f to -7000f)) {
            camera.x = x
            camera.y = y
            val position = world.positionsOf(entity, camera).single()
            // 400 and 300 are the viewport's center in points; the offset of (100, 50) is at zoom 1.
            assertEquals(Vec2(500f, 350f), camera.screenOf(position), "Camera at $x, $y.")
        }
    }

    @Test
    fun aFractionScrollsByThatFractionOfTheCameraEachAxisOnItsOwn() {
        val world = World()
        val half = world.layer(ParallaxLayer(0.5f), x = 100f, y = 50f)
        val mixed = world.layer(ParallaxLayer(factorX = 0.25f, factorY = 2f), x = 100f, y = 50f)
        val camera = camera(x = 300f, y = 200f)
        assertEquals(listOf(250f to 150f), world.positionsOf(half, camera))
        // 100 + 0.75 * 300 and 50 + (1 - 2) * 200.
        assertEquals(listOf(325f to -150f), world.positionsOf(mixed, camera))
    }

    @Test
    fun theFactorOfYStartsAsTheFactorOfX() {
        val layer = ParallaxLayer(0.4f)
        assertEquals(0.4f, layer.factorY)
        assertEquals(0f, layer.repeatX)
        assertEquals(0f, layer.repeatY)
        assertEquals(0.1f, ParallaxLayer(0.4f, 0.1f).factorY)
    }

    @Test
    fun theViewIsTheCameraAfterItsBoundsAndWithItsOffset() {
        val world = World()
        val sky = world.layer(ParallaxLayer(0f))
        val hills = world.layer(ParallaxLayer(0.5f))
        val camera = camera(x = 5000f, y = -90f)
        camera.bounds = Rect(x = 0f, y = 0f, width = 1000f, height = 800f)
        // The plan reads the camera as it is: the engine clamps it just before it draws.
        assertEquals(listOf(5000f to -90f), world.positionsOf(sky, camera))
        camera.clampToBounds()
        assertEquals(listOf(600f to 300f), world.positionsOf(sky, camera))
        assertEquals(listOf(300f to 150f), world.positionsOf(hills, camera))

        camera.offsetX = 12f
        camera.offsetY = -7f
        assertEquals(listOf(612f to 293f), world.positionsOf(sky, camera))
        assertEquals(listOf(306f to 146.5f), world.positionsOf(hills, camera))
    }

    @Test
    fun aLayerWithAFactorOfZeroDoesNotShakeAndOneWithAFactorOfOneDoes() {
        val world = World()
        val sky = world.layer(ParallaxLayer(0f), x = 20f, y = 10f)
        val ground = world.layer(ParallaxLayer(1f), x = 20f, y = 10f)
        val camera = camera(x = 250f, y = 100f)
        val skyAtRest = camera.screenOf(world.positionsOf(sky, camera).single())
        val groundAtRest = camera.screenOf(world.positionsOf(ground, camera).single())
        camera.offsetX = 9f
        camera.offsetY = -5f
        assertEquals(skyAtRest, camera.screenOf(world.positionsOf(sky, camera).single()))
        assertEquals(Vec2(groundAtRest.x - 9f, groundAtRest.y + 5f), camera.screenOf(world.positionsOf(ground, camera).single()))
    }

    @Test
    fun theRecordIsThatOfAnySpriteExceptForItsPosition() {
        val world = World()
        val entity = world.layer(ParallaxLayer(0.5f, repeatX = 400f), x = 10f, y = 20f, rotation = 0.5f)
        world.get(entity, Transform).apply { scaleX = 2f; scaleY = 3f }
        val record = world.recordsOf(entity, camera()).first()
        // Size is the sprite's 100 by 50 times the scale; rotation and color are the sprite's own.
        assertEquals(listOf(0.5f, 0f, 200f, 150f), record.subList(2, 6))
        assertEquals(listOf(1f, 1f, 1f, 1f), record.subList(12, 16))
        assertEquals(16, record.size)
    }

    @Test
    fun aRepeatDrawsEveryCopyThatTouchesTheViewAndNoOther() {
        val world = World()
        val entity = world.layer(ParallaxLayer(1f, repeatX = 100f))
        // The view is -400..400 and the sprite is 100 wide, so centers from -450 to 450 touch it.
        val xs = world.positionsOf(entity, camera()).map { it.first }
        assertEquals(listOf(-400f, -300f, -200f, -100f, 0f, 100f, 200f, 300f, 400f), xs)
        assertTrue(world.positionsOf(entity, camera()).all { it.second == 0f })

        // Scrolled, the copies move on with the view, still one at each end touching it.
        val scrolled = world.positionsOf(entity, camera(x = 60f)).map { it.first }
        assertEquals(listOf(-300f, -200f, -100f, 0f, 100f, 200f, 300f, 400f, 500f), scrolled)
        // A view that has moved a long way finds its copies at its own end of the world.
        val far = world.positionsOf(entity, camera(x = 1_000_000f)).map { it.first }
        assertEquals(9, far.size)
        assertEquals(1_000_000f, far[4])
    }

    @Test
    fun aCopyThatReachesTheViewFromItsOriginIsDrawnToo() {
        val world = World()
        val entity = world.layer(ParallaxLayer(1f, repeatX = 100f))
        // The origin at the left edge puts the whole quad to the right of its position, so the copy
        // at -500 reaches the view's left edge at -400 and must be drawn.
        world.get(entity, Sprite).originX = 0f
        val xs = world.positionsOf(entity, camera()).map { it.first }
        assertEquals(-500f, xs.first())
        assertTrue(xs.contains(400f))
    }

    @Test
    fun copiesRepeatFromTheShiftedPositionNotTheTransform() {
        val world = World()
        val entity = world.layer(ParallaxLayer(0.5f, repeatX = 100f), x = 17f)
        // At a factor of 0.5 and camera x of 1000 the sprite is at 17 + 500, so copies sit at 17 + 100k.
        val xs = world.positionsOf(entity, camera(x = 1000f)).map { it.first }
        assertEquals(9, xs.size)
        assertEquals(listOf(617f, 717f, 817f, 917f, 1017f, 1117f, 1217f, 1317f, 1417f), xs)
    }

    @Test
    fun zoomingOutAddsCopiesSoTheViewNeverShowsAnEdge() {
        val world = World()
        val entity = world.layer(ParallaxLayer(1f, repeatX = 100f))
        val camera = camera()
        camera.zoom = 0.25f
        // The view is 3200 wide: -1600..1600, so centers from -1650 to 1650 touch it.
        val xs = world.positionsOf(entity, camera).map { it.first }
        assertEquals((-16..16).map { it * 100f }, xs)
        camera.zoom = 2f
        assertEquals((-2..2).map { it * 100f }, world.positionsOf(entity, camera).map { it.first })
    }

    @Test
    fun aRepeatOnOneAxisLeavesTheOtherWhereItIs() {
        val world = World()
        val entity = world.layer(ParallaxLayer(1f, repeatY = 50f), x = 33f)
        // The view is -300..300 tall and the sprite 50 tall: centers from -325 to 325.
        val positions = world.positionsOf(entity, camera())
        assertEquals((-6..6).map { 33f to it * 50f }, positions)
        // The x axis has no repeat, so it is not culled however far from the view it is.
        val aside = world.layer(ParallaxLayer(1f, repeatY = 50f), x = 90_000f)
        assertEquals(13, world.positionsOf(aside, camera()).size)
    }

    @Test
    fun aRepeatOnBothAxesFillsRowsFromTheTopLeft() {
        val world = World()
        val entity = world.layer(ParallaxLayer(1f, repeatX = 100f, repeatY = 50f))
        val positions = world.positionsOf(entity, camera())
        assertEquals(9 * 13, positions.size)
        assertEquals(-400f to -300f, positions[0])
        assertEquals(-300f to -300f, positions[1])
        assertEquals(400f to -300f, positions[8])
        assertEquals(-400f to -250f, positions[9])
        assertEquals(400f to 300f, positions.last())
    }

    @Test
    fun aTinyPeriodStopsAtTheMostCopiesInsteadOfFillingTheView() {
        val world = World()
        val tiny = world.layer(ParallaxLayer(1f, repeatX = 0.001f))
        val grid = world.layer(ParallaxLayer(1f, repeatX = 1f, repeatY = 1f))
        val plan = ParallaxPlan()
        plan.update(world, camera(), 1f)
        assertEquals(256, ParallaxLayer.MAX_COPIES)
        assertEquals(ParallaxLayer.MAX_COPIES, plan.copiesOf(plan.layerSlots!![tiny.index]))
        assertEquals(ParallaxLayer.MAX_COPIES, plan.copiesOf(plan.layerSlots!![grid.index]))
        assertEquals(2 * (ParallaxLayer.MAX_COPIES - 1), plan.extraRecords)

        // Zoomed far out the view is huge, and a period of 1 still ends at the same limit.
        val camera = camera()
        camera.zoom = 1e-6f
        plan.update(world, camera, 1f)
        assertEquals(ParallaxLayer.MAX_COPIES, plan.copiesOf(plan.layerSlots!![grid.index]))
    }

    @Test
    fun aPositionThatIsNotANumberOrAnOverflowingPeriodDrawsNothing() {
        val world = World()
        val lost = world.layer(ParallaxLayer(1f, repeatX = 100f), x = Float.NaN)
        val overflow = world.layer(ParallaxLayer(1f, repeatX = 1e-45f), x = 9e4f)
        val plain = world.layer(ParallaxLayer(1f), x = Float.NaN)
        assertEquals(emptyList(), world.positionsOf(lost, camera()))
        assertEquals(emptyList(), world.positionsOf(overflow, camera()))
        // With no repeat it is a sprite like any other, drawn wherever its position is.
        assertTrue(world.positionsOf(plain, camera()).single().first.isNaN())
    }

    @Test
    fun aRotatedCameraCoversTheBoxAroundItsView() {
        val world = World()
        val entity = world.layer(ParallaxLayer(1f, repeatX = 100f))
        val camera = camera()
        // A quarter turn: the view is 600 wide along x and 800 along y, so centers -350..350 touch it.
        camera.rotation = (PI / 2).toFloat()
        assertEquals((-3..3).map { it * 100f }, world.positionsOf(entity, camera).map { it.first })
        // An eighth: the box around 800 by 600 is 989.9 wide, so centers from -544.9 to 544.9 touch it.
        camera.rotation = (PI / 4).toFloat()
        assertEquals((-5..5).map { it * 100f }, world.positionsOf(entity, camera).map { it.first })
    }

    @Test
    fun aTurnedSpriteReachesByItsTurnedSize() {
        val world = World()
        val flat = world.layer(ParallaxLayer(1f, repeatX = 100f))
        val turned = world.layer(ParallaxLayer(1f, repeatX = 100f), rotation = (PI / 2).toFloat())
        world.get(turned, Sprite).apply { width = 100f; height = 300f }
        // Upright it is 100 wide: centers -450..450. Turned a quarter, its 300 height lies along x: -550..550.
        assertEquals(9, world.positionsOf(flat, camera()).size)
        assertEquals((-5..5).map { it * 100f }, world.positionsOf(turned, camera()).map { it.first })
    }

    @Test
    fun aScaledSpriteReachesByItsDrawnSize() {
        val world = World()
        val entity = world.layer(ParallaxLayer(1f, repeatX = 200f))
        world.get(entity, Transform).scaleX = -2f
        // 200 wide on screen whatever the sign: centers -500..500 touch the view, so -400 and 400 only at multiples of 200.
        assertEquals(listOf(-400f, -200f, 0f, 200f, 400f), world.positionsOf(entity, camera()).map { it.first })
    }

    @Test
    fun aLayerIsBlendedBetweenFixedStepsAndThenShifted() {
        val world = World()
        val entity = world.layer(ParallaxLayer(0.5f), x = 100f)
        world.add(entity, Interpolated, Interpolated)
        val transform = world.get(entity, Transform)
        world.recordPreviousTransforms()
        transform.x = 130f
        world.recordPreviousTransforms()
        transform.x = 160f
        val camera = camera(x = 100f)
        // Halfway from 130 to 160 is 145, and half of the camera's 100 is added after.
        assertEquals(listOf(195f to 0f), world.positionsOf(entity, camera, alpha = 0.5f))
        assertEquals(listOf(180f to 0f), world.positionsOf(entity, camera, alpha = 0f))
        assertEquals(listOf(210f to 0f), world.positionsOf(entity, camera, alpha = 1f))
    }

    @Test
    fun theRecordsOfEveryLayerAreCountedBesideTheSpritesThatHaveThem() {
        val world = World()
        world.layer(ParallaxLayer(1f, repeatX = 100f))
        world.layer(ParallaxLayer(0.5f))
        world.layer(ParallaxLayer(1f, repeatY = 100f))
        val plan = ParallaxPlan()
        plan.update(world, camera(), 1f)
        // 9 copies, 1, and 7 (centers -300..300 touch the 600 tall view): 8 + 0 + 6 more than one each.
        assertEquals(8 + 0 + 6, plan.extraRecords)
        plan.update(World(), camera(), 1f)
        assertEquals(0, plan.extraRecords)
        assertNull(plan.layerSlots)
    }

    @Test
    fun aHiddenSpriteOrOneWithoutATransformOrSpriteDrawsNothingAndIsNotChecked() {
        val world = World()
        val hidden = world.layer(ParallaxLayer(Float.NaN))
        world.get(hidden, Sprite).isVisible = false
        val bare = world.create()
        world.add(bare, ParallaxLayer, ParallaxLayer(-1f))
        val noSprite = world.create()
        world.add(noSprite, Transform, Transform())
        world.add(noSprite, ParallaxLayer, ParallaxLayer(-1f))
        val plan = ParallaxPlan()
        plan.update(world, camera(), 1f)
        for (entity in listOf(hidden, bare, noSprite)) assertEquals(0, plan.copiesOf(plan.layerSlots!![entity.index]))
        assertEquals(0, plan.extraRecords)
    }

    @Test
    fun theSlotsOfManyLayersStayTheirOwn() {
        val world = World()
        // More layers than the plan starts with room for, each with its own position and period.
        val layers = (0 until 30).map { world.layer(ParallaxLayer(1f, repeatX = 100f + it), x = it.toFloat()) }
        val plan = ParallaxPlan()
        plan.update(world, camera(), 1f)
        for ((index, entity) in layers.withIndex()) {
            val period = 100f + index
            val xs = world.positionsOf(entity, camera()).map { it.first }
            // Centers at index + period * k from -450 to 450, as counted one by one.
            assertEquals((-10..10).map { index + period * it }.filter { it >= -450f && it <= 450f }, xs, "Layer $index.")
            assertEquals(xs.size, plan.copiesOf(plan.layerSlots!![entity.index]), "Layer $index.")
        }
    }

    @Test
    fun everyCopyThatTouchesTheViewIsDrawnAndNoCopyThatMissesIt() {
        val random = Random(14)
        repeat(300) { trial ->
            val world = World()
            val sprite = Sprite(region, width = random.nextFloat() * 380f + 20f, height = random.nextFloat() * 380f + 20f)
            val turn = if (random.nextBoolean()) 0f else random.nextFloat() * 6f - 3f
            val layer = ParallaxLayer(random.nextFloat() * 2f, random.nextFloat() * 2f, repeatX = random.nextFloat() * 470f + 30f)
            // A repeat on both axes needs coarser periods for the copies to stay under the limit.
            if (random.nextBoolean()) {
                layer.repeatX = random.nextFloat() * 350f + 150f
                layer.repeatY = random.nextFloat() * 350f + 150f
            }
            val entity = world.create()
            val transform = Transform(random.nextFloat() * 2000f - 1000f, random.nextFloat() * 2000f - 1000f, turn)
            transform.scaleX = random.nextFloat() * 4f - 2f
            transform.scaleY = random.nextFloat() * 4f - 2f
            world.add(entity, Transform, transform)
            world.add(entity, Sprite, sprite)
            world.add(entity, ParallaxLayer, layer)
            val camera = Camera(
                x = random.nextFloat() * 8000f - 4000f,
                y = random.nextFloat() * 8000f - 4000f,
                zoom = random.nextFloat() * 2.5f + 0.5f,
                rotation = if (random.nextBoolean()) 0f else random.nextFloat() * 6f - 3f,
                viewportWidth = 800f,
                viewportHeight = 600f,
            )
            camera.offsetX = random.nextFloat() * 20f - 10f
            camera.offsetY = random.nextFloat() * 20f - 10f

            val drawn = world.positionsOf(entity, camera)
            val expected = expectedCopies(transform, sprite, layer, camera)
            val missing = expected.inner.filter { copy -> drawn.none { isNear(it, copy) } }
            val extra = drawn.filter { copy -> expected.outer.none { isNear(it, copy) } }
            assertTrue(missing.isEmpty() && extra.isEmpty(), "Trial $trial: missing $missing, extra $extra from $layer, $camera.")
        }
    }

    /** Returns `true` if [a] and [b] are the same position up to the rounding of a float. */
    private fun isNear(a: Pair<Float, Float>, b: Pair<Float, Float>): Boolean =
        abs(a.first - b.first) < 0.01f && abs(a.second - b.second) < 0.01f

    /** The copies that touch the view by more than a margin, and those within a margin of it, as a sprite's positions. */
    private class Expected(val inner: Set<Pair<Float, Float>>, val outer: Set<Pair<Float, Float>>)

    /**
     * Works out by trying every copy in a wide range which ones touch the view, in doubles and
     * without the plan's shortcuts. A copy within [MARGIN] of the edge may go either way, so the
     * answer is the pair of sets it must be between.
     */
    private fun expectedCopies(transform: Transform, sprite: Sprite, layer: ParallaxLayer, camera: Camera): Expected {
        val centerX = camera.x.toDouble() + camera.offsetX
        val centerY = camera.y.toDouble() + camera.offsetY
        val c = abs(cos(camera.rotation.toDouble()))
        val s = abs(sin(camera.rotation.toDouble()))
        val viewX = (c * camera.visibleWidth + s * camera.visibleHeight) / 2
        val viewY = (s * camera.visibleWidth + c * camera.visibleHeight) / 2
        val width = abs(sprite.width * transform.scaleX).toDouble()
        val height = abs(sprite.height * transform.scaleY).toDouble()
        val cs = abs(cos(transform.rotation.toDouble()))
        val sn = abs(sin(transform.rotation.toDouble()))
        val spriteX = (cs * width + sn * height) / 2
        val spriteY = (sn * width + cs * height) / 2
        val x = transform.x + (1.0 - layer.factorX) * centerX
        val y = transform.y + (1.0 - layer.factorY) * centerY
        val columns = axisCopies(x, layer.repeatX.toDouble(), centerX, viewX + spriteX)
        val rows = axisCopies(y, layer.repeatY.toDouble(), centerY, viewY + spriteY)
        val inner = HashSet<Pair<Float, Float>>()
        val outer = HashSet<Pair<Float, Float>>()
        for ((cx, cxInside) in columns) {
            for ((cy, cyInside) in rows) {
                outer.add(cx.toFloat() to cy.toFloat())
                if (cxInside && cyInside) inner.add(cx.toFloat() to cy.toFloat())
            }
        }
        return Expected(inner, outer)
    }

    /**
     * Returns the centers [period] apart from [position], 0 for just that one, within [reach] plus a margin of [center],
     * each with whether it is inside by the margin.
     */
    private fun axisCopies(position: Double, period: Double, center: Double, reach: Double): List<Pair<Double, Boolean>> {
        if (period == 0.0) return listOf(position to true)
        val first = floor((center - reach - MARGIN - position) / period).toInt() - 1
        val last = ceil((center + reach + MARGIN - position) / period).toInt() + 1
        return (first..last).map { position + it * period }
            .filter { abs(it - center) <= reach + MARGIN }
            .map { it to (abs(it - center) <= reach - MARGIN) }
    }

    @Test
    fun aLayerBelowAParentFailsAndNamesTheEntityAndItsParent() {
        val world = World()
        val parent = world.create()
        world.add(parent, Transform, Transform())
        val child = world.layer(ParallaxLayer(0.5f))
        world.setParent(child, parent)
        val error = assertFailsWith<IllegalStateException> { world.recordsOf(child, camera()) }
        assertEquals(
            "Entity(1:0) has a ParallaxLayer, which needs an entity without a parent, but its parent is Entity(0:0).",
            error.message,
        )
    }

    @Test
    fun aTileSizeOnATextureThatDoesNotRepeatFailsWhenTheLayerIsPlanned() {
        val world = World()
        val entity = world.layer(ParallaxLayer(0.5f, repeatX = 100f))
        // Texture(8, 8) is clamped, so the plan rejects the tiling before any record is written.
        world.get(entity, Sprite).tileWidth = 4f
        val error = assertFailsWith<IllegalStateException> { world.recordsOf(entity, camera()) }
        assertTrue(error.message!!.startsWith("Entity(0:0) has a Sprite with a tile size"), error.message)
    }

    @Test
    fun aFactorThatIsNegativeInfiniteOrNotANumberFailsAndNamesTheEntity() {
        val cases = listOf(
            ParallaxLayer(-0.5f) to "-0.5x-0.5",
            ParallaxLayer(0.5f, Float.NaN) to "0.5xNaN",
            ParallaxLayer(Float.NaN) to "NaNxNaN",
            ParallaxLayer(Float.POSITIVE_INFINITY, 1f) to "Infinityx1.0",
            ParallaxLayer(1f, -1e-9f) to "1.0x-1.0E-9",
        )
        for ((layer, shown) in cases) {
            val world = World()
            val entity = world.layer(layer)
            val error = assertFailsWith<IllegalStateException>(shown) { world.recordsOf(entity, camera()) }
            assertEquals("Entity(0:0) has a ParallaxLayer with factor $shown, which is negative, infinite or not a number.", error.message)
        }
    }

    @Test
    fun aRepeatThatIsNegativeInfiniteOrNotANumberFailsAndAZeroRepeatIsNoRepeat() {
        val cases = listOf(
            ParallaxLayer(1f, repeatX = -1f) to "-1.0x0.0",
            ParallaxLayer(1f, repeatY = Float.NaN) to "0.0xNaN",
            ParallaxLayer(1f, repeatX = Float.POSITIVE_INFINITY) to "Infinityx0.0",
            ParallaxLayer(1f, repeatX = Float.NEGATIVE_INFINITY) to "-Infinityx0.0",
        )
        for ((layer, shown) in cases) {
            val world = World()
            val entity = world.layer(layer)
            val error = assertFailsWith<IllegalStateException>(shown) { world.recordsOf(entity, camera()) }
            assertEquals("Entity(0:0) has a ParallaxLayer with repeat $shown, which is negative, infinite or not a number.", error.message)
        }
        val world = World()
        val entity = world.layer(ParallaxLayer(1f, repeatX = 0f, repeatY = 0f))
        assertEquals(1, world.recordsOf(entity, camera()).size)
    }

    @Test
    fun theMessageNamesTheEntityThatHasTheLayer() {
        val world = World()
        world.layer(ParallaxLayer(1f))
        world.layer(ParallaxLayer(1f, repeatX = 100f))
        val third = world.layer(ParallaxLayer(-1f))
        val error = assertFailsWith<IllegalStateException> { world.recordsOf(third, camera()) }
        assertTrue(error.message.orEmpty().startsWith("Entity(2:0) has a ParallaxLayer with factor"), "Got ${error.message}.")
    }

    @Test
    fun aLayerChangedAfterItsFirstFrameIsPlannedAgain() {
        val world = World()
        val layer = ParallaxLayer(1f)
        val entity = world.layer(layer, x = 5f)
        val plan = ParallaxPlan()
        plan.update(world, camera(x = 100f), 1f)
        val slot = plan.layerSlots!![entity.index]
        assertEquals(1, plan.copiesOf(slot))
        layer.factorX = 0f
        layer.repeatX = 100f
        plan.update(world, camera(x = 100f), 1f)
        // At a factor of 0 the sprite is at 5 + 100, and the view's 800 reaches from -300 to 500.
        assertEquals(9, plan.copiesOf(slot))
        plan.fill(world, slot)
        plan.placeCopy(slot, 0)
        assertEquals(-295f, plan.template[0])
        layer.factorX = -1f
        assertFailsWith<IllegalStateException> { plan.update(world, camera(x = 100f), 1f) }
    }

    private companion object {
        /** How far from the view's edge a copy may be and go either way in the plan's floats. */
        const val MARGIN = 0.05
    }

    @Test
    fun toStringShowsBothPairs() {
        assertEquals("ParallaxLayer(factor=0.5x0.5, repeat=1024.0x0.0)", ParallaxLayer(0.5f, repeatX = 1024f).toString())
        assertEquals("ParallaxLayer(factor=0.0x1.0, repeat=0.0x64.0)", ParallaxLayer(0f, 1f, repeatY = 64f).toString())
        assertEquals("ComponentType(ParallaxLayer)", ParallaxLayer.toString())
    }
}
