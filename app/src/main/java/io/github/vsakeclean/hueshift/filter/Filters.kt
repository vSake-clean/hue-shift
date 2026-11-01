package io.github.vsakeclean.hueshift.filter

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.WbTwilight
import io.github.vsakeclean.hueshift.R

/**
 * Every filter hue-shift knows about.
 *
 * ### Adding a filter
 * 1. Add two strings (`filter_xyz` and `filter_xyz_description`) to `values/strings.xml`
 *    and `values-pl/strings.xml`.
 * 2. Declare it below, e.g.
 *    ```
 *    val Sepia = HueFilter(
 *        id = "sepia",
 *        title = R.string.filter_sepia,
 *        description = R.string.filter_sepia_description,
 *        icon = { Icons.Rounded.Photo },
 *        accent = 0xFFC8A27A,
 *        supportsIntensity = true,
 *        transform = { t -> ColorTransform.Identity.lerp(ColorTransform.luminance(1.07f, 0.74f, 0.43f), t) },
 *    )
 *    ```
 * 3. Add it to [all]. Done — it gets a card, preview, slider, tile and widget support.
 */
object Filters {

    const val OFF_ID = "off"

    private val GrayscaleFull = ColorTransform.luminance(1f, 1f, 1f)
    private val RedscaleFull = ColorTransform.luminance(1f, 0f, 0f)
    private val InvertedFull = ColorTransform.luminanceInversion(Luma.Rec601)
    private val NegativeFull = ColorTransform.negative()

    /** Removes all colour, like Android's own grayscale mode (but with adjustable strength). */
    val Grayscale = HueFilter(
        id = "grayscale",
        title = R.string.filter_grayscale,
        description = R.string.filter_grayscale_description,
        icon = { Icons.Rounded.Contrast },
        accent = 0xFFC9D1E8,
        supportsIntensity = true,
        defaultIntensity = 1f,
        transform = { t -> ColorTransform.Identity.lerp(GrayscaleFull, t) },
        systemFeature = SystemFeature.GRAYSCALE,
    )

    /** Warm, blue-light-free orange: blue is removed first, then part of the green. */
    val Orange = HueFilter(
        id = "orange",
        title = R.string.filter_orange,
        description = R.string.filter_orange_description,
        icon = { Icons.Rounded.WbTwilight },
        accent = 0xFFFF9F43,
        supportsIntensity = true,
        defaultIntensity = 0.6f,
        transform = { t -> ColorTransform.scale(1f, 1f - 0.48f * t, 1f - 0.9f * t) },
        overlayTint = { t -> OverlayTint(1f, 0.52f, 0.06f, alpha = 0.5f * t) },
    )

    /** Dark becomes light and light becomes dark, while colours keep their hue. */
    val Inverted = HueFilter(
        id = "inverted",
        title = R.string.filter_inverted,
        description = R.string.filter_inverted_description,
        icon = { Icons.Rounded.InvertColors },
        accent = 0xFF4FD1FF,
        transform = { InvertedFull },
        systemFeature = SystemFeature.INVERSION,
    )

    /** Photographic negative ("minus colours"): every colour turns into its opposite. */
    val Negative = HueFilter(
        id = "negative",
        title = R.string.filter_negative,
        description = R.string.filter_negative_description,
        icon = { Icons.Rounded.Exposure },
        accent = 0xFFB794F6,
        transform = { NegativeFull },
    )

    /** Like grayscale, but in shades of red – easy on the eyes at night. */
    val Redscale = HueFilter(
        id = "redscale",
        title = R.string.filter_redscale,
        description = R.string.filter_redscale_description,
        icon = { Icons.Rounded.Nightlight },
        accent = 0xFFFF5370,
        supportsIntensity = true,
        defaultIntensity = 1f,
        transform = { t -> ColorTransform.Identity.lerp(RedscaleFull, t) },
        overlayTint = { t -> OverlayTint(1f, 0f, 0f, alpha = 0.45f * t) },
    )

    /** No filter: normal colours. */
    val Off = HueFilter(
        id = OFF_ID,
        title = R.string.filter_off,
        description = R.string.filter_off_description,
        icon = { Icons.Rounded.FormatColorReset },
        accent = 0xFF8E93A6,
        transform = { ColorTransform.Identity },
    )

    /** All filters, in the order they appear in the app. */
    val all: List<HueFilter> = listOf(Grayscale, Orange, Inverted, Negative, Redscale, Off)

    /** Filter chosen on the very first start. */
    val Default: HueFilter = Grayscale

    fun byId(id: String?): HueFilter? = all.firstOrNull { it.id == id }
}
