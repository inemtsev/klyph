package klyph.roller

import klyph.platform.Color
import klyph.platform.Image
import klyph.platform.lerp
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

// The art that is painted in code rather than generated: the repeating tiles of the brick walls,
// stone caps, windows and steel girders, pylons, air conditioners, blocky clouds, the distant
// skyline, the sky gradient and the impact star. Each is painted once at startup into an Image, one
// texel at a time, and uploaded as a texture that is drawn with nearest sampling at Tuning.PIXEL
// points per texel (or more), so it looks like the generated pixel-art sprites. A tile is uploaded
// with SamplerAddress.REPEAT, and a Sprite with a tile size repeats it across a building, so the
// level needs a few small textures instead of one for every building. Every painting is seeded, so
// every run looks the same.

/** The three brick colors and the mortar of one wall's palette. */
private class BrickPalette(val bricks: List<Color>, val mortar: Color)

private val PALETTES = listOf(
    BrickPalette(listOf(Color(0xB4473CFFu), Color(0xA63F36FFu), Color(0xBE5245FFu)), Color(0x7A2C28FFu)),
    BrickPalette(listOf(Color(0x9C3B3BFFu), Color(0x8F3434FFu), Color(0xA84444FFu)), Color(0x652424FFu)),
    BrickPalette(listOf(Color(0xC0583FFFu), Color(0xB44F38FFu), Color(0xCA6448FFu)), Color(0x833A2AFFu)),
)

/** How many wall palettes there are, so how many brick tiles [paintBrickTile] paints. */
val BRICK_STYLE_COUNT: Int = PALETTES.size

private val CAP_OUTLINE = Color(0x3B1E1EFFu)
private val CAP_LIGHT = Color(0xF0E2D2FFu)
private val CAP_MID = Color(0xD6C1AEFFu)
private val CAP_SHADE = Color(0xA08470FFu)
private val UNDER_CAP = Color(0x4E1F1CFFu)

/** The dark column at each side of a wall, which the level draws as a quad of this color. */
val WALL_EDGE = Color(0x5A2420FFu)
private val WINDOW_FRAME = Color(0xE9DDCBFFu)
private val WINDOW_SILL = Color(0xCDBBA6FFu)
private val GLASS = Color(0x2F3D73FFu)
private val GLASS_LIGHT = Color(0x6F84C8FFu)
private val GLASS_SHINE = Color(0xA9B8E8FFu)

/**
 * Paints one repeat of a brick wall in the palette picked by [style]: rows of bricks 8 texels long
 * and 4 high with their mortar, every other row shifted by half a brick, each brick one of the
 * palette's shades at random. The tile is [BRICKS_ACROSS] bricks across and [BRICK_ROWS] rows
 * down, so 32 by 16 texels, and joins its copies without a seam; a tile of one or two bricks
 * repeats too plainly, as stripes or a lattice.
 */
fun paintBrickTile(style: Int): Image {
    val palette = PALETTES[style % PALETTES.size]
    val random = Random(style * 7919)
    val shades = IntArray(BRICK_ROWS * BRICKS_ACROSS) { random.nextInt(palette.bricks.size) }
    val image = Image(BRICKS_ACROSS * 8, BRICK_ROWS * 4)
    for (y in 0 until image.height) {
        val row = y / 4
        val shift = if (row % 2 == 0) 0 else 4
        for (x in 0 until image.width) {
            val isMortar = y % 4 == 3 || (x + shift) % 8 == 7
            val brick = (x + shift) % image.width / 8
            image[x, y] = if (isMortar) palette.mortar else palette.bricks[shades[row * BRICKS_ACROSS + brick]]
        }
    }
    return image
}

private const val BRICKS_ACROSS = 4
private const val BRICK_ROWS = 4

/**
 * Paints one repeat of the stone cap along a roof, 12 texels across and 6 high: an outline over a
 * light face, a shaded lip with a tick every 12 texels and the dark joint under it. The cap is the
 * edge the hero rolls on.
 */
fun paintCapTile(): Image {
    val image = Image(CAP_TILE_TEXELS_WIDE, CAP_TILE_TEXELS_HIGH)
    image.fillRect(0, 0, width = CAP_TILE_TEXELS_WIDE, height = 1, color = CAP_OUTLINE)
    image.fillRect(0, 1, width = CAP_TILE_TEXELS_WIDE, height = 2, color = CAP_LIGHT)
    image.fillRect(0, 3, width = CAP_TILE_TEXELS_WIDE, height = 1, color = CAP_MID)
    image.fillRect(0, 4, width = CAP_TILE_TEXELS_WIDE, height = 1, color = CAP_SHADE)
    image.fillRect(0, 5, width = CAP_TILE_TEXELS_WIDE, height = 1, color = UNDER_CAP)
    image.fillRect(3, 3, width = 1, height = 2, color = CAP_SHADE)
    return image
}

