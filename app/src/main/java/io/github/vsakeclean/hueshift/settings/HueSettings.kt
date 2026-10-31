package io.github.vsakeclean.hueshift.settings

import androidx.compose.runtime.Immutable
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.filter.Filters
import io.github.vsakeclean.hueshift.filter.HueFilter

/** Everything hue-shift remembers between launches. */
@Immutable
data class HueSettings(
    /** Whether the user wants the filter on. */
    val enabled: Boolean = false,
    /** Last chosen filter (never [Filters.Off]); it comes back when the filter is switched on again. */
    val filterId: String = Filters.Default.id,
    /** Intensity chosen for each filter, by filter id. */
    val intensities: Map<String, Float> = emptyMap(),
    /** Mode picked by the user; `null` picks the best available one automatically. */
    val enginePreference: EngineKind? = null,
    /** The user agreed to use root, so `su` may be called without asking again. */
    val rootAllowed: Boolean = false,
    /**
     * Bookkeeping: the engine that, as far as we know, still has a filter on screen. Used to clean
     * up after a mode switch or a process restart without touching system colours needlessly.
     */
    val appliedEngine: EngineKind? = null,
) {
    /** The selected filter (falls back to the default one for unknown ids). */
    val filter: HueFilter get() = Filters.byId(filterId)?.takeUnless { it.isOff } ?: Filters.Default

    /** The filter that should be on screen right now. */
    val effectiveFilter: HueFilter get() = if (enabled) filter else Filters.Off

    fun intensityOf(filter: HueFilter): Float =
        (intensities[filter.id] ?: filter.defaultIntensity).coerceIn(MIN_INTENSITY, 1f)

    companion object {
        /** Lowest value of the intensity slider (0 % would look exactly like "off"). */
        const val MIN_INTENSITY = 0.1f
    }
}
