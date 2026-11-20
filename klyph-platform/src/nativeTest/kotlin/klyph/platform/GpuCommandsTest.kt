package klyph.platform

import cnames.structs.SDL_GPUCopyPass
import cnames.structs.SDL_GPUTexture
import cnames.structs.SDL_GPUTransferBuffer
import kotlinx.cinterop.toCPointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GpuCommandsTest {
    // The guards run before SDL sees a handle, so these never point at anything and are never read.
    private val pass = CopyPass(checkNotNull(1L.toCPointer<SDL_GPUCopyPass>()))
    private val source = TransferBuffer(checkNotNull(2L.toCPointer<SDL_GPUTransferBuffer>()))
    private val destination = GpuTexture(checkNotNull(3L.toCPointer<SDL_GPUTexture>()))

    private fun rejection(width: Int = 4, height: Int = 4, sourceOffset: Int = 0, x: Int = 0, y: Int = 0): String? =
        assertFailsWith<IllegalArgumentException> {
            pass.uploadToTexture(source, destination, width, height, sourceOffset = sourceOffset, x = x, y = y)
        }.message

    @Test
    fun uploadToTextureRejectsAnEmptyOrNegativeSize() {
        assertEquals("Upload width 0 is not positive.", rejection(width = 0))
        assertEquals("Upload height -2 is not positive.", rejection(height = -2))
    }

    @Test
    fun uploadToTextureRejectsANegativeOffsetOrCorner() {
        assertEquals("Upload source offset -4 is negative.", rejection(sourceOffset = -4))
        assertEquals("Upload corner (-1, 0) is negative.", rejection(x = -1))
        assertEquals("Upload corner (0, -3) is negative.", rejection(y = -3))
    }
}
