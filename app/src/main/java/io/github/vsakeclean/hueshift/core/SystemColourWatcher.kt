package io.github.vsakeclean.hueshift.core

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Watches the system features that share the SurfaceFlinger colour matrix with hue-shift
 * (Night Light, colour inversion, colour correction, Extra dim, white balance) and tells the
 * controller when one of them changes.
 */
class SystemColourWatcher(private val context: Context, private val controller: FilterController) {

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = controller.onSystemColoursChanged()
    }

    fun start() {
        WATCHED_SETTINGS.forEach { name ->
            runCatching {
                context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(name), false, observer)
            }
        }
    }

    private companion object {
        val WATCHED_SETTINGS = listOf(
            "night_display_activated",
            "night_display_color_temperature",
            "accessibility_display_inversion_enabled",
            "accessibility_display_daltonizer_enabled",
            "accessibility_display_daltonizer",
            "reduce_bright_colors_activated",
            "reduce_bright_colors_level",
            "display_white_balance_enabled",
        )
    }
}
