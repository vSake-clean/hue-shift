package io.github.vsakeclean.hueshift.quick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.vsakeclean.hueshift.graph
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Receives taps on the widget. */
class ToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val pending = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                withTimeoutOrNull(8_000) {
                    if (graph.controller.quickToggle()) graph.controller.awaitRendered()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "io.github.vsakeclean.hueshift.action.TOGGLE"
    }
}
