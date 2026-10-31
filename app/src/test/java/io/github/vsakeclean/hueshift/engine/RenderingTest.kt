package io.github.vsakeclean.hueshift.engine

import io.github.vsakeclean.hueshift.filter.ColorTransform
import io.github.vsakeclean.hueshift.filter.OverlayTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderingTest {

    @Test
    fun `matrix fades in from nothing`() {
        val target = Rendering.Matrix(ColorTransform.negative())
        val start = Rendering.lerp(null, target, 0f) as Rendering.Matrix
        assertTrue(start.transform.isIdentity)
        assertEquals(target, Rendering.lerp(null, target, 1f))
    }

    @Test
    fun `matrix fades out to nothing`() {
        val from = Rendering.Matrix(ColorTransform.negative())
        assertNull(Rendering.lerp(from, null, 1f))
        val middle = Rendering.lerp(from, null, 0.5f) as Rendering.Matrix
        assertTrue(middle.transform.approximatelyEquals(ColorTransform.Identity.lerp(ColorTransform.negative(), 0.5f)))
    }

    @Test
    fun `tint fades by opacity`() {
        val tint = Rendering.Tint(OverlayTint(1f, 0.5f, 0f, 0.4f))
        val middle = Rendering.lerp(null, tint, 0.5f) as Rendering.Tint
        assertEquals(0.2f, middle.tint.alpha, 1e-4f)
        assertEquals(1f, middle.tint.red, 1e-4f)
    }

    @Test
    fun `different kinds switch at the end`() {
        val matrix = Rendering.Matrix(ColorTransform.negative())
        val tint = Rendering.Tint(OverlayTint(1f, 0f, 0f, 0.3f))
        assertEquals(tint, Rendering.lerp(matrix, tint, 0.5f))
    }
}
