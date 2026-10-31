package io.github.vsakeclean.hueshift.ui

import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.core.buildHueState
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.engine.EngineState
import io.github.vsakeclean.hueshift.filter.Filters
import io.github.vsakeclean.hueshift.settings.HueSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ColorEffectsTest {

    private fun state(
        enabled: Boolean,
        engine: EngineKind?,
        filterId: String = Filters.Negative.id,
    ): HueState = buildHueState(
        HueSettings(enabled = enabled, filterId = filterId),
        engine?.let { mapOf(it to EngineState.READY) } ?: emptyMap(),
    )

    @Test
    fun `preview simulates the filter while it is not applied`() {
        val effect = PreviewEffect.forPreview(state(enabled = true, engine = null))
        assertEquals(Filters.Negative.transformAt(1f), effect.transform)
    }

    @Test
    fun `preview shows the plain picture while the system applies the filter`() {
        val effect = PreviewEffect.forPreview(state(enabled = true, engine = EngineKind.SHIZUKU))
        assertNull(effect.transform)
        assertNull(effect.tint)
    }

    @Test
    fun `preview uses the overlay tint in basic mode`() {
        // Orange in basic mode, but not applied yet (e.g. the service just got enabled).
        val effect = PreviewEffect.forPreview(
            state(enabled = true, engine = EngineKind.OVERLAY, filterId = Filters.Orange.id).copy(rendering = null),
        )
        assertNotNull(effect.tint)
        assertNull(effect.transform)
    }

    @Test
    fun `swatches show their true colours under a system-wide negative`() {
        val applied = state(enabled = true, engine = EngineKind.SHIZUKU)
        val compensation = ScreenCompensation.forState(applied)
        // The "off" swatch should look like the plain rainbow once the system negative is applied on top.
        val drawn = compensation.color(1f, 0.2f, 0.4f)
        val seen = Filters.Negative.transformAt(1f).apply(drawn.red, drawn.green, drawn.blue)
        assertEquals(1f, seen[0], 1e-2f)
        assertEquals(0.2f, seen[1], 1e-2f)
        assertEquals(0.4f, seen[2], 1e-2f)
    }

    @Test
    fun `darkening effects are not compensated`() {
        val orange = buildHueState(
            HueSettings(enabled = true, filterId = Filters.Orange.id),
            mapOf(EngineKind.SHIZUKU to EngineState.READY),
        )
        val drawn = ScreenCompensation.forState(orange).color(0.5f, 0.5f, 0.5f)
        assertEquals(0.5f, drawn.red, 1e-2f)
        assertEquals(0.5f, drawn.green, 1e-2f)
        assertEquals(0.5f, drawn.blue, 1e-2f)
    }
}
