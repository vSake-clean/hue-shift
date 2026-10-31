package io.github.vsakeclean.hueshift.filter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {

    private val delta = 1e-3f

    @Test
    fun `filter ids are unique and resolvable`() {
        val ids = Filters.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        Filters.all.forEach { assertEquals(it, Filters.byId(it.id)) }
        assertNull(Filters.byId("does-not-exist"))
    }

    @Test
    fun `off is the identity`() {
        assertTrue(Filters.Off.isOff)
        assertTrue(Filters.Off.transformAt(1f).isIdentity)
        assertFalse(Filters.Off.supportsOverlay)
    }

    @Test
    fun `grayscale makes all channels equal`() {
        val rgb = Filters.Grayscale.transformAt(1f).apply(0.9f, 0.3f, 0.1f)
        assertEquals(rgb[0], rgb[1], delta)
        assertEquals(rgb[1], rgb[2], delta)
        // and keeps white white
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), Filters.Grayscale.transformAt(1f).apply(1f, 1f, 1f), delta)
    }

    @Test
    fun `negative turns every colour into its opposite`() {
        val negative = Filters.Negative.transformAt(1f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), negative.apply(1f, 1f, 1f), delta)
        assertArrayEquals(floatArrayOf(0f, 1f, 1f), negative.apply(1f, 0f, 0f), delta)
    }

    @Test
    fun `inverted colours match Android's built-in colour inversion`() {
        // Column-major matrix from frameworks/base ColorDisplayService.MATRIX_INVERT_COLOR.
        val android = floatArrayOf(
            0.402f, -0.598f, -0.599f, 0f,
            -1.174f, -0.174f, -1.175f, 0f,
            -0.228f, -0.228f, 0.772f, 0f,
            1f, 1f, 1f, 1f,
        )
        assertArrayEquals(android, Filters.Inverted.transformAt(1f).toSurfaceFlingerMatrix(), 2e-3f)
        // Black and white swap, a pure red stays reddish.
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), Filters.Inverted.transformAt(1f).apply(1f, 1f, 1f), delta)
        val red = Filters.Inverted.transformAt(1f).apply(1f, 0f, 0f)
        assertTrue(red[0] > red[1] && red[0] > red[2])
    }

    @Test
    fun `red scale keeps only the red channel`() {
        val rgb = Filters.Redscale.transformAt(1f).apply(0.2f, 0.9f, 0.4f)
        assertTrue(rgb[0] > 0f)
        assertEquals(0f, rgb[1], delta)
        assertEquals(0f, rgb[2], delta)
    }

    @Test
    fun `orange removes blue first`() {
        val white = Filters.Orange.transformAt(1f).apply(1f, 1f, 1f)
        assertEquals(1f, white[0], delta)
        assertTrue(white[1] < 1f)
        assertTrue(white[2] < white[1])
    }

    @Test
    fun `intensity scales the effect and is ignored where it makes no sense`() {
        listOf(Filters.Grayscale, Filters.Orange, Filters.Redscale).forEach { filter ->
            assertTrue(filter.supportsIntensity)
            assertTrue(filter.transformAt(0f).isIdentity)
        }
        listOf(Filters.Inverted, Filters.Negative).forEach { filter ->
            assertFalse(filter.supportsIntensity)
            assertEquals(filter.transformAt(1f), filter.transformAt(0.2f))
        }
    }

    @Test
    fun `only warm filters have an overlay approximation`() {
        assertNotNull(Filters.Orange.overlayTintAt(1f))
        assertNotNull(Filters.Redscale.overlayTintAt(1f))
        listOf(Filters.Grayscale, Filters.Inverted, Filters.Negative, Filters.Off).forEach {
            assertNull(it.overlayTintAt(1f))
        }
    }
}
