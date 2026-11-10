package klyph.collision

import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.entity.propagateTransforms
import klyph.event.Events
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollisionsTest {
    private fun World.spawn(
        shape: Shape,
        x: Float,
        y: Float,
        layers: CollisionLayers = CollisionLayers.DEFAULT,
        mask: CollisionLayers = CollisionLayers.ALL,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        scalesWithTransform: Boolean = true,
    ): Entity {
        val entity = create()
        add(entity, Transform, Transform(x = x, y = y, scaleX = scaleX, scaleY = scaleY))
        val collider = Collider(shape, offsetX = offsetX, offsetY = offsetY, layers = layers, mask = mask)
        collider.scalesWithTransform = scalesWithTransform
        add(entity, Collider, collider)
        return entity
    }

    private fun detect(world: World, collisions: Collisions = Collisions()): List<Overlap> {
        val events = Events()
        collisions.detect(world, events)
        val overlaps = ArrayList<Overlap>()
        events.forEach<Overlap> { overlaps.add(it) }
        assertEquals(overlaps.size, collisions.pairCount)
        return overlaps
    }

    /** Returns whether shapes [a] at ([ax], 0) and [b] at ([bx], [by]) overlap, checked in both orders. */
    private fun overlap(a: Shape, b: Shape, bx: Float, by: Float, ax: Float = 0f): Boolean {
        val world = World()
        world.spawn(a, ax, 0f)
        world.spawn(b, bx, by)
        val forward = detect(world).size
        val reverse = World().run {
            spawn(b, bx, by)
            spawn(a, ax, 0f)
            detect(this).size
        }
        assertEquals(forward, reverse)
        assertTrue(forward <= 1)
        return forward == 1
    }

    @Test
    fun circleCircle() {
        assertTrue(overlap(Shape.Circle(5f), Shape.Circle(5f), bx = 9.9f, by = 0f))
        // Touching at one point is not an overlap.
        assertEquals(false, overlap(Shape.Circle(5f), Shape.Circle(5f), bx = 10f, by = 0f))
        assertEquals(false, overlap(Shape.Circle(5f), Shape.Circle(5f), bx = 6f, by = 8f))
        // Bounding boxes overlap but the circles do not.
        assertEquals(false, overlap(Shape.Circle(5f), Shape.Circle(5f), bx = 7.2f, by = 7.2f))
        // Containment.
        assertTrue(overlap(Shape.Circle(10f), Shape.Circle(1f), bx = 2f, by = 2f))
        assertTrue(overlap(Shape.Circle(3f), Shape.Circle(3f), bx = 0f, by = 0f))
    }

    @Test
    fun boxBox() {
        assertTrue(overlap(Shape.Box(width = 10f, height = 10f), Shape.Box(width = 10f, height = 10f), bx = 9.5f, by = 9.5f))
        // Shared edges and corners are not overlaps.
        assertEquals(false, overlap(Shape.Box(width = 10f, height = 10f), Shape.Box(width = 10f, height = 10f), bx = 10f, by = 0f))
        assertEquals(false, overlap(Shape.Box(width = 10f, height = 10f), Shape.Box(width = 10f, height = 10f), bx = 0f, by = 10f))
        assertEquals(false, overlap(Shape.Box(width = 10f, height = 10f), Shape.Box(width = 10f, height = 10f), bx = 10f, by = 10f))
        assertEquals(false, overlap(Shape.Box(width = 10f, height = 10f), Shape.Box(width = 4f, height = 4f), bx = 0f, by = 20f))
        // Containment.
        assertTrue(overlap(Shape.Box(width = 100f, height = 100f), Shape.Box(width = 2f, height = 2f), bx = 10f, by = -10f))
        // A thin box crossing a wide one.
        assertTrue(overlap(Shape.Box(width = 100f, height = 2f), Shape.Box(width = 2f, height = 100f), bx = 0f, by = 0f))
    }

    @Test
    fun circleBox() {
        // Box 10x10 centered at 0: edges at +-5. Circle of radius 2.
        assertTrue(overlap(Shape.Box(width = 10f, height = 10f), Shape.Circle(2f), bx = 6.9f, by = 0f))
        assertEquals(false, overlap(Shape.Box(width = 10f, height = 10f), Shape.Circle(2f), bx = 7f, by = 0f))
        assertEquals(false, overlap(Shape.Box(width = 10f, height = 10f), Shape.Circle(2f), bx = 0f, by = -7f))
        // Near a corner: the bounding boxes overlap, the circle misses the corner.
        assertEquals(false, overlap(Shape.Box(width = 10f, height = 10f), Shape.Circle(2f), bx = 6.5f, by = 6.5f))
        assertTrue(overlap(Shape.Box(width = 10f, height = 10f), Shape.Circle(2f), bx = 6.4f, by = 6.4f))
        // Touching the corner exactly: distance (3, 4) from the corner, radius 5.
        assertEquals(false, overlap(Shape.Box(width = 10f, height = 10f), Shape.Circle(5f), bx = 8f, by = 9f))
        // Containment either way.
        assertTrue(overlap(Shape.Box(width = 10f, height = 10f), Shape.Circle(1f), bx = 1f, by = 1f))
        assertTrue(overlap(Shape.Box(width = 2f, height = 2f), Shape.Circle(50f), bx = 3f, by = 3f))
    }

    @Test
    fun narrowPhaseFunctions() {
        assertTrue(circlesOverlap(0f, 0f, 1f, 1.5f, 0f, 1f))
        assertEquals(false, circlesOverlap(0f, 0f, 1f, 2f, 0f, 1f))
        assertTrue(boxesOverlap(0f, 0f, 2f, 2f, 1f, 1f, 3f, 3f))
        assertEquals(false, boxesOverlap(0f, 0f, 2f, 2f, 2f, 0f, 4f, 2f))
        assertTrue(circleOverlapsBox(1f, 1f, 0.1f, 0f, 0f, 2f, 2f))
        assertEquals(false, circleOverlapsBox(3f, 1f, 1f, 0f, 0f, 2f, 2f))
    }

    @Test
    fun scaleMultipliesShapeAndOffset() {
        val world = World()
        // Radius 5 scaled by 2 reaches x = 10; the circle at 19 with radius 10 starts at 9.
        world.spawn(Shape.Circle(5f), 0f, 0f, scaleX = 2f, scaleY = 0.1f)
        world.spawn(Shape.Circle(10f), 19f, 0f)
        assertEquals(1, detect(world).size)

        val boxes = World()
        // Box 10x10 scaled by (3, 1): x from -15 to 15, y from -5 to 5.
        boxes.spawn(Shape.Box(width = 10f, height = 10f), 0f, 0f, scaleX = 3f)
        boxes.spawn(Shape.Box(width = 2f, height = 2f), 15.5f, 0f)
        boxes.spawn(Shape.Box(width = 2f, height = 2f), 0f, 6.5f)
        assertEquals(1, detect(boxes).size)

        val offsets = World()
        // Offset (5, 0) scaled by -2 puts the center at x = -10; a negative scale keeps the radius positive.
        val probe = offsets.spawn(Shape.Circle(1f), 0f, 0f, offsetX = 5f, scaleX = -2f)
        val target = offsets.spawn(Shape.Circle(1f), -12f, 0f)
        offsets.spawn(Shape.Circle(1f), 10f, 0f)
        assertEquals(listOf(Overlap(probe, target)), detect(offsets))
    }

    @Test
    fun unscaledColliderIgnoresScale() {
        // The same circle of radius 5 at x = 0, scaled by 2, against a probe at x = 14 reaching
        // back to x = 9: scaled it reaches x = 10 and overlaps, unscaled it reaches x = 5 and not.
        val scaled = World()
        scaled.spawn(Shape.Circle(5f), 0f, 0f, scaleX = 2f, scaleY = 2f)
        scaled.spawn(Shape.Circle(5f), 14f, 0f)
        assertEquals(1, detect(scaled).size)

        val unscaled = World()
        unscaled.spawn(Shape.Circle(5f), 0f, 0f, scaleX = 2f, scaleY = 2f, scalesWithTransform = false)
        unscaled.spawn(Shape.Circle(5f), 14f, 0f)
        assertEquals(emptyList(), detect(unscaled))

        // A box squashed to nothing keeps its full size, and its offset is not scaled or mirrored:
        // the 10x10 box offset by (5, 0) stays from x = 0 to 10.
        val squashed = World()
        val box = squashed.spawn(
            Shape.Box(width = 10f, height = 10f), 0f, 0f, offsetX = 5f, scaleX = -0.01f, scaleY = 0.01f, scalesWithTransform = false,
        )
        val right = squashed.spawn(Shape.Box(width = 2f, height = 2f), 9.5f, 4.5f)
        squashed.spawn(Shape.Box(width = 2f, height = 2f), -1.5f, 0f)
        assertEquals(listOf(Overlap(box, right)), detect(squashed))
    }

    @Test
    fun unscaledColliderInWorldTransformIgnoresParentScale() {
        val world = World()
        val ship = world.create()
        world.add(ship, Transform, Transform(x = 100f, y = 0f, scaleX = 3f, scaleY = 3f))
        val shield = world.spawn(Shape.Circle(4f), 0f, 0f, scalesWithTransform = false)
        world.setParent(shield, ship)
        world.propagateTransforms()
        // Scaled by the parent the radius would be 12 and reach x = 112; unscaled it reaches 104.
        world.spawn(Shape.Circle(1f), 106f, 0f)
        assertEquals(emptyList(), detect(world))
        world.get(shield, Collider).scalesWithTransform = true
        assertEquals(1, detect(world).size)
    }

    @Test
    fun queriesAgreeWithDetectionOnUnscaledColliders() {
        val world = World()
        val ring = world.spawn(Shape.Circle(10f), 0f, 0f, scaleX = 0.1f, scalesWithTransform = false)
        val hero = world.spawn(Shape.Circle(2f), 11f, 0f)
        val collisions = Collisions()
        assertEquals(listOf(Overlap(ring, hero)), detect(world, collisions))
        // Scaled, the ring's radius would be 1; unscaled it is 10 in every query.
        assertEquals(listOf(ring), collisions.overlapping(Shape.Circle(2f), 0f, 9f))
        assertEquals(ring, collisions.firstOverlappingOrNull(Shape.Box(width = 2f, height = 2f), -9.5f, 0f))
        assertEquals(listOf(ring), collisions.overlappingTo(ArrayList(), Shape.Circle(1f), 0f, -9.5f))

        world.get(ring, Collider).scalesWithTransform = true
        assertEquals(emptyList(), detect(world, collisions))
        assertEquals(emptyList(), collisions.overlapping(Shape.Circle(2f), 0f, 9f))
        assertNull(collisions.firstOverlappingOrNull(Shape.Box(width = 2f, height = 2f), -9.5f, 0f))
        assertEquals(emptyList(), collisions.overlappingTo(ArrayList(), Shape.Circle(1f), 0f, -9.5f))
    }

    @Test
    fun parentedColliderUsesWorldTransform() {
        val world = World()
        val ship = world.create()
        world.add(ship, Transform, Transform(x = 100f, y = 0f, scaleX = 2f, scaleY = 2f))
        val gun = world.spawn(Shape.Circle(1f), 10f, 0f)
        world.setParent(gun, ship)
        val target = world.spawn(Shape.Circle(1f), 120f, 0f)
        // Before propagation the gun has no WorldTransform and sits at its local position.
        assertEquals(emptyList(), detect(world))
        world.propagateTransforms()
        assertEquals(listOf(Overlap(gun, target)), detect(world))
    }

    @Test
    fun masks() {
        val player = CollisionLayers.of(0)
        val enemy = CollisionLayers.of(1)
        val bullet = CollisionLayers.of(2)

        // One-sided: the bullet hits enemies, the enemy hits nothing, the pair is still reported.
        val oneSided = World()
        oneSided.spawn(Shape.Circle(5f), 0f, 0f, layers = enemy, mask = CollisionLayers.NONE)
        oneSided.spawn(Shape.Circle(5f), 1f, 0f, layers = bullet, mask = enemy)
        assertEquals(1, detect(oneSided).size)

        // Neither mask contains the other's layer.
        val none = World()
        none.spawn(Shape.Circle(5f), 0f, 0f, layers = player, mask = enemy)
        none.spawn(Shape.Circle(5f), 1f, 0f, layers = bullet, mask = enemy)
        assertEquals(0, detect(none).size)

        // Two-sided.
        val both = World()
        both.spawn(Shape.Circle(5f), 0f, 0f, layers = player, mask = enemy)
        both.spawn(Shape.Circle(5f), 1f, 0f, layers = enemy, mask = player + bullet)
        assertEquals(1, detect(both).size)

        // A collider on no layer is never hit.
        val ghost = World()
        ghost.spawn(Shape.Circle(5f), 0f, 0f, layers = CollisionLayers.NONE, mask = CollisionLayers.NONE)
        ghost.spawn(Shape.Circle(5f), 1f, 0f)
        assertEquals(0, detect(ghost).size)
    }

    @Test
    fun colliderSpanningSeveralCellsReportsEachPairOnce() {
        val world = World()
        val wall = world.spawn(Shape.Box(width = 200f, height = 200f), 0f, 0f)
        val small = world.spawn(Shape.Box(width = 30f, height = 30f), 50f, 50f)
        val crossing = world.spawn(Shape.Circle(20f), -64f, 0f)
        for (cellSize in listOf(8f, 16f, 64f, 1000f)) {
            val overlaps = detect(world, Collisions(cellSize))
            assertEquals(setOf(Overlap(wall, small), Overlap(wall, crossing)), overlaps.toSet(), "cell size $cellSize")
            assertEquals(2, overlaps.size, "cell size $cellSize")
        }
    }

    @Test
    fun hugeColliderLeavesTheGridButStillOverlaps() {
        val world = World()
        val zone = world.spawn(Shape.Box(width = 10_000f, height = 10_000f), 0f, 0f)
        val zone2 = world.spawn(Shape.Box(width = 10_000f, height = 10f), 0f, 0f)
        val inside = world.spawn(Shape.Circle(4f), 300f, 300f)
        world.spawn(Shape.Circle(4f), 6000f, 0f)
        val collisions = Collisions(4f)
        val overlaps = detect(world, collisions).toSet()
        assertEquals(setOf(Overlap(zone, zone2), Overlap(zone, inside)), overlaps)
        assertEquals(listOf(zone, inside), collisions.overlapping(Shape.Circle(1f), x = 300f, y = 300f))
    }

    @Test
    fun pairPutsSmallerIndexFirst() {
        val world = World()
        val first = world.spawn(Shape.Circle(5f), 0f, 0f)
        val second = world.spawn(Shape.Circle(5f), 1f, 0f)
        // Re-adding the first collider moves it to the end of the store.
        world.remove(first, Collider)
        world.add(first, Collider, Collider(Shape.Circle(5f)))
        assertEquals(listOf(Overlap(first, second)), detect(world))
    }

    @Test
    fun entitiesWithoutPositionAreSkipped() {
        val world = World()
        val bare = world.create()
        world.add(bare, Collider, Collider(Shape.Circle(100f)))
        val left = world.spawn(Shape.Circle(5f), 0f, 0f)
        val right = world.spawn(Shape.Circle(5f), 200f, 0f)
        world.spawn(Shape.Circle(5f), Float.NaN, 0f)
        val collisions = Collisions()
        assertEquals(0, detect(world, collisions).size)
        assertEquals(2, collisions.colliderCount)
        assertEquals(listOf(left, right), collisions.overlapping(Shape.Circle(1000f), x = 0f, y = 0f))
    }

    @Test
    fun queriesReadTheLastDetection() {
        val world = World()
        val collisions = Collisions(16f)
        val a = world.spawn(Shape.Circle(5f), 0f, 0f)
        assertEquals(emptyList(), collisions.overlapping(Shape.Circle(5f), x = 0f, y = 0f))
        assertNull(collisions.firstOverlappingOrNull(Shape.Circle(5f), x = 0f, y = 0f))

        detect(world, collisions)
        assertEquals(listOf(a), collisions.overlapping(Shape.Circle(5f), x = 0f, y = 0f))

        // Moving the entity does not move it in the snapshot until the next detection.
        world.get(a, Transform).x = 100f
        val b = world.spawn(Shape.Circle(5f), 0f, 0f)
        assertEquals(listOf(a), collisions.overlapping(Shape.Box(width = 2f, height = 2f), x = 0f, y = 0f))
        assertEquals(emptyList(), collisions.overlapping(Shape.Box(width = 2f, height = 2f), x = 100f, y = 0f))

        detect(world, collisions)
        assertEquals(listOf(b), collisions.overlapping(Shape.Box(width = 2f, height = 2f), x = 0f, y = 0f))
        assertEquals(listOf(a), collisions.overlapping(Shape.Box(width = 2f, height = 2f), x = 100f, y = 0f))
    }

    @Test
    fun queryShapesAndMasks() {
        val world = World()
        val collisions = Collisions(8f)
        val enemy = CollisionLayers.of(1)
        val box = world.spawn(Shape.Box(width = 20f, height = 20f), 0f, 0f, layers = enemy)
        val circle = world.spawn(Shape.Circle(10f), 30f, 0f)
        detect(world, collisions)

        // Touching the box edge at x = 10 is not an overlap.
        // The box's edge at x = 10 and the circle's leftmost point at x = 20 only touch.
        assertEquals(emptyList(), collisions.overlapping(Shape.Box(width = 10f, height = 10f), x = 15f, y = 0f))
        assertEquals(listOf(box, circle), collisions.overlapping(Shape.Box(width = 12f, height = 2f), x = 15f, y = 0f))
        assertEquals(listOf(box), collisions.overlapping(Shape.Box(width = 12f, height = 2f), x = 15f, y = 0f, mask = enemy))
        assertEquals(listOf(circle), collisions.overlapping(Shape.Circle(1f), x = 30f, y = 9f))
        assertEquals(emptyList(), collisions.overlapping(Shape.Circle(1f), x = 37.8f, y = 7.8f))
        // A query larger than the grid tests every collider.
        assertEquals(listOf(box, circle), collisions.overlapping(Shape.Box(width = 10_000f, height = 10_000f), x = 0f, y = 0f))

        val destination = mutableListOf(circle)
        assertEquals(listOf(circle, box), collisions.overlappingTo(destination, Shape.Circle(2f), x = 0f, y = 0f))
        val set = collisions.overlappingTo(HashSet<Entity>(), Shape.Box(width = 100f, height = 100f), x = 15f, y = 0f)
        assertEquals(setOf(box, circle), set)

        assertFailsWith<IllegalArgumentException> { collisions.overlapping(Shape.Circle(1f), x = Float.NaN, y = 0f) }
    }

    @Test
    fun firstOverlapOrNullReturnsSmallestIndex() {
        val world = World()
        val collisions = Collisions()
        val first = world.spawn(Shape.Circle(5f), 0f, 0f)
        val second = world.spawn(Shape.Circle(5f), 2f, 0f)
        world.remove(first, Collider)
        world.add(first, Collider, Collider(Shape.Circle(5f)))
        detect(world, collisions)
        assertEquals(first, collisions.firstOverlappingOrNull(Shape.Circle(1f), x = 1f, y = 0f))
        assertEquals(second, collisions.firstOverlappingOrNull(Shape.Circle(1f), x = 6f, y = 0f))
        assertNull(collisions.firstOverlappingOrNull(Shape.Circle(1f), x = 50f, y = 0f))
        assertNull(collisions.firstOverlappingOrNull(Shape.Circle(1f), x = 1f, y = 0f, mask = CollisionLayers.of(3)))
    }

    @Test
    fun cellSizeMustBePositive() {
        assertEquals("Cell size 0.0 is not a positive finite number.", assertFailsWith<IllegalArgumentException> { Collisions(0f) }.message)
        val collisions = Collisions()
        assertFailsWith<IllegalArgumentException> { collisions.cellSize = -1f }
        assertFailsWith<IllegalArgumentException> { collisions.cellSize = Float.POSITIVE_INFINITY }
        collisions.cellSize = 32f
        assertEquals(32f, collisions.cellSize)
    }

    @Test
    fun toStringNamesTheLastDetection() {
        val world = World()
        val collisions = Collisions(10f)
        world.spawn(Shape.Box(width = 4f, height = 4f), 5f, 5f)
        world.spawn(Shape.Box(width = 4f, height = 4f), 6f, 6f)
        detect(world, collisions)
        assertEquals("Collisions(cellSize=10.0, colliders=2, cells=1, pairs=1)", collisions.toString())
    }

    @Test
    fun detectingAgainReplacesTheSnapshot() {
        val world = World()
        val collisions = Collisions(16f)
        val entities = List(500) { world.spawn(Shape.Circle(3f), (it % 50) * 5f, (it / 50) * 5f) }
        val crowded = detect(world, collisions).size
        assertTrue(crowded > 0)
        for (entity in entities) world.get(entity, Transform).x += (entity.index * 100).toFloat()
        assertEquals(0, detect(world, collisions).size)
        for (entity in entities.drop(10)) world.destroy(entity)
        assertEquals(0, detect(world, collisions).size)
        assertEquals(10, collisions.colliderCount)
    }

    @Test
    fun gridMatchesBruteForceOnRandomScenes() {
        for (seed in 1..3) {
            val random = Random(seed)
            val world = World()
            val shapes = ArrayList<TestShape>()
            repeat(2000) {
                val x = random.nextFloat() * 1000f - 500f
                val y = random.nextFloat() * 1000f - 500f
                val scale = if (random.nextInt(4) == 0) 0.5f + random.nextFloat() * 1.5f else 1f
                val shape: Shape = if (random.nextBoolean()) {
                    Shape.Circle(2f + random.nextFloat() * 18f)
                } else {
                    Shape.Box(width = 4f + random.nextFloat() * 36f, height = 4f + random.nextFloat() * 36f)
                }
                val layers = CollisionLayers.of(random.nextInt(4))
                val mask = CollisionLayers(random.nextInt(16))
                shapes.add(TestShape(world.spawn(shape, x, y, layers, mask, scaleX = scale, scaleY = scale), shape, x, y, scale, layers, mask))
            }
            // A few colliders large enough to leave the grid at the smallest cell size.
            repeat(4) {
                val x = random.nextFloat() * 1000f - 500f
                val y = random.nextFloat() * 1000f - 500f
                val shape = Shape.Box(width = 150f, height = 120f)
                shapes.add(TestShape(world.spawn(shape, x, y), shape, x, y, 1f, CollisionLayers.DEFAULT, CollisionLayers.ALL))
            }
            checkAgainstBruteForce(world, shapes, "seed $seed")
        }
    }

    @Test
    fun gridMatchesBruteForceOnCellAlignedScenes() {
        // Positions and sizes on a coarse lattice put many edges exactly on cell borders and make
        // many pairs touch without overlapping.
        val random = Random(7)
        val world = World()
        val shapes = ArrayList<TestShape>()
        repeat(2000) {
            val x = random.nextInt(-40, 40) * 8f
            val y = random.nextInt(-40, 40) * 8f
            val shape: Shape = if (random.nextBoolean()) {
                Shape.Circle(random.nextInt(1, 4) * 4f)
            } else {
                Shape.Box(width = random.nextInt(1, 5) * 8f, height = random.nextInt(1, 5) * 8f)
            }
            shapes.add(TestShape(world.spawn(shape, x, y), shape, x, y, 1f, CollisionLayers.DEFAULT, CollisionLayers.ALL))
        }
        checkAgainstBruteForce(world, shapes, "lattice")
    }

    private class TestShape(
        val entity: Entity,
        val shape: Shape,
        val x: Float,
        val y: Float,
        val scale: Float,
        val layers: CollisionLayers,
        val mask: CollisionLayers,
    ) {
        val circle = shape is Shape.Circle
        val halfWidth = when (shape) {
            is Shape.Circle -> shape.radius * abs(scale)
            is Shape.Box -> shape.width * abs(scale) * 0.5f
        }
        val halfHeight = when (shape) {
            is Shape.Circle -> shape.radius * abs(scale)
            is Shape.Box -> shape.height * abs(scale) * 0.5f
        }

        fun overlaps(other: TestShape): Boolean {
            if (!mask.intersects(other.layers) && !other.mask.intersects(layers)) return false
            if (!boxesOverlap(
                    x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight,
                    other.x - other.halfWidth, other.y - other.halfHeight, other.x + other.halfWidth, other.y + other.halfHeight,
                )
            ) {
                return false
            }
            return when {
                circle && other.circle -> circlesOverlap(x, y, halfWidth, other.x, other.y, other.halfWidth)
                circle -> circleOverlapsBox(x, y, halfWidth, other.x - other.halfWidth, other.y - other.halfHeight, other.x + other.halfWidth, other.y + other.halfHeight)
                other.circle -> circleOverlapsBox(other.x, other.y, other.halfWidth, x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight)
                else -> true
            }
        }
    }

    private fun checkAgainstBruteForce(world: World, shapes: List<TestShape>, label: String) {
        val expected = HashSet<Overlap>()
        for (i in shapes.indices) {
            for (j in i + 1 until shapes.size) {
                if (shapes[i].overlaps(shapes[j])) {
                    val a = shapes[i].entity
                    val b = shapes[j].entity
                    expected.add(if (a.index < b.index) Overlap(a, b) else Overlap(b, a))
                }
            }
        }
        assertTrue(expected.size > 100, "$label: too few pairs (${expected.size}) to be a useful test")
        for (cellSize in listOf(3f, 8f, 16f, 64f, 250f)) {
            val collisions = Collisions(cellSize)
            val overlaps = detect(world, collisions)
            assertEquals(overlaps.size, overlaps.toSet().size, "$label, cell size $cellSize: a pair was reported twice")
            assertEquals(expected, overlaps.toSet(), "$label, cell size $cellSize")
            for (overlap in overlaps) assertTrue(overlap.a.index < overlap.b.index)

            // Queries agree with brute force too, for a sample of probes.
            val random = Random(cellSize.toInt())
            repeat(50) {
                val probe = TestShape(
                    Entity(-1L),
                    if (random.nextBoolean()) Shape.Circle(1f + random.nextFloat() * 30f) else Shape.Box(width = 2f + random.nextFloat() * 60f, height = 2f + random.nextFloat() * 60f),
                    random.nextFloat() * 1000f - 500f,
                    random.nextFloat() * 1000f - 500f,
                    1f,
                    CollisionLayers.NONE,
                    CollisionLayers.of(random.nextInt(4)) + CollisionLayers.of(random.nextInt(4)),
                )
                val bruteForce = shapes.filter { probe.mask.intersects(it.layers) && probe.overlapsIgnoringLayers(it) }.map { it.entity }.sortedBy { it.index }
                assertEquals(bruteForce, collisions.overlapping(probe.shape, x = probe.x, y = probe.y, mask = probe.mask), "$label, cell size $cellSize, query")
                assertEquals(bruteForce.firstOrNull(), collisions.firstOverlappingOrNull(probe.shape, x = probe.x, y = probe.y, mask = probe.mask))
            }
        }
    }

    private fun TestShape.overlapsIgnoringLayers(other: TestShape): Boolean =
        TestShape(entity, shape, x, y, scale, CollisionLayers.ALL, CollisionLayers.ALL)
            .overlaps(TestShape(other.entity, other.shape, other.x, other.y, other.scale, CollisionLayers.ALL, CollisionLayers.ALL))
}
