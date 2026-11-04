package klyph.math

import kotlin.math.PI

/**
 * Returns this value, taken as an angle in degrees, in radians, the unit every angle in klyph is
 * given in: `90f.degrees` is a quarter turn, `PI / 2`. [toDegrees] is its inverse.
 *
 * @sample samples.math.Angles.degrees
 */
public val Float.degrees: Float
    get() = (this * (PI / 180.0)).toFloat()

/**
 * Returns this angle in radians converted to degrees: `PI.toFloat().toDegrees()` is 180. Use it to
 * show an angle to a person; klyph itself takes radians everywhere.
 *
 * @sample samples.math.Angles.degrees
 */
public fun Float.toDegrees(): Float = (this * (180.0 / PI)).toFloat()
