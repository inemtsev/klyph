package klyph.collision

import klyph.entity.Entity
import klyph.entity.Transform
import klyph.entity.World
import klyph.event.Events
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlideTest {
    private val step = 1f / 60f
    private val gap = Collisions.CONTACT_GAP

    private fun World.solid(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        passable: Passable = Passable.NEVER,
        layers: CollisionLayers = CollisionLayers.DEFAULT,
    ): Entity {
        val entity = create()
        add(entity, Transform, Transform(x = (left + right) / 2, y = (top + bottom) / 2))
        add(entity, Collider, Collider(Shape.Box(width = right - left, height = bottom - top), layers = layers))
        add(entity, Solid, Solid(passable))
        return entity
    }

    /**
     * A level that a hero 20 by 20 running right at 200 from (0, 90) meets in two seconds: a
     * ceiling over the start to bump, a one-way ledge to jump up through and land on, a drop to a
     * lower roof, a step down of one unit that only the floor snap keeps him on, and a wall.
     */
    private fun World.course() {
        solid(left = -50f, top = -20f, right = 60f, bottom = 50f)
        solid(left = -50f, top = 85f, right = 40f, bottom = 89f, passable = Passable.FROM_BELOW)
        solid(left = -50f, top = 100f, right = 120f, bottom = 200f)
        solid(left = 120f, top = 140f, right = 260f, bottom = 200f)
        solid(left = 260f, top = 141f, right = 600f, bottom = 200f)
        solid(left = 380f, top = 0f, right = 420f, bottom = 141f)
    }

    /** Spawns a body whose box is [width] by [height], [offsetY] below its Transform, scaled by [scale]. */
    private fun World.body(x: Float, y: Float, width: Float, height: Float, offsetY: Float = 0f, scale: Float = 1f): Entity {
        val entity = create()
        add(entity, Transform, Transform(x = x, y = y, scaleX = scale, scaleY = scale))
        add(entity, Collider, Collider(Shape.Box(width = width, height = height), offsetY = offsetY))
        add(entity, KinematicBody, KinematicBody())
        return entity
    }

    @Test
    fun slideBoxMovesExactlyAsTheBodyThroughAJumpArc() {
        val world = World()
        world.course()
        val hero = world.body(x = 0f, y = 90f - gap, width = 20f, height = 20f)
        val transform = world.get(hero, Transform)
        val body = world.get(hero, KinematicBody)
        val collisions = Collisions()
        collisions.moveBodies(world, step, scenes = null)

        // The plan starts where the body is and follows the same speeds, zeroing them where the body would.
        val slide = BoxSlide()
        var x = transform.x
        var y = transform.y
        var speedX = 0f
        var speedY = 0f
        var isPlanOnFloor = false
        var ceilings = 0
        var walls = 0
        var landings = 0
        for (n in 0 until 120) {
            val jump = n == 0
            body.dx = 200f
            if (jump) body.dy = -500f
            body.dy += 1800f * step
            speedX = 200f
            if (jump) speedY = -500f
            speedY += 1800f * step
            val snap = if (isPlanOnFloor && speedY >= 0f) Collisions.FLOOR_SNAP_DISTANCE else 0f

            collisions.moveBodies(world, step, scenes = null)
            collisions.slideBox(x = x, y = y, width = 20f, height = 20f, dx = speedX * step, dy = speedY * step, floorSnap = snap, result = slide)
            if (slide.isOnWall) speedX = 0f
            if (slide.isOnFloor || slide.isOnCeiling) speedY = 0f
            x = slide.x
            y = slide.y
            isPlanOnFloor = slide.isOnFloor

            assertEquals(transform.x, x, "Step $n: x.")
            assertEquals(transform.y, y, "Step $n: y.")
            assertEquals(body.isOnFloor, slide.isOnFloor, "Step $n: floor.")
            assertEquals(body.isOnWall, slide.isOnWall, "Step $n: wall.")
            assertEquals(body.isOnCeiling, slide.isOnCeiling, "Step $n: ceiling.")
            assertEquals(body.wallNormalX, slide.wallNormalX, "Step $n: wall normal.")
            assertEquals(body.floorOrNull, slide.floorOrNull, "Step $n: floor entity.")
            assertEquals(body.dx, speedX, "Step $n: dx.")
            assertEquals(body.dy, speedY, "Step $n: dy.")
            if (slide.isOnCeiling) ceilings++
            if (slide.isOnWall) walls++
            if (body.hasLanded) landings++
        }
        // The arc met everything the course holds: the ceiling, the ledge, the roofs and the wall.
        assertTrue(ceilings > 0)
        assertTrue(walls > 0)
        assertTrue(landings >= 3, "Only $landings landings.")
        assertEquals(380f - gap, transform.x + 10f, 0.0001f)
        assertEquals(141f - gap, transform.y + 10f, 0.0001f)
    }

    @Test
    fun slideColliderMovesExactlyAsTheBodyWithAnOffsetAndAScale() {
        val world = World()
        world.course()
        // A box of 10 by 10 scaled to 20 by 20, 3 units below the Transform, also scaled.
        val hero = world.body(x = 0f, y = 90f - gap - 6f, width = 10f, height = 10f, offsetY = 3f, scale = 2f)
        val transform = world.get(hero, Transform)
        val body = world.get(hero, KinematicBody)
        val collisions = Collisions()
        collisions.moveBodies(world, step, scenes = null)

        val slide = BoxSlide()
        var x = transform.x
        var y = transform.y
        var speedX: Float
        var speedY = 0f
        var isPlanOnFloor = false
        for (n in 0 until 120) {
            body.dx = 200f
            if (n == 0) body.dy = -500f
            body.dy += 1800f * step
            speedX = 200f
            if (n == 0) speedY = -500f
            speedY += 1800f * step
            val snap = if (isPlanOnFloor && speedY >= 0f) Collisions.FLOOR_SNAP_DISTANCE else 0f

            collisions.slideCollider(hero, x = x, y = y, dx = speedX * step, dy = speedY * step, floorSnap = snap, result = slide)
            collisions.moveBodies(world, step, scenes = null)
            if (slide.isOnFloor || slide.isOnCeiling) speedY = 0f
            x = slide.x
            y = slide.y
            isPlanOnFloor = slide.isOnFloor
            assertEquals(transform.x, x, "Step $n: x.")
            assertEquals(transform.y, y, "Step $n: y.")
            assertEquals(body.isOnFloor, slide.isOnFloor, "Step $n: floor.")
        }
        assertEquals(380f - gap, transform.x + 10f, 0.0001f)
    }

    @Test
    fun slideBoxReportsWhatItTouchedAndHowFarItMoved() {
        val world = World()
        val floor = world.solid(left = 0f, top = 100f, right = 200f, bottom = 140f)
        world.solid(left = 150f, top = 0f, right = 160f, bottom = 100f)
        val collisions = Collisions()
        collisions.detect(world, Events())

        val slide = collisions.slideBox(x = 100f, y = 50f, width = 20f, height = 20f, dx = 100f, dy = 100f)
        assertEquals(150f - gap - 10f, slide.x, 0.0001f)
        assertEquals(slide.x - 100f, slide.movedX, 0.0001f)
        assertEquals(100f - gap - 10f, slide.y, 0.0001f)
        assertEquals(slide.y - 50f, slide.movedY, 0.0001f)
        assertTrue(slide.isOnWall)
        assertEquals(-1f, slide.wallNormalX)
        assertTrue(slide.isOnFloor)
        assertEquals(floor, slide.floorOrNull)
        assertFalse(slide.isOnCeiling)

        // Reused: every property is that of the last slide.
        val up = collisions.slideBox(x = 50f, y = 200f, width = 20f, height = 20f, dx = 0f, dy = -60f, result = slide)
        assertTrue(up === slide)
        assertTrue(slide.isOnCeiling)
        assertFalse(slide.isOnFloor)
        assertFalse(slide.isOnWall)
        assertNull(slide.floorOrNull)
        assertEquals(140f + gap + 10f, slide.y, 0.0001f)
        assertEquals(
            "BoxSlide(x=50.0, y=${slide.y}, movedX=0.0, movedY=${slide.movedY}, isOnFloor=false, isOnWall=false, " +
                "wallNormalX=0.0, isOnCeiling=true)",
            slide.toString(),
        )
    }

    @Test
    fun slideBoxSnapsToAFloorOnlyWhenAskedAndNotMovingUp() {
        val world = World()
        val floor = world.solid(left = 0f, top = 101f, right = 200f, bottom = 140f)
        val collisions = Collisions()
        collisions.detect(world, Events())
        // Feet a unit above the floor, moving across only.
        val plain = collisions.slideBox(x = 50f, y = 90f, width = 20f, height = 20f, dx = 10f, dy = 0f)
        assertFalse(plain.isOnFloor)
        assertEquals(90f, plain.y)
        val snapped = collisions.slideBox(x = 50f, y = 90f, width = 20f, height = 20f, dx = 10f, dy = 0f, floorSnap = Collisions.FLOOR_SNAP_DISTANCE)
        assertTrue(snapped.isOnFloor)
        assertEquals(floor, snapped.floorOrNull)
        assertEquals(101f - gap - 10f, snapped.y, 0.0001f)
        val rising = collisions.slideBox(x = 50f, y = 90f, width = 20f, height = 20f, dx = 0f, dy = -1f, floorSnap = Collisions.FLOOR_SNAP_DISTANCE)
        assertFalse(rising.isOnFloor)
    }

    @Test
    fun slideBoxFollowsTheOneWayRuleAndTheMask() {
        val world = World()
        val water = CollisionLayers.of(3)
        world.solid(left = 0f, top = 100f, right = 200f, bottom = 110f, passable = Passable.FROM_BELOW)
        world.solid(left = 0f, top = 300f, right = 200f, bottom = 340f, layers = water)
        val collisions = Collisions()
        collisions.detect(world, Events())
        // Up through the ledge, then down onto it.
        assertFalse(collisions.slideBox(x = 50f, y = 120f, width = 20f, height = 20f, dx = 0f, dy = -60f).isOnCeiling)
        assertTrue(collisions.slideBox(x = 50f, y = 60f, width = 20f, height = 20f, dx = 0f, dy = 60f).isOnFloor)
        // The water is left out by the mask.
        val sink = collisions.slideBox(x = 50f, y = 200f, width = 20f, height = 20f, dx = 0f, dy = 200f, mask = CollisionLayers.DEFAULT)
        assertFalse(sink.isOnFloor)
        assertEquals(400f, sink.y)
    }

    @Test
    fun invalidSlidesAreRejected() {
        val world = World()
        val collisions = Collisions()
        collisions.detect(world, Events())
        assertFailsWith<IllegalArgumentException> { collisions.slideBox(x = 0f, y = 0f, width = 0f, height = 20f, dx = 1f, dy = 0f) }
        assertFailsWith<IllegalArgumentException> { collisions.slideBox(x = 0f, y = 0f, width = 20f, height = 20f, dx = Float.NaN, dy = 0f) }
        val snap = assertFailsWith<IllegalArgumentException> {
            collisions.slideBox(x = 0f, y = 0f, width = 20f, height = 20f, dx = 1f, dy = 0f, floorSnap = -1f)
        }
        assertEquals("Floor snap -1.0 is negative or not finite.", snap.message)
        val hero = world.body(x = 0f, y = 0f, width = 20f, height = 20f)
        assertFailsWith<IllegalArgumentException> { collisions.slideCollider(hero, x = Float.POSITIVE_INFINITY, y = 0f, dx = 0f, dy = 1f) }
    }

    @Test
    fun sweepColliderSweepsTheEntitysOwnBox() {
        val world = World()
        val wall = world.solid(left = 100f, top = -100f, right = 110f, bottom = 100f)
        val floor = world.solid(left = -100f, top = 60f, right = 300f, bottom = 80f, layers = CollisionLayers.of(3))
        val hero = world.create()
        val transform = Transform(x = 0f, y = 0f, scaleX = 2f, scaleY = 2f)
        world.add(hero, Transform, transform)
        // 10 by 10 scaled to 20 by 20, its center 10 to the right of the Transform after the scale.
        val collider = Collider(Shape.Box(width = 10f, height = 10f), offsetX = 5f, mask = CollisionLayers.DEFAULT)
        world.add(hero, Collider, collider)
        val collisions = Collisions()
        collisions.detect(world, Events())

        // The box spans 0 to 20 and reaches the wall after 80 of 100.
        assertEquals(SweepHit(wall, 0.8f, -1f, 0f), collisions.sweepCollider(hero, dx = 100f, dy = 0f))
        // The floor is on a layer outside the mask.
        assertNull(collisions.sweepCollider(hero, dx = 0f, dy = 100f))
        collider.mask = CollisionLayers.ALL
        assertEquals(floor, collisions.sweepCollider(hero, dx = 0f, dy = 100f)?.entity)

        // Unscaled, the box spans 0 to 10 around 5 and reaches the wall after 90.
        collider.scalesWithTransform = false
        assertEquals(0.9f, assertNotNull(collisions.sweepCollider(hero, dx = 100f, dy = 0f)).fraction, 0.00001f)
    }

    @Test
    fun sweepColliderRejectsEntitiesItCannotPlace() {
        val loose = World()
        val nobody = loose.create()
        val unattached = assertFailsWith<IllegalStateException> { Collisions().sweepCollider(nobody, dx = 1f, dy = 0f) }
        assertEquals("Collisions has met no world yet; call detect or moveBodies first.", unattached.message)

        val world = World()
        val collisions = Collisions()
        collisions.detect(world, Events())
        val bare = world.create()
        assertEquals(
            "Entity $bare has no Transform.",
            assertFailsWith<IllegalArgumentException> { collisions.sweepCollider(bare, dx = 1f, dy = 0f) }.message,
        )
        world.add(bare, Transform, Transform())
        assertEquals(
            "Entity $bare has no Collider.",
            assertFailsWith<IllegalArgumentException> { collisions.sweepCollider(bare, dx = 1f, dy = 0f) }.message,
        )
        world.add(bare, Collider, Collider(Shape.Circle(4f)))
        assertFailsWith<IllegalArgumentException> { collisions.sweepCollider(bare, dx = 1f, dy = 0f) }
        world.add(bare, Collider, Collider(Shape.Box(width = 4f, height = 4f)))
        world.add(bare, Solid, Solid())
        assertFailsWith<IllegalArgumentException> { collisions.sweepCollider(bare, dx = 1f, dy = 0f) }
        world.remove(bare, Solid)
        world.setParent(bare, world.create())
        assertFailsWith<IllegalArgumentException> { collisions.sweepCollider(bare, dx = 1f, dy = 0f) }
        world.destroy(bare)
        assertEquals(
            "Entity $bare is not alive.",
            assertFailsWith<IllegalArgumentException> { collisions.sweepCollider(bare, dx = 1f, dy = 0f) }.message,
        )
    }

    @Test
    fun floorBelowFindsTheTopUnderALine() {
        val world = World()
        val roof = world.solid(left = 0f, top = 100f, right = 100f, bottom = 200f)
        val collisions = Collisions()
        collisions.detect(world, Events())

        val hit = assertNotNull(collisions.floorBelowOrNull(x = 50f, y = 98f, width = 4f, depth = 8f))
        assertEquals(SweepHit(roof, 0.25f, 0f, -1f), hit)
        // A line exactly on the top meets it at once; a line past the edge or too high meets nothing.
        assertEquals(0f, assertNotNull(collisions.floorBelowOrNull(x = 50f, y = 100f, width = 4f, depth = 8f)).fraction)
        assertNull(collisions.floorBelowOrNull(x = 110f, y = 98f, width = 4f, depth = 8f))
        assertNull(collisions.floorBelowOrNull(x = 50f, y = 80f, width = 4f, depth = 8f))
        // Half the line over the roof is enough.
        assertEquals(roof, collisions.floorBelowOrNull(x = 101f, y = 98f, width = 4f, depth = 8f)?.entity)
    }

    @Test
    fun floorBelowPassesThroughASolidItStartsInside() {
        val world = World()
        world.solid(left = 0f, top = 0f, right = 100f, bottom = 50f)
        val below = world.solid(left = 0f, top = 60f, right = 100f, bottom = 80f)
        val collisions = Collisions()
        collisions.detect(world, Events())
        assertEquals(below, collisions.floorBelowOrNull(x = 50f, y = 20f, width = 4f, depth = 100f)?.entity)
    }

    @Test
    fun floorBelowFollowsTheOneWayRuleAndTheMask() {
        val world = World()
        val ledge = world.solid(left = 0f, top = 100f, right = 100f, bottom = 110f, passable = Passable.FROM_BELOW, layers = CollisionLayers.of(4))
        val water = world.solid(left = 0f, top = 200f, right = 100f, bottom = 240f, layers = CollisionLayers.of(3))
        val ground = world.solid(left = 0f, top = 300f, right = 100f, bottom = 340f)
        val collisions = Collisions()
        collisions.detect(world, Events())

        // From above, the ledge is a floor; from inside it or below its top, it is not.
        assertEquals(ledge, collisions.floorBelowOrNull(x = 50f, y = 96f, width = 4f, depth = 8f)?.entity)
        assertEquals(ledge, collisions.floorBelowOrNull(x = 50f, y = 100f, width = 4f, depth = 8f)?.entity)
        assertEquals(water, collisions.floorBelowOrNull(x = 50f, y = 104f, width = 4f, depth = 200f)?.entity)
        // The mask leaves the ledge and the water out.
        assertEquals(ground, collisions.floorBelowOrNull(x = 50f, y = 96f, width = 4f, depth = 400f, mask = CollisionLayers.DEFAULT)?.entity)
    }

    @Test
    fun invalidProbesAreRejected() {
        val collisions = Collisions()
        assertFailsWith<IllegalArgumentException> { collisions.floorBelowOrNull(x = 0f, y = Float.NaN, width = 4f, depth = 8f) }
        assertFailsWith<IllegalArgumentException> { collisions.floorBelowOrNull(x = 0f, y = 0f, width = 0f, depth = 8f) }
        val depth = assertFailsWith<IllegalArgumentException> { collisions.floorBelowOrNull(x = 0f, y = 0f, width = 4f, depth = 0f) }
        assertEquals("Probe depth 0.0 is not a positive finite number.", depth.message)
    }
}
