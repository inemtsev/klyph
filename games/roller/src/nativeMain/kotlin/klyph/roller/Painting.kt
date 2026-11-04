package klyph.roller

import klyph.platform.Color
import klyph.platform.Image
import klyph.platform.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// The art that is painted in code rather than generated: brick buildings, steel ledges and
// pylons, air conditioners, blocky clouds, the distant skyline, the sky gradient and the impact star. Each is
// painted once at startup into an Image, one texel at a time, and uploaded as a texture that is
// drawn with nearest sampling at Tuning.PIXEL points per texel (or more), so it looks like the
// generated pixel-art sprites. Every painting is seeded, so every run looks the same. Image has
// fill, fillCircle and set, so the rectangle helper below is the game's own.

/** Fills the rectangle of [width] by [height] texels whose top-left corner is ([x], [y]) with [color], clipped to the image. */
fun Image.fillRect(x: Int, y: Int, width: Int, height: Int, color: Color) {
    for (row in max(0, y) until min(this.height, y + height)) {
        for (column in max(0, x) until min(this.width, x + width)) set(column, row, color)
    }
}

/** The three brick colors and the mortar of one building's palette. */
private class BrickPalette(val bricks: List<Color>, val mortar: Color)

private val PALETTES = listOf(
    BrickPalette(listOf(Color(0xB4473CFFu), Color(0xA63F36FFu), Color(0xBE5245FFu)), Color(0x7A2C28FFu)),
    BrickPalette(listOf(Color(0x9C3B3BFFu), Color(0x8F3434FFu), Color(0xA84444FFu)), Color(0x652424FFu)),
    BrickPalette(listOf(Color(0xC0583FFFu), Color(0xB44F38FFu), Color(0xCA6448FFu)), Color(0x833A2AFFu)),
)

private val CAP_OUTLINE = Color(0x3B1E1EFFu)
private val CAP_LIGHT = Color(0xF0E2D2FFu)
private val CAP_MID = Color(0xD6C1AEFFu)
private val CAP_SHADE = Color(0xA08470FFu)
private val UNDER_CAP = Color(0x4E1F1CFFu)
private val WALL_EDGE = Color(0x5A2420FFu)
private val WINDOW_FRAME = Color(0xE9DDCBFFu)
private val WINDOW_SILL = Color(0xCDBBA6FFu)
private val GLASS = Color(0x2F3D73FFu)
private val GLASS_LIGHT = Color(0x6F84C8FFu)
private val GLASS_SHINE = Color(0xA9B8E8FFu)

/**
 * Paints the face of a brick building [width] by [height] texels: a stone cap along the roof,
 * staggered bricks in one of three palettes picked by [style], and one or two rows of windows.
 */
fun paintBuilding(width: Int, height: Int, style: Int): Image {
    val image = Image(width, height)
    val palette = PALETTES[style % PALETTES.size]
    val random = Random(style * 7919 + width)
    // Bricks are 8 by 4 texels with their mortar, every other row shifted by half a brick.
    val shades = IntArray((height / 4 + 1) * (width / 8 + 2)) { random.nextInt(palette.bricks.size) }
    for (y in 0 until height) {
        val row = y / 4
        val shift = if (row % 2 == 0) 0 else 4
        for (x in 0 until width) {
            val isMortar = y % 4 == 3 || (x + shift) % 8 == 7
            val brick = (x + shift) / 8
            image[x, y] = if (isMortar) palette.mortar else palette.bricks[shades[row * (width / 8 + 2) + brick]]
        }
    }
    // The walls' edges, so neighbouring buildings and the sky do not run into the bricks.
    image.fillRect(0, 0, 1, height, WALL_EDGE)
    image.fillRect(width - 1, 0, 1, height, WALL_EDGE)
    // The stone cap the hero rolls on.
    image.fillRect(0, 0, width, 1, CAP_OUTLINE)
    image.fillRect(0, 1, width, 2, CAP_LIGHT)
    image.fillRect(0, 3, width, 1, CAP_MID)
    image.fillRect(0, 4, width, 1, CAP_SHADE)
    image.fillRect(0, 5, width, 1, UNDER_CAP)
    for (x in 3 until width step 12) image.fillRect(x, 3, 1, 2, CAP_SHADE)
    paintWindowRow(image, top = 13)
    if (height > 80) paintWindowRow(image, top = 45)
    return image
}

