package klyph.text

import klyph.platform.Color
import klyph.platform.Image
import klyph.render.Texture
import klyph.render.TextureRegion

// The engine's built-in font: an 8 by 8 monospace bitmap font for printable ASCII, U+0020 to
// U+007E, rasterized at startup into one atlas together with the white pixel that
// `Canvas.drawRect` draws with.
//
// Glyph data: `font8x8_basic.h` from https://github.com/dhepper/font8x8 by Daniel Hepper,
// commit 8e279d2d864e79128e96188a6b9526cfa3fbfef9, file SHA-256
// 49d8df366296b203ca3211bc0672cf2a762135bf12710735b6292756b19dffd5. Entries U+0020 to U+007E
// are copied unchanged, one hex string of eight row bytes each.
//
// Licence: public domain. The header and the repository README both state "License: Public
// Domain". They derive the data from `font8_8.asm` by Marcel Sondaar, whose header names
// "International Business Machines (public domain VGA fonts)" and "License: Public Domain".
//
// Encoding: each glyph is eight bytes, the top row first. Within a byte, the least significant
// bit is the leftmost pixel.

/** Rows of the glyphs from `FIRST_CHAR` on, as 16 hex digits: eight bytes, top row first. */
private val GLYPH_ROWS: Array<String> = arrayOf(
    "0000000000000000", // U+0020 space
    "183C3C1818001800", // U+0021 !
    "3636000000000000", // U+0022 "
    "36367F367F363600", // U+0023 #
    "0C3E031E301F0C00", // U+0024 $
    "006333180C666300", // U+0025 %
    "1C361C6E3B336E00", // U+0026 &
    "0606030000000000", // U+0027 '
    "180C0606060C1800", // U+0028 (
    "060C1818180C0600", // U+0029 )
    "00663CFF3C660000", // U+002A *
    "000C0C3F0C0C0000", // U+002B +
    "00000000000C0C06", // U+002C ,
    "0000003F00000000", // U+002D -
    "00000000000C0C00", // U+002E .
    "6030180C06030100", // U+002F /
    "3E63737B6F673E00", // U+0030 0
    "0C0E0C0C0C0C3F00", // U+0031 1
    "1E33301C06333F00", // U+0032 2
    "1E33301C30331E00", // U+0033 3
    "383C36337F307800", // U+0034 4
    "3F031F3030331E00", // U+0035 5
    "1C06031F33331E00", // U+0036 6
    "3F3330180C0C0C00", // U+0037 7
    "1E33331E33331E00", // U+0038 8
    "1E33333E30180E00", // U+0039 9
    "000C0C00000C0C00", // U+003A :
    "000C0C00000C0C06", // U+003B ;
    "180C0603060C1800", // U+003C <
    "00003F00003F0000", // U+003D =
    "060C1830180C0600", // U+003E >
    "1E3330180C000C00", // U+003F ?
    "3E637B7B7B031E00", // U+0040 @
    "0C1E33333F333300", // U+0041 A
    "3F66663E66663F00", // U+0042 B
    "3C66030303663C00", // U+0043 C
    "1F36666666361F00", // U+0044 D
    "7F46161E16467F00", // U+0045 E
    "7F46161E16060F00", // U+0046 F
    "3C66030373667C00", // U+0047 G
    "3333333F33333300", // U+0048 H
    "1E0C0C0C0C0C1E00", // U+0049 I
    "7830303033331E00", // U+004A J
    "6766361E36666700", // U+004B K
    "0F06060646667F00", // U+004C L
    "63777F7F6B636300", // U+004D M
    "63676F7B73636300", // U+004E N
    "1C36636363361C00", // U+004F O
    "3F66663E06060F00", // U+0050 P
    "1E3333333B1E3800", // U+0051 Q
    "3F66663E36666700", // U+0052 R
    "1E33070E38331E00", // U+0053 S
    "3F2D0C0C0C0C1E00", // U+0054 T
    "3333333333333F00", // U+0055 U
    "33333333331E0C00", // U+0056 V
    "6363636B7F776300", // U+0057 W
    "6363361C1C366300", // U+0058 X
    "3333331E0C0C1E00", // U+0059 Y
    "7F6331184C667F00", // U+005A Z
    "1E06060606061E00", // U+005B [
    "03060C1830604000", // U+005C backslash
    "1E18181818181E00", // U+005D ]
    "081C366300000000", // U+005E ^
    "00000000000000FF", // U+005F _
    "0C0C180000000000", // U+0060 `
    "00001E303E336E00", // U+0061 a
    "0706063E66663B00", // U+0062 b
    "00001E3303331E00", // U+0063 c
    "3830303E33336E00", // U+0064 d
    "00001E333F031E00", // U+0065 e
    "1C36060F06060F00", // U+0066 f
    "00006E33333E301F", // U+0067 g
    "0706366E66666700", // U+0068 h
    "0C000E0C0C0C1E00", // U+0069 i
    "300030303033331E", // U+006A j
    "070666361E366700", // U+006B k
    "0E0C0C0C0C0C1E00", // U+006C l
    "0000337F7F6B6300", // U+006D m
    "00001F3333333300", // U+006E n
    "00001E3333331E00", // U+006F o
    "00003B66663E060F", // U+0070 p
    "00006E33333E3078", // U+0071 q
    "00003B6E66060F00", // U+0072 r
    "00003E031E301F00", // U+0073 s
    "080C3E0C0C2C1800", // U+0074 t
    "0000333333336E00", // U+0075 u
    "00003333331E0C00", // U+0076 v
    "0000636B7F7F3600", // U+0077 w
    "000063361C366300", // U+0078 x
    "00003333333E301F", // U+0079 y
    "00003F190C263F00", // U+007A z
    "380C0C070C0C3800", // U+007B {
    "1818180018181800", // U+007C |
    "070C0C380C0C0700", // U+007D }
    "6E3B000000000000", // U+007E ~
)

