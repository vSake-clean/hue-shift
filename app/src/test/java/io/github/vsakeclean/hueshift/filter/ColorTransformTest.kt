package io.github.vsakeclean.hueshift.filter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTransformTest {

    private val delta = 1e-4f

    @Test
    fun `identity leaves colours untouched`() {
        assertArrayEquals(floatArrayOf(0.2f, 0.5f, 0.9f), ColorTransform.Identity.apply(0.2f, 0.5f, 0.9f), delta)
        assertTrue(ColorTransform.Identity.isIdentity)
    }

    @Test
    fun `lerp goes from start to end`() {
        val negative = ColorTransform.negative()
        assertEquals(ColorTransform.Identity, ColorTransform.Identity.lerp(negative, 0f))
        assertEquals(negative, ColorTransform.Identity.lerp(negative, 1f))
        // Halfway between identity and negative every colour becomes mid grey.
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f), ColorTransform.Identity.lerp(negative, 0.5f).apply(0.1f, 0.7f, 1f), delta)
    }

    @Test
    fun `times applies the right-hand transform first`() {
        val halveRed = ColorTransform.scale(0.5f, 1f, 1f)
        val negative = ColorTransform.negative()
        // negative(halveRed(c)) for c = white: red 1 -> 0.5 -> 0.5, green 1 -> 1 -> 0
        assertArrayEquals(floatArrayOf(0.5f, 0f, 0f), (negative * halveRed).apply(1f, 1f, 1f), delta)
        // halveRed(negative(c)) for c = black: red 0 -> 1 -> 0.5, green 0 -> 1 -> 1
        assertArrayEquals(floatArrayOf(0.5f, 1f, 1f), (halveRed * negative).apply(0f, 0f, 0f), delta)
    }

    @Test
    fun `inverse undoes the transform`() {
        val transforms = listOf(
            ColorTransform.negative(),
            ColorTransform.luminanceInversion(),
            ColorTransform.scale(1f, 0.7f, 0.4f),
            ColorTransform.Identity.lerp(ColorTransform.luminance(1f, 1f, 1f), 0.5f),
        )
        for (transform in transforms) {
            val inverse = requireNotNull(transform.inverse()) { "$transform should be invertible" }
            assertTrue("$transform", (inverse * transform).approximatelyEquals(ColorTransform.Identity, 1e-3f))
        }
    }

    @Test
    fun `full grayscale cannot be undone`() {
        assertNull(ColorTransform.luminance(1f, 1f, 1f).inverse())
    }

    @Test
    fun `apply clamps to the valid range`() {
        assertArrayEquals(floatArrayOf(1f, 0f, 1f), ColorTransform.scale(3f, -1f, 1f).apply(0.5f, 0.5f, 1f), delta)
    }

    @Test
    fun `surface flinger matrix is column major with offsets in the last column`() {
        val m = ColorTransform.negative().toSurfaceFlingerMatrix()
        assertEquals(16, m.size)
        // Column 0 holds the coefficients of the red input for the r, g, b, a outputs.
        assertArrayEquals(floatArrayOf(-1f, 0f, 0f, 0f), m.copyOfRange(0, 4), delta)
        assertArrayEquals(floatArrayOf(0f, -1f, 0f, 0f), m.copyOfRange(4, 8), delta)
        assertArrayEquals(floatArrayOf(0f, 0f, -1f, 0f), m.copyOfRange(8, 12), delta)
        // Column 3: offsets, and 1 for alpha (the last row must stay 0, 0, 0, 1).
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f), m.copyOfRange(12, 16), delta)
    }

    @Test
    fun `surface flinger matrix places cross-channel coefficients correctly`() {
        val gray = ColorTransform.luminance(1f, 0f, 0f).toSurfaceFlingerMatrix()
        // Red output = 0.2126 R + 0.7152 G + 0.0722 B: row 0 of columns 0, 1, 2.
        assertEquals(Luma.Rec709.red, gray[0], delta)
        assertEquals(Luma.Rec709.green, gray[4], delta)
        assertEquals(Luma.Rec709.blue, gray[8], delta)
        // Green output is zero.
        assertEquals(0f, gray[1], delta)
        assertEquals(0f, gray[5], delta)
    }

    @Test
    fun `colour matrix values use offsets in the 0 to 255 range`() {
        val values = ColorTransform.negative().toColorMatrixValues()
        assertEquals(20, values.size)
        assertArrayEquals(floatArrayOf(-1f, 0f, 0f, 0f, 255f), values.copyOfRange(0, 5), delta)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f), values.copyOfRange(15, 20), delta)
    }
}
