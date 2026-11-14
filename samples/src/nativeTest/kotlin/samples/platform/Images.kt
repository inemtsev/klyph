package samples.platform

import klyph.platform.Color
import klyph.platform.Image
import klyph.platform.withAlpha
import samples.Sample
import samples.assertPrints
import kotlin.test.assertFailsWith

class Images {
    @Sample
    fun get() {
        val image = Image(4, 4)
        assertPrints(image[1, 2], "Color(#00000000)")
        image[1, 2] = Color(red = 1f, green = 0f, blue = 0f)
        assertPrints(image[1, 2], "Color(#ff0000ff)")
    }

    @Sample
    fun fill() {
        val image = Image(2, 2)
        image.fill(Color.WHITE)
        assertPrints(image[0, 0], "Color(#ffffffff)")
        assertPrints(image[1, 1], "Color(#ffffffff)")
    }

    @Sample
    fun decodeOrNull() {
        assertPrints(Image.decodeOrNull(byteArrayOf(1, 2, 3)), "null")
    }

    @Sample
    fun fillCircle() {
        val image = Image(16, 16)
        image.fillCircle(centerX = 8f, centerY = 8f, radius = 6f, color = Color.WHITE)
        assertPrints(image[8, 8], "Color(#ffffffff)")
        assertPrints(image[0, 0], "Color(#00000000)")
    }

    @Sample
    fun fillRect() {
        val image = Image(8, 8)
        image.fillRect(x = 2, y = 2, width = 4, height = 3, color = Color.WHITE)
        assertPrints(image[2, 2], "Color(#ffffffff)")
        assertPrints(image[5, 4], "Color(#ffffffff)")
        assertPrints(image[6, 4], "Color(#00000000)")
        // The part of a rectangle outside the image is left out.
        image.fillRect(x = 6, y = 6, width = 10, height = 10, color = Color.RED)
        assertPrints(image[7, 7], "Color(#ff0000ff)")
        assertFailsWith<IllegalArgumentException> { image.fillRect(x = 0, y = 0, width = -1, height = 1, color = Color.RED) }
    }

    @Sample
    fun draw() {
        val stamp = Image(2, 2)
        stamp.fill(Color.RED)
        stamp[0, 1] = Color.RED.withAlpha(0.5f)
        stamp[1, 1] = Color.TRANSPARENT
        val sheet = Image(4, 4)
        sheet.fill(Color.WHITE)
        sheet.draw(stamp, x = 1, y = 1)
        // An opaque pixel replaces the one under it, a half transparent one blends with it, and a transparent one leaves it.
        assertPrints(sheet[1, 1], "Color(#ff0000ff)")
        assertPrints(sheet[1, 2], "Color(#ff7f7fff)")
        assertPrints(sheet[2, 2], "Color(#ffffffff)")
        // The part of the stamp outside the sheet is left out.
        sheet.draw(stamp, x = 3, y = 3)
        assertPrints(sheet[3, 3], "Color(#ff0000ff)")
    }
}