private const val CAP_TILE_TEXELS_WIDE = 12
private const val CAP_TILE_TEXELS_HIGH = 6

/** Texels across of a window, not counting the ends of its sill, and between two windows. */
const val WINDOW_TEXELS = 10
const val WINDOW_GAP_TEXELS = 8

/**
 * Paints one repeat of a row of windows, [WINDOW_TEXELS] + [WINDOW_GAP_TEXELS] texels across and
 * 15 high: a two-pane window over its sill, a texel in from the left so that the sill, which sticks
 * out a texel at each side, stays inside the tile, then the gap. The rest is clear, so the wall shows.
 */
fun paintWindowTile(): Image {
    val windowHeight = 14
    val image = Image(WINDOW_TEXELS + WINDOW_GAP_TEXELS, windowHeight + 1)
    val x = 1
    image.fillRect(x - 1, windowHeight - 1, width = WINDOW_TEXELS + 2, height = 2, color = WINDOW_SILL)
    image.fillRect(x, 0, width = WINDOW_TEXELS, height = windowHeight - 1, color = WINDOW_FRAME)
    image.fillRect(x + 1, 1, width = WINDOW_TEXELS - 2, height = windowHeight - 3, color = GLASS)
    image.fillRect(x + 1, 1, width = WINDOW_TEXELS - 2, height = 3, color = GLASS_LIGHT)
    // A diagonal shine across the lower panes.
    for (k in 0 until 4) {
        image[x + 2 + k, 9 - k] = GLASS_SHINE
    }
    image.fillRect(x + WINDOW_TEXELS / 2 - 1, 1, width = 1, height = windowHeight - 3, color = WINDOW_FRAME)
    return image
}

val STEEL_OUTLINE = Color(0x262B38FFu)
private val STEEL_LIGHT = Color(0xC9D2E3FFu)
private val STEEL_MID = Color(0x9AA6BEFFu)
private val STEEL_DARK = Color(0x4A5470FFu)
private val STEEL_BRACE = Color(0x8C97B0FFu)
private val RIVET = Color(0xF4F7FFFFu)

/**
 * Paints one repeat of a steel girder, 8 texels long and 8 high: a light top flange with a rivet
 * over an X-braced web. The level draws the dark plate at each end of a girder apart, as a quad of
 * [STEEL_OUTLINE].
 */
fun paintGirderTile(): Image {
    val image = Image(8, 8)
    image.fill(STEEL_DARK)
    for (y in 3..6) {
        for (x in 0 until 8) {
            if ((x + y) % 8 == 0 || (x - y + 64) % 8 == 0) image[x, y] = STEEL_BRACE
        }
    }
    image.fillRect(0, 0, width = 8, height = 1, color = STEEL_OUTLINE)
    image.fillRect(0, 1, width = 8, height = 1, color = STEEL_LIGHT)
    image.fillRect(0, 2, width = 8, height = 1, color = STEEL_MID)
    image.fillRect(0, 7, width = 8, height = 1, color = STEEL_OUTLINE)
    image[4, 1] = RIVET
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
        if (step == 0) image.fillRect(2, y, width = 4, height = 1, color = STEEL_DARK)
    }
    image.fillRect(0, 0, width = 1, height = height, color = STEEL_OUTLINE)
    image.fillRect(1, 0, width = 1, height = height, color = STEEL_MID)
    image.fillRect(6, 0, width = 1, height = height, color = STEEL_MID)
    image.fillRect(7, 0, width = 1, height = height, color = STEEL_OUTLINE)
    return image
}

private val UNIT_BODY = Color(0xB9C0CFFFu)
private val UNIT_SHADE = Color(0x8A92A6FFu)
private val UNIT_GRILLE = Color(0x5A6276FFu)

/** Paints a rooftop air conditioner 20 by 13 texels: a grey box with a round fan grille and vents. */
fun paintAirConditioner(): Image {
    val image = Image(20, 13)
    image.fill(UNIT_BODY)
    image.fillRect(0, 10, width = 20, height = 3, color = UNIT_SHADE)
    image.fillCircle(centerX = 6f, centerY = 5.5f, radius = 4f, color = UNIT_GRILLE)
    image.fillCircle(centerX = 6f, centerY = 5.5f, radius = 1.5f, color = UNIT_BODY)
    for (y in 2..8 step 2) image.fillRect(12, y, width = 6, height = 1, color = UNIT_GRILLE)
    image.fillRect(0, 0, width = 20, height = 1, color = STEEL_OUTLINE)
    image.fillRect(0, 0, width = 1, height = 13, color = STEEL_OUTLINE)
    image.fillRect(19, 0, width = 1, height = 13, color = STEEL_OUTLINE)
    image.fillRect(0, 12, width = 20, height = 1, color = STEEL_OUTLINE)
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
    soft.fillRect(5, height - 5, width = width - 10, height = 4, color = CLOUD)
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
