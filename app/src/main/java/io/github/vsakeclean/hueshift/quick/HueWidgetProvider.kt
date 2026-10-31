package io.github.vsakeclean.hueshift.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.graph
import kotlinx.coroutines.launch

/** Home screen widget showing the current filter; a tap switches it on or off. */
class HueWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                update(context, manager, appWidgetIds, graph.controller.awaitLoaded())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        fun updateAll(context: Context, state: HueState) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, HueWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids, state)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray, state: HueState) {
            val active = state.enabled && state.isAppliedSystemWide
            val views = RemoteViews(context.packageName, R.layout.widget_hue).apply {
                setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                setTextViewText(R.id.widget_subtitle, QuickToggles.statusText(context, state))
                setInt(
                    R.id.widget_icon,
                    "setBackgroundResource",
                    if (active) R.drawable.widget_icon_bg_on else R.drawable.widget_icon_bg_off,
                )
                setOnClickPendingIntent(R.id.widget_root, clickIntent(context, state))
            }
            runCatching { manager.updateAppWidget(ids, views) }
        }

        private fun clickIntent(context: Context, state: HueState): PendingIntent =
            if (state.canQuickToggle) {
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, ToggleReceiver::class.java).setAction(ToggleReceiver.ACTION_TOGGLE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getActivity(
                    context,
                    1,
                    QuickToggles.openAppIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
    }
}
