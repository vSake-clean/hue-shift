package io.github.vsakeclean.hueshift.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.vsakeclean.hueshift.engine.OverlayService
import io.github.vsakeclean.hueshift.graph
import io.github.vsakeclean.hueshift.quick.QuickToggles
import io.github.vsakeclean.hueshift.ui.theme.HueShiftTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val controller get() = graph.controller

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        QuickToggles.publishShortcut(this)
        val actions = createActions()
        setContent {
            HueShiftTheme {
                val state by controller.state.collectAsStateWithLifecycle()
                MainScreen(state = state, actions = actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just installed/started Shizuku or changed system colours.
        controller.refreshEngines()
        controller.reapplyIfOwned()
    }

    private fun createActions() = MainActions(
        onSelectFilter = controller::select,
        onToggle = controller::setEnabled,
        onIntensityChange = { controller.setIntensity(it, save = false) },
        onIntensityChangeFinished = controller::commit,
        onRequestShizuku = controller::requestShizukuPermission,
        onOpenShizuku = {
            val intent = graph.shizuku.managerLaunchIntent()
            if (intent != null) launch(intent) else openShizukuDownload()
        },
        onGetShizuku = ::openShizukuDownload,
        onRequestRoot = { lifecycleScope.launch { controller.enableRoot() } },
        onOpenAccessibility = ::openAccessibilitySettings,
        onOpenAppInfo = {
            launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        },
        onOpenDeveloperOptions = {
            if (!launch(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) launch(Intent(Settings.ACTION_SETTINGS))
        },
        onPreferEngine = controller::preferEngine,
        onRetry = controller::retry,
        onOpenUrl = { url -> launch(Intent(Intent.ACTION_VIEW, url.toUri())) },
    )

    private fun openShizukuDownload() {
        val store = Intent(Intent.ACTION_VIEW, "market://details?id=$SHIZUKU_PACKAGE".toUri())
        if (!launch(store)) launch(Intent(Intent.ACTION_VIEW, SHIZUKU_DOWNLOAD_URL.toUri()))
    }

    private fun openAccessibilitySettings() {
        // Try to jump straight to hue-shift's entry; not every system allows that.
        val component = ComponentName(this, OverlayService::class.java).flattenToString()
        val details = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        if (!launch(details)) launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun launch(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
        const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    }
}
