package samples.platform

import klyph.platform.Color
import klyph.platform.Image
import samples.Sample
import samples.assertPrints

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
}
