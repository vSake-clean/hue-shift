package io.github.vsakeclean.hueshift.engine

import androidx.compose.runtime.Immutable
import io.github.vsakeclean.hueshift.filter.ColorTransform
import io.github.vsakeclean.hueshift.filter.HueFilter
import io.github.vsakeclean.hueshift.filter.OverlayTint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The ways hue-shift can change the colours of the whole screen, best quality first. */
enum class EngineKind {
    /** SurfaceFlinger colour matrix, sent with shell privileges through Shizuku (or Sui). */
    SHIZUKU,

    /** SurfaceFlinger colour matrix, sent through `su`. */
    ROOT,

    /** Translucent colour layer drawn by an accessibility service; only warm tints. */
    OVERLAY,
    ;

    /** True for engines that apply exact colour matrices (and therefore support every filter). */
    val appliesMatrix: Boolean get() = this != OVERLAY

    fun supports(filter: HueFilter): Boolean = appliesMatrix || filter.supportsOverlay

    /** How this kind of engine draws [filter] at [intensity], or `null` if it cannot. */
    fun renderingFor(filter: HueFilter, intensity: Float): Rendering? =
        if (appliesMatrix) {
            Rendering.Matrix(filter.transformAt(intensity))
        } else {
            filter.overlayTintAt(intensity)?.let(Rendering::Tint)
        }
}

enum class EngineState {
    /** Ready to change colours. */
    READY,

    /** Shizuku app is not installed. */
    NOT_INSTALLED,

    /** Shizuku is installed but its service is not running. */
    NOT_RUNNING,

    /** The user still has to grant access (Shizuku permission, root, accessibility service). */
    NEEDS_PERMISSION,

    /** Access was refused (Shizuku "deny forever", no root). */
    DENIED,

    /** The installed Shizuku version is too old. */
    UNSUPPORTED,

    /** Access is granted, but the system rejected the last change. */
    ERROR,
    ;

    val isReady: Boolean get() = this == READY
}

/** What an engine actually puts on the screen. */
@Immutable
sealed interface Rendering {

    @Immutable
    data class Matrix(val transform: ColorTransform) : Rendering

    @Immutable
    data class Tint(val tint: OverlayTint) : Rendering

    companion object {
        /**
         * Interpolates between two renderings of the same kind (`null` meaning "no effect"),
         * used for smooth, system-wide transitions.
         */
        fun lerp(from: Rendering?, to: Rendering?, fraction: Float): Rendering? {
            if (fraction >= 1f || from == to) return to
            if (from == null && to == null) return null
            return when {
                (from == null || from is Matrix) && (to == null || to is Matrix) -> {
                    val start = (from as Matrix?)?.transform ?: ColorTransform.Identity
                    val end = (to as Matrix?)?.transform ?: ColorTransform.Identity
                    Matrix(start.lerp(end, fraction))
                }
                (from == null || from is Tint) && (to == null || to is Tint) -> {
                    val start = (from as Tint?)?.tint ?: OverlayTint.None
                    val end = (to as Tint?)?.tint ?: OverlayTint.None
                    Tint(start.lerp(end, fraction))
                }
                else -> to
            }
        }
    }
}

/** Something that can show a [Rendering] over the whole screen. */
interface FilterEngine {
    val kind: EngineKind

    val state: StateFlow<EngineState>

    /** Pause between animation frames. Engines whose [show] is slow can return 0. */
    val frameIntervalMillis: Long get() = 16L

    /** Human-readable reason of the last failure, shown in the setup sheet. */
    val errorDetails: StateFlow<String?> get() = NoErrorDetails

    /** Puts [rendering] on screen (`null` removes any effect). Throws if the system refused. */
    suspend fun show(rendering: Rendering?)

    /** Called once when the app process starts. */
    fun start() {}

    /** Re-checks availability (e.g. after returning from the Shizuku app). */
    fun refresh() {}
}

private val NoErrorDetails: StateFlow<String?> = MutableStateFlow(null)
