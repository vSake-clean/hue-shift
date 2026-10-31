package io.github.vsakeclean.hueshift.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.filter.Filters
import io.github.vsakeclean.hueshift.settings.HueSettings
import io.github.vsakeclean.hueshift.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun store(name: String) =
        SettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "$name.preferences_pb") }))

    @Test
    fun `defaults when nothing was saved`() = runBlocking {
        assertEquals(HueSettings(), store("empty").load())
    }

    @Test
    fun `settings survive a round trip`() = runBlocking {
        val store = store("roundtrip")
        val settings = HueSettings(
            enabled = true,
            filterId = Filters.Orange.id,
            intensities = mapOf(Filters.Orange.id to 0.42f, Filters.Grayscale.id to 0.8f),
            enginePreference = EngineKind.ROOT,
            rootAllowed = true,
            appliedEngine = EngineKind.ROOT,
        )
        store.save(settings)
        assertEquals(settings, store.load())

        val cleared = settings.copy(enginePreference = null, appliedEngine = null)
        store.save(cleared)
        assertEquals(cleared, store.load())
    }

    @Test
    fun `unknown or off filter ids fall back to the default filter`() {
        assertEquals(Filters.Default, HueSettings(filterId = "something-removed").filter)
        assertEquals(Filters.Default, HueSettings(filterId = Filters.OFF_ID).filter)
    }

    @Test
    fun `intensity is clamped and defaults per filter`() {
        val settings = HueSettings(intensities = mapOf(Filters.Grayscale.id to 0f, Filters.Redscale.id to 3f))
        assertEquals(HueSettings.MIN_INTENSITY, settings.intensityOf(Filters.Grayscale), 1e-6f)
        assertEquals(1f, settings.intensityOf(Filters.Redscale), 1e-6f)
        assertEquals(Filters.Orange.defaultIntensity, settings.intensityOf(Filters.Orange), 1e-6f)
    }

    @Test
    fun `the effective filter is off while disabled`() {
        assertEquals(Filters.Off, HueSettings(enabled = false, filterId = Filters.Negative.id).effectiveFilter)
        assertEquals(Filters.Negative, HueSettings(enabled = true, filterId = Filters.Negative.id).effectiveFilter)
    }
}
