package io.github.vsakeclean.hueshift.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.engine.Rendering
import io.github.vsakeclean.hueshift.filter.ColorTransform
import io.github.vsakeclean.hueshift.filter.HueFilter
import io.github.vsakeclean.hueshift.filter.OverlayTint
import kotlin.math.abs

/**
 * What the in-app preview draws by itself: a colour matrix and/or a tint on top.
 * [key] identifies the filter, so that intensity changes animate faster than filter changes.
 */
@Immutable
data class PreviewEffect(
    val key: String,
    val transform: ColorTransform? = null,
    val tint: OverlayTint? = null,
) {
    companion object {
        val None = PreviewEffect(key = "none")

        fun lerp(from: PreviewEffect, to: PreviewEffect, fraction: Float): PreviewEffect {
            if (fraction <= 0f) return from
            if (fraction >= 1f) return to
            val transform = if (from.transform == null && to.transform == null) {
                null
            } else {
                (from.transform ?: ColorTransform.Identity).lerp(to.transform ?: ColorTransform.Identity, fraction)
            }
            val tint = if (from.tint == null && to.tint == null) {
                null
            } else {
                (from.tint ?: OverlayTint.None).lerp(to.tint ?: OverlayTint.None, fraction)
            }
            return PreviewEffect(to.key, transform, tint)
        }

        /** How [filter] at [intensity] looks with the [engine] that would draw it. */
        fun of(filter: HueFilter, intensity: Float, engine: EngineKind?): PreviewEffect {
            if (filter.isOff) return None
            // Android's own features have no intensity: show them at full strength.
            filter.systemFeature?.takeIf { engine == EngineKind.SYSTEM }?.let {
                return PreviewEffect(filter.id, transform = it.transform)
            }
            val tint = if (engine == EngineKind.OVERLAY) filter.overlayTintAt(intensity) else null
            return if (tint != null) {
                PreviewEffect(filter.id, tint = tint)
            } else {
                PreviewEffect(filter.id, transform = filter.transformAt(intensity))
            }
        }

        /**
         * The effect the preview window has to draw itself. When the filter already runs
         * system-wide, the preview is recoloured together with the rest of the screen, so it
         * must show the plain picture (otherwise the filter would be applied twice).
         */
        fun forPreview(state: HueState): PreviewEffect = when {
            !state.loaded -> None
            // A different key makes the hand-over to the system effect use the long, synchronised animation.
            state.isAppliedSystemWide -> None.copy(key = state.effectiveFilter.id + "@system")
            else -> of(state.effectiveFilter, state.intensity, state.filterEngines[state.effectiveFilter.id])
        }
    }
}

/**
 * Turns "this is how the colour should look" into "draw this colour" while a system-wide
 * effect is active, so filter swatches keep showing their true look.
 *
 * Only effects that can be undone without losing colours are compensated (negative and
 * inverted colours). Darkening effects such as orange or grayscale cannot be undone on screen,
 * so the swatches are simply shown through them, like everything else.
 */
@Immutable
class ScreenCompensation private constructor(private val rendering: Rendering?) {

    private val inverse: ColorTransform? = when (rendering) {
        is Rendering.Matrix -> rendering.transform
        is Rendering.System -> rendering.feature.transform
        else -> null
    }
        ?.takeIf { abs(abs(it.determinant) - 1f) < 0.05f }
        ?.inverse()

    fun color(red: Float, green: Float, blue: Float): Color {
        val rgb = inverse?.apply(red, green, blue) ?: floatArrayOf(red, green, blue)
        return Color(rgb[0], rgb[1], rgb[2])
    }

    override fun equals(other: Any?): Boolean = other is ScreenCompensation && other.rendering == rendering

    override fun hashCode(): Int = rendering.hashCode()

    companion object {
        val None = ScreenCompensation(null)

        fun forState(state: HueState): ScreenCompensation =
            if (state.isAppliedSystemWide) ScreenCompensation(state.rendering) else None
    }
}

/** Rainbow used for the filter swatches. */
private val SwatchStops = listOf(
    floatArrayOf(1f, 0.18f, 0.25f),
    floatArrayOf(1f, 0.55f, 0.1f),
    floatArrayOf(1f, 0.88f, 0.15f),
    floatArrayOf(0.2f, 0.85f, 0.35f),
    floatArrayOf(0.1f, 0.8f, 0.95f),
    floatArrayOf(0.2f, 0.4f, 1f),
    floatArrayOf(0.62f, 0.3f, 1f),
    floatArrayOf(1f, 0.35f, 0.75f),
)

/** The rainbow as it looks with [filter] at [intensity], ready to be drawn under [compensation]. */
fun swatchColors(
    filter: HueFilter,
    intensity: Float,
    engine: EngineKind?,
    compensation: ScreenCompensation,
): List<Color> {
    val effect = PreviewEffect.of(filter, intensity, engine)
    return SwatchStops.map { stop ->
        var rgb = effect.transform?.apply(stop[0], stop[1], stop[2]) ?: stop
        effect.tint?.let { rgb = it.blendOver(rgb[0], rgb[1], rgb[2]) }
        compensation.color(rgb[0], rgb[1], rgb[2])
    }
}

/** Smoothly animates between preview effects. */
@Composable
fun animatePreviewEffect(target: PreviewEffect): State<PreviewEffect> {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        val subtle = target.key == to.key
        from = PreviewEffect.lerp(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(if (subtle) 140 else 480, easing = FastOutSlowInEasing))
    }
    return remember { derivedStateOf { PreviewEffect.lerp(from, to, progress.value) } }
}

/** Draws the content through [effect]; the lambda is read during drawing, so animations don't recompose. */
fun Modifier.previewEffect(effect: () -> PreviewEffect): Modifier = drawWithCache {
    val paint = Paint()
    onDrawWithContent {
        val current = effect()
        val transform = current.transform
        if (transform != null && !transform.isIdentity) {
            paint.colorFilter = ColorFilter.colorMatrix(ColorMatrix(transform.toColorMatrixValues()))
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                drawContent()
                canvas.restore()
            }
        } else {
            drawContent()
        }
        val tint = current.tint
        if (tint != null && tint.isVisible) drawRect(Color(tint.red, tint.green, tint.blue, tint.alpha))
    }
}
