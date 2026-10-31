package io.github.vsakeclean.hueshift.engine

import io.github.vsakeclean.hueshift.filter.ColorTransform
import io.github.vsakeclean.hueshift.filter.Filters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SurfaceFlingerTest {

    @Test
    fun `identity and null reset the matrix`() {
        assertEquals("service call SurfaceFlinger 1015 i32 0", SurfaceFlinger.shellCommand(null))
        assertEquals("service call SurfaceFlinger 1015 i32 0", SurfaceFlinger.shellCommand(ColorTransform.Identity))
    }

    @Test
    fun `matrix command lists 16 floats`() {
        val command = SurfaceFlinger.shellCommand(Filters.Negative.transformAt(1f))
        assertTrue(command.startsWith("service call SurfaceFlinger 1015 i32 1 f "))
        assertEquals(16, command.split(" f ").size - 1)
    }

    @Test
    fun `floats always use a dot, even with a Polish locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pl-PL"))
            val command = SurfaceFlinger.shellCommand(Filters.Grayscale.transformAt(1f))
            assertTrue(command.contains("f 0.212600"))
            assertFalse(command.contains(","))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
