package io.github.vsakeclean.hueshift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.core.buildHueState
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.engine.EngineState
import io.github.vsakeclean.hueshift.filter.Filters
import io.github.vsakeclean.hueshift.filter.HueFilter
import io.github.vsakeclean.hueshift.settings.HueSettings
import io.github.vsakeclean.hueshift.ui.theme.HueColors
import io.github.vsakeclean.hueshift.ui.theme.HueShiftTheme

/** Ready-made states for Android Studio previews and the README screenshots. */
object SampleStates {

    private val nothingSetUp = mapOf(
        EngineKind.SHIZUKU to EngineState.NOT_RUNNING,
        EngineKind.ROOT to EngineState.NEEDS_PERMISSION,
        EngineKind.OVERLAY to EngineState.NEEDS_PERMISSION,
    )

    private val shizukuReady = nothingSetUp + (EngineKind.SHIZUKU to EngineState.READY)

    /** First launch: no mode set up yet, filter off. */
    fun firstStart(): HueState = buildHueState(HueSettings(), nothingSetUp)

    /** A filter picked before any mode was set up: only the preview shows it. */
    fun previewOnly(filter: HueFilter): HueState =
        buildHueState(HueSettings(enabled = true, filterId = filter.id), nothingSetUp)

    /** A filter running on the whole screen through Shizuku. */
    fun live(filter: HueFilter, intensity: Float? = null): HueState = buildHueState(
        HueSettings(
            enabled = !filter.isOff,
            filterId = if (filter.isOff) Filters.Default.id else filter.id,
            intensities = intensity?.let { mapOf(filter.id to it) } ?: emptyMap(),
        ),
        shizukuReady,
    )

    /** What the setup sheet shows while nothing is ready. */
    fun setup(): HueState = firstStart()
}

@Preview(name = "First start", widthDp = 411, heightDp = 1400, locale = "pl")
@Composable
private fun FirstStartPreview() {
    HueShiftTheme { MainScreen(SampleStates.firstStart(), MainActions()) }
}

@Preview(name = "Orange, live", widthDp = 411, heightDp = 1400)
@Composable
private fun OrangePreview() {
    HueShiftTheme { MainScreen(SampleStates.live(Filters.Orange, 0.7f), MainActions()) }
}

@Preview(name = "Setup", widthDp = 411, heightDp = 1300)
@Composable
private fun SetupPreview() {
    HueShiftTheme {
        Box(Modifier.background(HueColors.Surface)) { SetupContent(SampleStates.setup(), MainActions()) }
    }
}
