package klyph.math

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class AnglesTest {
    @Test
    fun degreesConvertToRadiansAndBack() {
        assertEquals((PI / 2).toFloat(), 90f.degrees)
        assertEquals((2 * PI).toFloat(), 360f.degrees)
        assertEquals(-PI.toFloat(), (-180f).degrees)
        assertEquals(180f, PI.toFloat().toDegrees(), 1e-4f)
        assertEquals(45f, 45f.degrees.toDegrees(), 1e-4f)
        assertEquals(0f, 0f.degrees)
    }
}