/** Paints a row of two-pane windows across [image] with their tops at row [top]. */
private fun paintWindowRow(image: Image, top: Int) {
    val windowWidth = 10
    val windowHeight = 14
    val gap = 8
    val margin = 8
    val count = max(0, (image.width - 2 * margin + gap) / (windowWidth + gap))
    if (count == 0) return
    val used = count * windowWidth + (count - 1) * gap
    var x = (image.width - used) / 2
    repeat(count) {
        image.fillRect(x - 1, top + windowHeight - 1, windowWidth + 2, 2, WINDOW_SILL)
        image.fillRect(x, top, windowWidth, windowHeight - 1, WINDOW_FRAME)
        image.fillRect(x + 1, top + 1, windowWidth - 2, windowHeight - 3, GLASS)
        image.fillRect(x + 1, top + 1, windowWidth - 2, 3, GLASS_LIGHT)
        // A diagonal shine across the lower panes.
        for (k in 0 until 4) {
            image[x + 2 + k, top + 9 - k] = GLASS_SHINE
        }
        image.fillRect(x + windowWidth / 2 - 1, top + 1, 1, windowHeight - 3, WINDOW_FRAME)
        x += windowWidth + gap
    }
}

private val STEEL_OUTLINE = Color(0x262B38FFu)
private val STEEL_LIGHT = Color(0xC9D2E3FFu)
private val STEEL_MID = Color(0x9AA6BEFFu)
private val STEEL_DARK = Color(0x4A5470FFu)
private val STEEL_BRACE = Color(0x8C97B0FFu)
private val RIVET = Color(0xF4F7FFFFu)

/** Paints a steel girder [width] texels long and 8 high: a light top flange with rivets over an X-braced web. */
fun paintLedge(width: Int): Image {
    val image = Image(width, 8)
    image.fill(STEEL_DARK)
    for (y in 3..6) {
        for (x in 0 until width) {
            if ((x + y) % 8 == 0 || (x - y + 64) % 8 == 0) image[x, y] = STEEL_BRACE
        }
    }
    image.fillRect(0, 0, width, 1, STEEL_OUTLINE)
    image.fillRect(0, 1, width, 1, STEEL_LIGHT)
    image.fillRect(0, 2, width, 1, STEEL_MID)
    image.fillRect(0, 7, width, 1, STEEL_OUTLINE)
    image.fillRect(0, 0, 1, 8, STEEL_OUTLINE)
    image.fillRect(width - 1, 0, 1, 8, STEEL_OUTLINE)
    for (x in 4 until width - 2 step 8) image[x, 1] = RIVET
    return image
}

/**
 * Paints a lattice pylon 8 texels wide and [height] tall: two rails with X bracing between them,
 * the hatched steel the billboards stand on. Shorter pylons use the top of it as a region.
 */
fun paintPylon(height: Int): Image {
    val image = Image(8, height)
    for (y in 0 until height) {
        // A cross every 8 rows: one stroke going down to the right and one down to the left.
        val step = y % 8
        val down = 2 + step / 2
        val up = 5 - step / 2
        image[down, y] = STEEL_MID
        image[up, y] = STEEL_MID
        if (step == 0) image.fillRect(2, y, 4, 1, STEEL_DARK)
    }
    image.fillRect(0, 0, 1, height, STEEL_OUTLINE)
    image.fillRect(1, 0, 1, height, STEEL_MID)
    image.fillRect(6, 0, 1, height, STEEL_MID)
    image.fillRect(7, 0, 1, height, STEEL_OUTLINE)
    return image
}

private val UNIT_BODY = Color(0xB9C0CFFFu)
private val UNIT_SHADE = Color(0x8A92A6FFu)
private val UNIT_GRILLE = Color(0x5A6276FFu)

