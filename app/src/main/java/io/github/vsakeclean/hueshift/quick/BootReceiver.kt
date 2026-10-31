package io.github.vsakeclean.hueshift.quick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.vsakeclean.hueshift.graph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The SurfaceFlinger colour matrix is reset on reboot. Starting the app process is enough for
 * the controller to put the filter back (immediately with root, as soon as Shizuku is running
 * otherwise); this receiver only keeps the process alive while that happens.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                withTimeoutOrNull(8_000) {
                    val state = graph.controller.awaitLoaded()
                    if (state.enabled) {
                        graph.controller.state.first { it.isAppliedSystemWide }
                        graph.controller.awaitRendered()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
