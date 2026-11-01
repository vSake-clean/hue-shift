package io.github.vsakeclean.hueshift.core

import androidx.compose.runtime.Immutable
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.engine.EngineState
import io.github.vsakeclean.hueshift.engine.Rendering
import io.github.vsakeclean.hueshift.filter.HueFilter
import io.github.vsakeclean.hueshift.settings.HueSettings

/** Snapshot of everything the UI, the tile and the widget need to know. */
@Immutable
data class HueState(
    val loaded: Boolean,
    val settings: HueSettings,
    val engineStates: Map<EngineKind, EngineState>,
    /** Engine that would draw the selected filter (ready and able to), whether it is on or not. */
    val engineForSelected: EngineKind?,
    /** Best engine that is ready at all; drives the "how it works" card. */
    val bestEngine: EngineKind?,
    /** What should be on screen right now; `null` when nothing is drawn. */
    val rendering: Rendering?,
    /** Engine drawing [rendering]. */
    val renderingEngine: EngineKind?,
    /** For every filter id: the engine that would draw it (filters without one are missing). */
    val filterEngines: Map<String, EngineKind> = emptyMap(),
    /** Why an engine failed, for the setup sheet. */
    val engineErrors: Map<EngineKind, String> = emptyMap(),
) {
    val enabled: Boolean get() = settings.enabled

    /** Last chosen filter (also while the filter is switched off). */
    val selected: HueFilter get() = settings.filter

    val intensity: Float get() = settings.intensityOf(selected)

    /** [selected] when switched on, otherwise the "off" filter. */
    val effectiveFilter: HueFilter get() = settings.effectiveFilter

    /** The filter is on and really changes the colours of the whole screen. */
    val isAppliedSystemWide: Boolean get() = rendering != null && renderingEngine != null

    /** No mode is set up yet, so nothing can change the system colours. */
    val needsSetup: Boolean get() = bestEngine == null

    /** A mode is ready, but not one that can draw the selected filter (e.g. grayscale in overlay mode). */
    val selectedNeedsFullMode: Boolean get() = engineForSelected == null && bestEngine != null

    /** A quick toggle (tile, widget, shortcut) can do its job without opening the app. */
    val canQuickToggle: Boolean get() = engineForSelected != null || isAppliedSystemWide

    fun stateOf(kind: EngineKind): EngineState = engineStates[kind] ?: EngineState.NEEDS_PERMISSION

    companion object {
        val Initial = HueState(
            loaded = false,
            settings = HueSettings(),
            engineStates = emptyMap(),
            engineForSelected = null,
            bestEngine = null,
            rendering = null,
            renderingEngine = null,
        )
    }
}
