package io.github.vsakeclean.hueshift.core

import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.engine.EngineState
import io.github.vsakeclean.hueshift.filter.Filters
import io.github.vsakeclean.hueshift.filter.HueFilter
import io.github.vsakeclean.hueshift.settings.HueSettings

/**
 * Derives the full [HueState] from the settings and the state of each engine.
 * [engines] lists the available engine kinds, best quality first.
 */
fun buildHueState(
    settings: HueSettings,
    states: Map<EngineKind, EngineState>,
    engines: List<EngineKind> = EngineKind.entries,
    errors: Map<EngineKind, String> = emptyMap(),
): HueState {
    fun engineFor(filter: HueFilter): EngineKind? {
        val candidates = engines.filter { states[it]?.isReady == true && it.supports(filter) }
        return candidates.firstOrNull { it == settings.enginePreference } ?: candidates.firstOrNull()
    }

    val selected = settings.filter
    val engineForSelected = engineFor(selected)
    val bestEngine = settings.enginePreference?.takeIf { states[it]?.isReady == true }
        ?: engines.firstOrNull { states[it]?.isReady == true }
    val rendering = if (settings.enabled) {
        engineForSelected?.renderingFor(selected, settings.intensityOf(selected))
    } else {
        null
    }
    return HueState(
        loaded = true,
        settings = settings,
        engineStates = states,
        engineForSelected = engineForSelected,
        bestEngine = bestEngine,
        rendering = rendering,
        renderingEngine = if (rendering != null) engineForSelected else null,
        filterEngines = buildMap {
            Filters.all.forEach { filter -> engineFor(filter)?.let { put(filter.id, it) } }
        },
        engineErrors = errors,
    )
}
