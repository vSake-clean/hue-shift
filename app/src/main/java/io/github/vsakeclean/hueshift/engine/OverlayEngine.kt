package io.github.vsakeclean.hueshift.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Basic mode without Shizuku or root: [OverlayService] (an accessibility service) draws a
 * translucent colour above every app, the status bar and the navigation bar.
 * It can only tint, so it supports filters that define an overlay tint.
 */
class OverlayEngine : FilterEngine {

    override val kind = EngineKind.OVERLAY

    private val _state = MutableStateFlow(EngineState.NEEDS_PERMISSION)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile private var service: OverlayService? = null

    internal fun attach(service: OverlayService) {
        this.service = service
        _state.value = EngineState.READY
    }

    internal fun detach(service: OverlayService) {
        if (this.service === service) {
            this.service = null
            _state.value = EngineState.NEEDS_PERMISSION
        }
    }

    override suspend fun show(rendering: Rendering?) = withContext(Dispatchers.Main.immediate) {
        val tint = (rendering as? Rendering.Tint)?.tint
        val current = service
        if (current != null) {
            current.showTint(tint)
        } else {
            // Without the service there is no overlay on screen, so "nothing" is already shown.
            check(tint == null) { "The overlay service is not running" }
        }
    }
}
