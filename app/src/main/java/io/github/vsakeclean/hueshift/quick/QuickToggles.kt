package io.github.vsakeclean.hueshift.quick

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.core.FilterController
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Shared helpers for the ways to toggle the filter without opening the app. */
object QuickToggles {

    private const val SHORTCUT_TOGGLE = "toggle"

    private data class Snapshot(
        val enabled: Boolean,
        val filterId: String,
        val applied: Boolean,
        val canToggle: Boolean,
    )

    /** Keeps the Quick Settings tile and the home screen widgets in sync with the app. */
    fun keepUpdated(context: Context, scope: CoroutineScope, controller: FilterController) {
        scope.launch {
            controller.state
                .filter { it.loaded }
                .map { Snapshot(it.enabled, it.selected.id, it.isAppliedSystemWide, it.canQuickToggle) }
                .distinctUntilChanged()
                .collect {
                    requestTileUpdate(context)
                    HueWidgetProvider.updateAll(context, controller.state.value)
                }
        }
    }

    fun requestTileUpdate(context: Context) {
        runCatching {
            TileService.requestListeningState(context, ComponentName(context, HueTileService::class.java))
        }
    }

    /** Adds "Toggle filter" to the menu that appears when long-pressing the launcher icon. */
    fun publishShortcut(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_TOGGLE)
            .setShortLabel(context.getString(R.string.shortcut_toggle_short))
            .setLongLabel(context.getString(R.string.shortcut_toggle_long))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_shortcut_toggle))
            .setIntent(Intent(context, ToggleActivity::class.java).setAction(ToggleActivity.ACTION_TOGGLE))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }

    fun openAppIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Short status text, e.g. "Grayscale" or "Off". */
    fun statusText(context: Context, state: HueState): String = when {
        state.enabled && state.isAppliedSystemWide -> context.getString(state.selected.title)
        !state.canQuickToggle -> context.getString(R.string.quick_needs_setup)
        else -> context.getString(R.string.quick_off)
    }

    /** Message shown after toggling from a shortcut. */
    fun feedback(context: Context, state: HueState): String =
        if (state.enabled) {
            context.getString(R.string.toast_filter_on, context.getString(state.selected.title))
        } else {
            context.getString(R.string.toast_filter_off)
        }
}