/** Paints a rooftop air conditioner 20 by 13 texels: a grey box with a round fan grille and vents. */
fun paintAirConditioner(): Image {
    val image = Image(20, 13)
    image.fill(UNIT_BODY)
    image.fillRect(0, 10, 20, 3, UNIT_SHADE)
    image.fillCircle(centerX = 6f, centerY = 5.5f, radius = 4f, color = UNIT_GRILLE)
    image.fillCircle(centerX = 6f, centerY = 5.5f, radius = 1.5f, color = UNIT_BODY)
    for (y in 2..8 step 2) image.fillRect(12, y, 6, 1, UNIT_GRILLE)
    image.fillRect(0, 0, 20, 1, STEEL_OUTLINE)
    image.fillRect(0, 0, 1, 13, STEEL_OUTLINE)
    image.fillRect(19, 0, 1, 13, STEEL_OUTLINE)
    image.fillRect(0, 12, 20, 1, STEEL_OUTLINE)
    return image
}

private val CLOUD = Color(0xFFFFFFFFu)
private val CLOUD_SHADE = Color(0xDCD8F2FFu)

/**
 * Paints a blocky cloud 40 by 16 texels: a few round puffs on a flat bottom, cut to hard-edged
 * texels and shaded underneath. [variant] picks the puffs.
 */
fun paintCloud(variant: Int): Image {
    val width = 40
    val height = 16
    val soft = Image(width, height)
    val random = Random(variant * 31 + 5)
    val puffs = 3 + variant % 2
    for (i in 0 until puffs) {
        val centerX = 8f + (width - 16f) * i / (puffs - 1) + random.nextFloat() * 3f - 1.5f
        val radius = 5f + random.nextFloat() * 4f + if (i == puffs / 2) 2f else 0f
        soft.fillCircle(centerX = centerX, centerY = height - radius * 0.8f - 1f, radius = radius, color = CLOUD)
    }
    soft.fillRect(5, height - 5, width - 10, 4, CLOUD)
    // Hard edges: a texel is either cloud or sky.
    val image = Image(width, height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (soft[x, y].alpha < 0.5f) continue
            val isUnderside = y >= height - 4 || soft[x, (y + 2).coerceAtMost(height - 1)].alpha < 0.5f
            image[x, y] = if (isUnderside) CLOUD_SHADE else CLOUD
        }
    }
    return image
}

private val SKYLINE_BACK = Color(0xCBC4EAFFu)
private val SKYLINE_FRONT = Color(0xB0A8D8FFu)
private val SKYLINE_WINDOW = Color(0xDCD7F2FFu)

/**
 * Paints the distant city, [width] by [height] texels: a pale back row of tall towers and a
 * darker front row, with a few lit windows. Towers that run off the right edge carry on at the
 * left, so the strip repeats without a seam.
 */
fun paintSkyline(width: Int, height: Int): Image {
    val image = Image(width, height)
    val random = Random(2026)
    for ((color, minHeight, maxHeight) in listOf(Triple(SKYLINE_BACK, 40, 76), Triple(SKYLINE_FRONT, 18, 48))) {
        var x = 0
        while (x < width) {
            val towerWidth = 10 + random.nextInt(18)
            val towerHeight = minHeight + random.nextInt(maxHeight - minHeight)
            val top = height - towerHeight
            for (column in x until x + towerWidth) {
                val wrapped = column % width
                for (row in top until height) image[wrapped, row] = color
            }
            if (random.nextInt(3) == 0) {
                val antenna = x + towerWidth / 2
                for (row in top - 6 until top) image[antenna % width, row] = color
            }
            if (color == SKYLINE_FRONT) {
                for (row in top + 3 until height - 2 step 4) {
                    for (column in x + 2 until x + towerWidth - 2 step 3) {
                        if (random.nextInt(4) == 0) image[column % width, row] = SKYLINE_WINDOW
                    }
                }
            }
            x += towerWidth + random.nextInt(4)
        }
    }
    return image
}

/** Paints a 1 by [height] strip from [top] at the top to [bottom] at the bottom, for the sky. */
fun paintGradient(height: Int, top: Color, bottom: Color): Image {
    val image = Image(1, height)
    for (y in 0 until height) image[0, y] = top.lerp(bottom, y / (height - 1f))
    return image
}

/** Paints a white four-pointed star [size] texels across, for impacts and sparkles. */
fun paintStar(size: Int): Image {
    val image = Image(size, size)
    val half = size / 2f
    for (y in 0 until size) {
        for (x in 0 until size) {
            val dx = abs(x + 0.5f - half) / half
            val dy = abs(y + 0.5f - half) / half
            if (sqrt(dx) + sqrt(dy) <= 1f) image[x, y] = Color.WHITE
        }
    }
    return image
}
