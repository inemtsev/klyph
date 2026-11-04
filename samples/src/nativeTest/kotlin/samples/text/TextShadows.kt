package samples.text

import klyph.platform.Color
import klyph.text.TextShadow
import samples.Sample
import samples.assertPrints

class TextShadows {
    @Sample
    fun textShadow() {
        val shadow = TextShadow(offsetX = 2f, offsetY = 2f, color = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.5f))
        assertPrints(shadow, "TextShadow(offsetX=2.0, offsetY=2.0, color=Color(#00000080))")
        // Pass it to Canvas.drawText or set Text.shadow.
        assertPrints(TextShadow() == TextShadow(), "true")
    }
}
