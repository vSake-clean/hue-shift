package io.github.vsakeclean.hueshift.filter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayTintTest {

    private val tint = OverlayTint(1f, 0.5f, 0f, alpha = 0.4f)

    @Test
    fun `blend and unblend are inverse`() {
        val blended = tint.blendOver(0.2f, 0.6f, 0.9f)
        assertArrayEquals(floatArrayOf(0.2f, 0.6f, 0.9f), tint.unblend(blended[0], blended[1], blended[2]), 1e-4f)
    }

    @Test
    fun `fading from nothing keeps the colour`() {
        val half = OverlayTint.None.lerp(tint, 0.5f)
        assertEquals(1f, half.red, 1e-4f)
        assertEquals(0.5f, half.green, 1e-4f)
        assertEquals(0.2f, half.alpha, 1e-4f)
    }

    @Test
    fun `argb packs alpha first`() {
        assertEquals(0xFFFF0000.toInt(), OverlayTint(1f, 0f, 0f, 1f).toArgb())
        assertEquals(0x80000000.toInt(), OverlayTint(0f, 0f, 0f, 0.5f).toArgb() and 0xFF000000.toInt())
    }
}
