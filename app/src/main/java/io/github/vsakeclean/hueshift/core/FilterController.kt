package io.github.vsakeclean.hueshift.core

import android.util.Log
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.engine.EngineState
import io.github.vsakeclean.hueshift.engine.FilterEngine
import io.github.vsakeclean.hueshift.engine.Rendering
import io.github.vsakeclean.hueshift.engine.RootEngine
import io.github.vsakeclean.hueshift.engine.ShizukuEngine
import io.github.vsakeclean.hueshift.engine.SystemSettingsEngine
import io.github.vsakeclean.hueshift.filter.HueFilter
import io.github.vsakeclean.hueshift.settings.HueSettings
import io.github.vsakeclean.hueshift.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/**
 * The heart of hue-shift: keeps the settings, picks the engine and puts the chosen filter on
 * screen with a smooth, system-wide transition. It has no Android dependencies of its own,
 * which keeps it unit-testable with fake engines.
 *
 * It lives as long as the app process, so the activity, the Quick Settings tile, the widget,
 * the launcher shortcut and the boot receiver all share the same state.
 */
class FilterController(
    private val scope: CoroutineScope,
    private val store: SettingsStore,
    /** All engines, best quality first. */
    val engines: List<FilterEngine>,
) {

    private val shizuku: ShizukuEngine? get() = engines.firstNotNullOfOrNull { it as? ShizukuEngine }
    private val root: RootEngine? get() = engines.firstNotNullOfOrNull { it as? RootEngine }
    private val system: SystemSettingsEngine? get() = engines.firstNotNullOfOrNull { it as? SystemSettingsEngine }

    private val settings = MutableStateFlow<HueSettings?>(null)
    private val saveRequests = Channel<HueSettings>(Channel.CONFLATED)
    private val reapplyRequests = MutableStateFlow(0)
    private var reapplyJob: Job? = null
    private var started = false

    private val engineStates: Flow<Map<EngineKind, EngineState>> =
        combine(engines.map { engine -> engine.state.map { engine.kind to it } }) { it.toMap() }

    private val engineErrors: Flow<Map<EngineKind, String>> =
        combine(engines.map { engine -> engine.errorDetails.map { engine.kind to it } }) { pairs ->
            pairs.mapNotNull { (kind, error) -> error?.let { kind to it } }.toMap()
        }

    val state: StateFlow<HueState> = combine(settings.filterNotNull(), engineStates, engineErrors, ::buildState)
        .stateIn(scope, SharingStarted.Eagerly, HueState.Initial)

    fun start() {
        if (started) return
        started = true
        engines.forEach { it.start() }
        scope.launch {
            val loaded = store.load()
            settings.value = loaded
            if (loaded.rootAllowed) root?.connect()
        }
        scope.launch {
            for (snapshot in saveRequests) {
                runCatching { store.save(snapshot) }.onFailure { Log.w(TAG, "Could not save settings", it) }
            }
        }
        scope.launch(Dispatchers.Default) { render() }
        scope.launch { grantSystemFeaturesWhenShizukuIsRefused() }
    }

    /**
     * When the phone refuses the colour matrix even to Shizuku (e.g. Xiaomi with HyperOS), Shizuku
     * can still give hue-shift the permission for Android's own grayscale and colour inversion.
     * Do that once by ourselves instead of sending the user to yet another button.
     */
    private suspend fun grantSystemFeaturesWhenShizukuIsRefused() {
        val shizuku = shizuku ?: return
        val system = system ?: return
        state.first { current ->
            current.loaded &&
                current.stateOf(EngineKind.SYSTEM) == EngineState.NEEDS_PERMISSION &&
                current.stateOf(EngineKind.SHIZUKU) == EngineState.ERROR &&
                shizuku.isRefused && shizuku.isAuthorized
        }
        system.grantWithShizuku()
    }

    // region Actions

    /** Picks a filter and switches it on; picking "off" switches the filter off. */
    fun select(filter: HueFilter) = edit {
        if (filter.isOff) it.copy(enabled = false) else it.copy(filterId = filter.id, enabled = true)
    }

    fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

    fun toggle() = edit { it.copy(enabled = !it.enabled) }

    /** Changes the intensity of the selected filter. Use `save = false` while the slider is dragged. */
    fun setIntensity(value: Float, save: Boolean = true) = edit(save) {
        val clamped = value.coerceIn(HueSettings.MIN_INTENSITY, 1f)
        it.copy(intensities = it.intensities + (it.filter.id to clamped))
    }

    /** Saves the current settings (e.g. when the user lets go of the slider). */
    fun commit() {
        settings.value?.let { saveRequests.trySend(it) }
    }

    /** Uses [kind] when it is ready; `null` goes back to picking the best mode automatically. */
    fun preferEngine(kind: EngineKind?) = edit { it.copy(enginePreference = kind) }

    fun requestShizukuPermission() {
        shizuku?.requestPermission()
    }

    /** Asks for root. Returns whether it was granted. */
    suspend fun enableRoot(): Boolean {
        val granted = root?.connect() == true
        edit { it.copy(rootAllowed = granted, enginePreference = if (granted) EngineKind.ROOT else it.enginePreference) }
        return granted
    }

    /** Gives hue-shift the permission to switch the built-in colour features, using Shizuku. */
    suspend fun grantSystemFeatures(): Boolean = system?.grantWithShizuku() == true

    fun retry(kind: EngineKind) {
        when (kind) {
            EngineKind.SHIZUKU -> shizuku?.retry()
            EngineKind.ROOT -> scope.launch { root?.connect() }
            EngineKind.SYSTEM -> system?.refresh()
            EngineKind.OVERLAY -> Unit
        }
        reapply()
    }

    fun refreshEngines() = engines.forEach { it.refresh() }

    /** Sends the current filter again (some system features reset the colour matrix). */
    fun reapply() = reapplyRequests.update { it + 1 }

    /** [reapply], but only when hue-shift currently owns the system colour matrix. */
    fun reapplyIfOwned() {
        val current = state.value
        if (current.isAppliedSystemWide && current.renderingEngine?.appliesMatrix == true) reapply()
    }

    suspend fun awaitLoaded(): HueState = state.first { it.loaded }

    /**
     * Toggles the filter from a tile, widget or shortcut. Returns `false` when there is nothing
     * that could change the screen yet, so the caller should open the app instead.
     */
    suspend fun quickToggle(): Boolean {
        val current = awaitLoaded()
        // Right after a cold start Shizuku needs a moment to hand over its binder.
        val ready = if (current.canQuickToggle) {
            current
        } else {
            withTimeoutOrNull(QUICK_TOGGLE_WAIT_MS) { state.first { it.canQuickToggle } }
        }
        if (ready == null) return false
        toggle()
        return true
    }

    // endregion

    private fun edit(save: Boolean = true, change: (HueSettings) -> HueSettings) {
        if (settings.value == null) {
            // Not loaded yet (cold start from a tile or widget): apply once loading is done.
            scope.launch {
                settings.filterNotNull().first()
                edit(save, change)
            }
            return
        }
        val updated = settings.updateAndGet { current -> current?.let(change) } ?: return
        if (save) saveRequests.trySend(updated)
    }

    private fun engine(kind: EngineKind): FilterEngine = engines.first { it.kind == kind }

    private fun buildState(
        settings: HueSettings,
        states: Map<EngineKind, EngineState>,
        errors: Map<EngineKind, String>,
    ): HueState = buildHueState(settings, states, engines.map { it.kind }, errors)

    // region Rendering

    private data class RenderTarget(
        val engine: EngineKind?,
        val rendering: Rendering?,
        val filterId: String,
        val enabled: Boolean,
        val readyEngines: Set<EngineKind>,
        val reapply: Int,
    )

    // Only touched by the render coroutine.
    private var onScreenEngine: EngineKind? = null
    private var onScreen: Rendering? = null
    private var previousTarget: RenderTarget? = null

    private val latestTargetIndex = MutableStateFlow(-1)
    private val renderedTargetIndex = MutableStateFlow(-1)

    /**
     * Suspends until the latest change has reached the screen (or [timeoutMs] passed). Receivers
     * use it so that Android does not freeze the process in the middle of a transition.
     */
    suspend fun awaitRendered(timeoutMs: Long = 3_000) {
        withTimeoutOrNull(timeoutMs) {
            delay(50) // let the latest settings change travel through the state flows
            combine(latestTargetIndex, renderedTargetIndex) { latest, rendered -> rendered >= latest }
                .first { it }
        }
    }

    private suspend fun render() {
        combine(state.filter { it.loaded }, reapplyRequests) { current, tick ->
            RenderTarget(
                // While switched off, keep the engine so that turning off fades out smoothly.
                engine = current.renderingEngine ?: current.engineForSelected?.takeIf { !current.enabled },
                rendering = current.rendering,
                filterId = current.selected.id,
                enabled = current.enabled,
                readyEngines = current.engineStates.filterValues { it.isReady }.keys,
                reapply = tick,
            )
        }
            .distinctUntilChanged()
            .withIndex()
            .onEach { latestTargetIndex.value = it.index }
            .collectLatest { (index, target) ->
                val last = previousTarget
                previousTarget = target
                renderTarget(target, last)
                renderedTargetIndex.value = index
            }
    }

    private suspend fun renderTarget(target: RenderTarget, last: RenderTarget?) {
        // An overlay disappears together with its service: forget about it.
        if (EngineKind.OVERLAY !in target.readyEngines) {
            if (onScreenEngine == EngineKind.OVERLAY) {
                onScreenEngine = null
                onScreen = null
            }
            if (settings.value?.appliedEngine == EngineKind.OVERLAY) recordApplied(null)
        }

        // Remove the effect from engines that should no longer draw anything.
        val applied = settings.value?.appliedEngine
        for (kind in setOfNotNull(applied, onScreenEngine)) {
            if (kind == target.engine || kind !in target.readyEngines) continue
            val cleared = runCatching { engine(kind).show(null) }.isSuccess
            if (cleared) {
                if (onScreenEngine == kind) {
                    onScreenEngine = null
                    onScreen = null
                }
                if (settings.value?.appliedEngine == kind) recordApplied(null)
            }
        }

        val kind = target.engine ?: return
        val unchanged = last != null && last.engine == kind && last.rendering == target.rendering &&
            last.reapply == target.reapply
        if (unchanged && onScreenEngine == kind) return
        // Off and nothing of ours on screen: leave the system colours (e.g. Night Light) alone.
        val nothingOfOursShown = (onScreenEngine != kind || onScreen == null) && settings.value?.appliedEngine != kind
        if (target.rendering == null && nothingOfOursShown) return

        val engine = engine(kind)
        try {
            // Animate from what is on screen when we know it; otherwise (re-apply, or a filter
            // left over by a previous process) set the target directly.
            val start: Rendering?
            val animated = when {
                last != null && last.reapply != target.reapply -> {
                    start = null
                    false
                }
                onScreenEngine == kind -> {
                    start = onScreen
                    true
                }
                settings.value?.appliedEngine != kind -> {
                    start = null
                    true
                }
                else -> {
                    start = null
                    false
                }
            }
            // Remember early that this engine draws something, in case the process dies mid-way.
            if (target.rendering != null) recordApplied(kind)
            if (animated) {
                val subtle = last != null && last.filterId == target.filterId && last.enabled == target.enabled
                val duration = if (subtle) SHORT_ANIMATION_MS else LONG_ANIMATION_MS
                onScreenEngine = kind
                onScreen = start
                animate(engine, start, target.rendering, duration) { onScreen = it }
            } else {
                engine.show(target.rendering)
            }
            onScreenEngine = kind
            onScreen = target.rendering
            if (target.rendering == null) recordApplied(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply the filter with $kind", e)
            engine.refresh()
        }
    }

    private suspend fun animate(
        engine: FilterEngine,
        from: Rendering?,
        to: Rendering?,
        durationMs: Long,
        onFrame: (Rendering?) -> Unit,
    ) {
        // Built-in system features switch at once: no frames to send.
        if (from == to || from is Rendering.System || to is Rendering.System) {
            engine.show(to)
            onFrame(to)
            return
        }
        val start = System.nanoTime()
        while (true) {
            val elapsedMs = (System.nanoTime() - start) / 1_000_000f
            val fraction = (elapsedMs / durationMs).coerceIn(0f, 1f)
            val frame = Rendering.lerp(from, to, easeInOutCubic(fraction))
            engine.show(frame)
            onFrame(frame)
            if (fraction >= 1f) return
            if (engine.frameIntervalMillis > 0) delay(engine.frameIntervalMillis) else yield()
        }
    }

    private fun recordApplied(kind: EngineKind?) {
        if (settings.value?.appliedEngine != kind) edit { it.copy(appliedEngine = kind) }
    }

    // endregion

    /**
     * Night Light, colour correction and friends use the same SurfaceFlinger matrix. When the
     * system changes one of them while a hue-shift filter is active, put our filter back.
     */
    fun onSystemColoursChanged() {
        val current = state.value
        if (!current.isAppliedSystemWide || current.renderingEngine?.appliesMatrix != true) return
        reapplyJob?.cancel()
        reapplyJob = scope.launch {
            delay(500)
            reapply()
            // Night Light fades in over a few seconds, re-apply once it is done.
            delay(3_500)
            reapply()
        }
    }

    private companion object {
        const val TAG = "HueShift"
        const val LONG_ANIMATION_MS = 450L
        const val SHORT_ANIMATION_MS = 120L
        const val QUICK_TOGGLE_WAIT_MS = 2_500L

        fun easeInOutCubic(t: Float): Float =
            if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }
}
