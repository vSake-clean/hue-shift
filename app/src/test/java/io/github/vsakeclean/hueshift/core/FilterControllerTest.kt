package io.github.vsakeclean.hueshift.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.engine.EngineState
import io.github.vsakeclean.hueshift.engine.FilterEngine
import io.github.vsakeclean.hueshift.engine.Rendering
import io.github.vsakeclean.hueshift.filter.Filters
import io.github.vsakeclean.hueshift.filter.HueFilter
import io.github.vsakeclean.hueshift.settings.HueSettings
import io.github.vsakeclean.hueshift.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class FilterControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private val files = AtomicInteger()

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Records everything it is asked to show. */
    private class FakeEngine(
        override val kind: EngineKind,
        ready: Boolean = true,
    ) : FilterEngine {
        override val state = MutableStateFlow(if (ready) EngineState.READY else EngineState.NOT_RUNNING)
        override val frameIntervalMillis = 4L
        override val errorDetails = MutableStateFlow<String?>(null)
        val shown = CopyOnWriteArrayList<Rendering?>()

        override suspend fun show(rendering: Rendering?) {
            shown += rendering
        }
    }

    private fun newFile() = File(tmp.root, "settings-${files.incrementAndGet()}.preferences_pb")

    private fun storeFor(file: File = newFile()) =
        SettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))

    /** Writes settings like a previous run of the app would have done. */
    private suspend fun seed(settings: HueSettings): File {
        val file = newFile()
        val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        SettingsStore(PreferenceDataStoreFactory.create(scope = seedScope, produceFile = { file })).save(settings)
        seedScope.coroutineContext[Job]!!.cancelAndJoin()
        return file
    }

    private fun controller(store: SettingsStore, vararg engines: FilterEngine) =
        FilterController(scope, store, engines.toList()).also { it.start() }

    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(3_000) {
        while (!condition()) delay(10)
    }

    private fun matrixOf(filter: HueFilter, intensity: Float = filter.defaultIntensity) =
        Rendering.Matrix(filter.transformAt(intensity))

    @Test
    fun `selecting a filter fades the whole screen into it`() = runBlocking {
        val engine = FakeEngine(EngineKind.SHIZUKU)
        val controller = controller(storeFor(), engine)
        controller.awaitLoaded()

        controller.select(Filters.Grayscale)
        controller.awaitRendered()

        assertEquals(matrixOf(Filters.Grayscale), engine.shown.last())
        assertTrue("expected animation frames, got ${engine.shown.size}", engine.shown.size > 3)
        assertTrue(controller.state.value.isAppliedSystemWide)
        waitUntil { controller.state.value.settings.appliedEngine == EngineKind.SHIZUKU }
    }

    @Test
    fun `switching off fades back to normal colours`() = runBlocking {
        val engine = FakeEngine(EngineKind.SHIZUKU)
        val controller = controller(storeFor(), engine)
        controller.awaitLoaded()
        controller.select(Filters.Negative)
        controller.awaitRendered()
        engine.shown.clear()

        controller.setEnabled(false)
        controller.awaitRendered()

        assertNull(engine.shown.last())
        assertTrue(engine.shown.size > 3)
        waitUntil { controller.state.value.settings.appliedEngine == null }
    }

    @Test
    fun `starting switched off never touches the system colours`() = runBlocking {
        val engine = FakeEngine(EngineKind.SHIZUKU)
        val controller = controller(storeFor(), engine)
        controller.awaitLoaded()
        controller.awaitRendered()
        delay(200)
        assertTrue(engine.shown.isEmpty())
    }

    @Test
    fun `a filter that was on before is restored at once`() = runBlocking {
        val file = seed(HueSettings(enabled = true, filterId = Filters.Negative.id, appliedEngine = EngineKind.SHIZUKU))
        val engine = FakeEngine(EngineKind.SHIZUKU)
        val controller = controller(storeFor(file), engine)
        controller.awaitLoaded()
        controller.awaitRendered()
        assertEquals(listOf<Rendering?>(matrixOf(Filters.Negative)), engine.shown.toList())
    }

    @Test
    fun `a filter left over by a previous run is removed once the engine is back`() = runBlocking {
        val file = seed(HueSettings(enabled = false, appliedEngine = EngineKind.SHIZUKU))
        val engine = FakeEngine(EngineKind.SHIZUKU, ready = false)
        val controller = controller(storeFor(file), engine)
        controller.awaitLoaded()
        delay(100)
        assertTrue(engine.shown.isEmpty())

        engine.state.value = EngineState.READY
        controller.awaitRendered()

        assertEquals(listOf<Rendering?>(null), engine.shown.toList())
        waitUntil { controller.state.value.settings.appliedEngine == null }
    }

    @Test
    fun `overlay mode only draws warm filters`() = runBlocking {
        val overlay = FakeEngine(EngineKind.OVERLAY)
        val controller = controller(storeFor(), overlay)
        controller.awaitLoaded()

        controller.select(Filters.Grayscale)
        controller.awaitRendered()
        val state = controller.state.value
        assertTrue(state.selectedNeedsFullMode)
        assertFalse(state.isAppliedSystemWide)
        assertTrue(overlay.shown.isEmpty())

        controller.select(Filters.Orange)
        controller.awaitRendered()
        assertTrue(overlay.shown.last() is Rendering.Tint)
    }

    @Test
    fun `the preferred mode is used whenever it can draw the filter`() = runBlocking {
        val shizuku = FakeEngine(EngineKind.SHIZUKU)
        val overlay = FakeEngine(EngineKind.OVERLAY)
        val controller = controller(storeFor(), shizuku, overlay)
        controller.awaitLoaded()
        controller.preferEngine(EngineKind.OVERLAY)

        controller.select(Filters.Orange)
        controller.awaitRendered()
        assertTrue(overlay.shown.last() is Rendering.Tint)
        assertTrue(shizuku.shown.isEmpty())

        // Grayscale needs a matrix: the overlay is cleared and Shizuku takes over.
        controller.select(Filters.Grayscale)
        controller.awaitRendered()
        assertNull(overlay.shown.last())
        assertEquals(matrixOf(Filters.Grayscale), shizuku.shown.last())
    }

    @Test
    fun `intensity changes reach the screen`() = runBlocking {
        val engine = FakeEngine(EngineKind.ROOT)
        val controller = controller(storeFor(), engine)
        controller.awaitLoaded()
        controller.select(Filters.Orange)
        controller.awaitRendered()

        controller.setIntensity(0.3f, save = false)
        controller.awaitRendered()

        assertEquals(matrixOf(Filters.Orange, 0.3f), engine.shown.last())
        assertEquals(0.3f, controller.state.value.intensity, 1e-4f)
    }

    @Test
    fun `quick toggle waits for Shizuku to hand over its binder`() = runBlocking {
        val engine = FakeEngine(EngineKind.SHIZUKU, ready = false)
        val controller = controller(storeFor(), engine)
        controller.awaitLoaded()

        val toggled = async { controller.quickToggle() }
        delay(300)
        engine.state.value = EngineState.READY

        assertTrue(toggled.await())
        controller.awaitRendered()
        assertTrue(controller.state.value.isAppliedSystemWide)
        assertEquals(matrixOf(Filters.Default), engine.shown.last())
    }

    @Test
    fun `quick toggle asks to open the app when nothing is set up`() = runBlocking {
        val controller = controller(storeFor(), FakeEngine(EngineKind.SHIZUKU, ready = false))
        controller.awaitLoaded()
        assertFalse(controller.quickToggle())
        assertFalse(controller.state.value.enabled)
    }

    @Test
    fun `reapply sends the current filter again without animation`() = runBlocking {
        val engine = FakeEngine(EngineKind.SHIZUKU)
        val controller = controller(storeFor(), engine)
        controller.awaitLoaded()
        controller.select(Filters.Redscale)
        controller.awaitRendered()
        engine.shown.clear()

        controller.reapplyIfOwned()
        controller.awaitRendered()

        assertEquals(listOf<Rendering?>(matrixOf(Filters.Redscale)), engine.shown.toList())
    }

    @Test
    fun `re-applying while switched off leaves the system colours alone`() = runBlocking {
        val engine = FakeEngine(EngineKind.SHIZUKU)
        val controller = controller(storeFor(), engine)
        controller.awaitLoaded()
        controller.select(Filters.Grayscale)
        controller.awaitRendered()
        controller.setEnabled(false)
        controller.awaitRendered()
        waitUntil { controller.state.value.settings.appliedEngine == null }
        engine.shown.clear()

        controller.retry(EngineKind.SHIZUKU)
        controller.awaitRendered()

        assertTrue(engine.shown.isEmpty())
    }

    @Test
    fun `engine errors are passed on to the UI state`() = runBlocking {
        val engine = FakeEngine(EngineKind.SHIZUKU)
        val controller = controller(storeFor(), engine)
        controller.awaitLoaded()

        engine.errorDetails.value = "SecurityException: denied"
        engine.state.value = EngineState.ERROR
        waitUntil { controller.state.value.engineErrors[EngineKind.SHIZUKU] == "SecurityException: denied" }

        engine.errorDetails.value = null
        waitUntil { controller.state.value.engineErrors.isEmpty() }
    }
}