/** The engine's built-in font and the white pixel `Canvas.drawRect` draws with, both in one atlas. */
internal class BuiltInFont(
    /** The 8 by 8 font: 95 glyphs, line height 8, baseline 7, advance 8, no kerning. */
    val font: Font,
    /** A one-texel region of opaque white whose neighbors are white too, so filtering keeps it white. */
    val whitePixel: TextureRegion,
) {
    override fun toString(): String = "BuiltInFont(font=$font)"

    companion object {
        /** The first character the font covers, the space. */
        const val FIRST_CHAR: Int = 0x20

        /** Number of glyphs: printable ASCII, U+0020 to U+007E. */
        const val GLYPH_COUNT: Int = 95

        /** Glyph width and height in texels, also the advance and the line height. */
        const val GLYPH_SIZE: Int = 8

        /** Distance from the top of a glyph cell to the baseline: the bottom row holds descenders. */
        const val BASELINE: Int = 7

        /** Atlas cell size: the glyph plus one transparent texel on each side. */
        private const val CELL = GLYPH_SIZE + 2
        private const val COLUMNS = 16
        private const val ROWS = 6

        /** Atlas width in texels: 16 cells. */
        const val ATLAS_WIDTH: Int = COLUMNS * CELL

        /** Atlas height in texels: 6 cells, the 95 glyphs and the white block in the last cell. */
        const val ATLAS_HEIGHT: Int = ROWS * CELL

        /** Top-left texel of the 3 by 3 white block in the cell after the last glyph. */
        private val WHITE_X = (GLYPH_COUNT % COLUMNS) * CELL + 1
        private val WHITE_Y = (GLYPH_COUNT / COLUMNS) * CELL + 1

        /**
         * Returns a new [ATLAS_WIDTH] by [ATLAS_HEIGHT] image of the glyphs, in rows of 16 from
         * the space, each in a 10 by 10 cell with a transparent border, followed by a 3 by 3 white
         * block. Set pixels are opaque white and the rest transparent white, so linear filtering
         * fades glyph edges instead of darkening them.
         */
        fun rasterize(): Image {
            val image = Image(ATLAS_WIDTH, ATLAS_HEIGHT)
            image.fill(TRANSPARENT_WHITE)
            for (index in 0 until GLYPH_COUNT) {
                val rows = GLYPH_ROWS[index]
                val left = cellX(index) + 1
                val top = cellY(index) + 1
                for (row in 0 until GLYPH_SIZE) {
                    val bits = rows.substring(row * 2, row * 2 + 2).toInt(16)
                    for (column in 0 until GLYPH_SIZE) {
                        if ((bits shr column) and 1 != 0) image[left + column, top + row] = Color.WHITE
                    }
                }
            }
            for (y in WHITE_Y until WHITE_Y + 3) {
                for (x in WHITE_X until WHITE_X + 3) image[x, y] = Color.WHITE
            }
            return image
        }

        /**
         * Builds the font and white pixel over [atlas], a texture of the image [rasterize]
         * returns. Tests pass a headless texture of the same size.
         */
        fun over(atlas: Texture): BuiltInFont {
            require(atlas.width == ATLAS_WIDTH && atlas.height == ATLAS_HEIGHT) {
                "Built-in font atlas $atlas is not ${ATLAS_WIDTH}x$ATLAS_HEIGHT."
            }
            val size = GLYPH_SIZE.toFloat()
            val glyphs = List(GLYPH_COUNT) { index ->
                val char = (FIRST_CHAR + index).toChar()
                val region = if (GLYPH_ROWS[index].all { it == '0' }) {
                    null
                } else {
                    TextureRegion(atlas, cellX(index) + 1, cellY(index) + 1, GLYPH_SIZE, GLYPH_SIZE)
                }
                Glyph(char, region, xOffset = 0f, yOffset = 0f, xAdvance = size)
            }
            val font = Font(glyphs, emptyMap(), lineHeight = size, baseline = BASELINE.toFloat(), pages = listOf(atlas))
            return BuiltInFont(font, TextureRegion(atlas, WHITE_X + 1, WHITE_Y + 1, 1, 1))
        }

        private fun cellX(index: Int): Int = (index % COLUMNS) * CELL

        private fun cellY(index: Int): Int = (index / COLUMNS) * CELL

        /** White at zero alpha. */
        private val TRANSPARENT_WHITE = Color(0xFFFFFF00u)
    }
}
