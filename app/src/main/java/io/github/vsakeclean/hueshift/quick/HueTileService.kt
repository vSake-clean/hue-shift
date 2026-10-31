package io.github.vsakeclean.hueshift.quick

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.graph
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Quick Settings tile: one tap switches the last used filter on or off. */
class HueTileService : TileService() {

    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val graph = graph
        listening?.cancel()
        listening = graph.scope.launch { graph.controller.state.collect(::updateTile) }
        // A good moment to put our filter back if a system feature reset the colours.
        graph.controller.reapplyIfOwned()
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val graph = graph
        graph.scope.launch {
            if (!graph.controller.quickToggle()) openApp()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = QuickToggles.openAppIntent(this)
        val launch = Runnable {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(
                        PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
        }
        if (isLocked) unlockAndRun(launch) else launch.run()
    }

    private fun updateTile(state: HueState) {
        val tile = qsTile ?: return
        val active = state.loaded && state.enabled && state.isAppliedSystemWide
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        if (state.loaded) {
            val status = QuickToggles.statusText(this, state)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = status
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tile.stateDescription = status
        }
        tile.updateTile()
    }
}
