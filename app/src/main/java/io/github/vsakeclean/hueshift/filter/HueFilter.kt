package io.github.vsakeclean.hueshift.filter

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A colour filter that hue-shift can put over the whole screen.
 *
 * Adding a new filter only takes a new instance in [Filters] (plus its two strings).
 * The card, the live preview, the intensity slider, persistence, the Quick Settings tile
 * and the widget pick it up automatically.
 */
@Immutable
class HueFilter(
    /** Stable identifier, stored in the settings. Never change it once released. */
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    /** Icon shown on the filter card (a lambda, so the filter list stays cheap to create). */
    val icon: () -> ImageVector,
    /** ARGB accent colour of the card. */
    val accent: Long,
    /** Whether an intensity slider makes sense for this filter. */
    val supportsIntensity: Boolean = false,
    /** Intensity used until the user picks their own (`0..1`). */
    val defaultIntensity: Float = 1f,
    /** The exact colour transform at a given intensity (`0..1`). */
    private val transform: (intensity: Float) -> ColorTransform,
    /**
     * Optional approximation for the overlay mode, which can only lay a translucent colour on top
     * of the screen. Filters without it require Shizuku or root.
     */
    private val overlayTint: ((intensity: Float) -> OverlayTint)? = null,
    /** The built-in Android feature with the same look, if there is one (always at full strength). */
    val systemFeature: SystemFeature? = null,
) {
    val isOff: Boolean get() = id == Filters.OFF_ID

    val supportsOverlay: Boolean get() = overlayTint != null

    fun transformAt(intensity: Float): ColorTransform = transform(effectiveIntensity(intensity))

    fun overlayTintAt(intensity: Float): OverlayTint? = overlayTint?.invoke(effectiveIntensity(intensity))

    private fun effectiveIntensity(intensity: Float): Float =
        if (supportsIntensity) intensity.coerceIn(0f, 1f) else 1f

    override fun equals(other: Any?): Boolean = other is HueFilter && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "HueFilter($id)"
}

/**
 * A translucent colour drawn on top of the whole screen (source-over blending),
 * used when hue-shift runs in overlay mode.
 */
@Immutable
data class OverlayTint(val red: Float, val green: Float, val blue: Float, val alpha: Float) {

    val isVisible: Boolean get() = alpha > 0.002f

    fun lerp(target: OverlayTint, fraction: Float): OverlayTint {
        if (fraction <= 0f) return this
        if (fraction >= 1f) return target
        // Fading from/to "nothing" keeps the colour and only changes the opacity.
        val from = if (isVisible) this else target.copy(alpha = 0f)
        val to = if (target.isVisible) target else copy(alpha = 0f)
        return OverlayTint(
            red = from.red + (to.red - from.red) * fraction,
            green = from.green + (to.green - from.green) * fraction,
            blue = from.blue + (to.blue - from.blue) * fraction,
            alpha = from.alpha + (to.alpha - from.alpha) * fraction,
        )
    }

    /** The colour of a pixel ([red], [green], [blue]) once this tint is drawn over it. */
    fun blendOver(red: Float, green: Float, blue: Float): FloatArray = floatArrayOf(
        red + (this.red - red) * alpha,
        green + (this.green - green) * alpha,
        blue + (this.blue - blue) * alpha,
    )

    /** Undoes [blendOver] as far as possible (results are clamped to `0..1`). */
    fun unblend(red: Float, green: Float, blue: Float): FloatArray {
        if (alpha >= 0.999f) return floatArrayOf(red, green, blue)
        val keep = 1f - alpha
        return floatArrayOf(
            ((red - this.red * alpha) / keep).coerceIn(0f, 1f),
            ((green - this.green * alpha) / keep).coerceIn(0f, 1f),
            ((blue - this.blue * alpha) / keep).coerceIn(0f, 1f),
        )
    }

    fun toArgb(): Int {
        fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }

    companion object {
        val None = OverlayTint(0f, 0f, 0f, 0f)
    }
}
