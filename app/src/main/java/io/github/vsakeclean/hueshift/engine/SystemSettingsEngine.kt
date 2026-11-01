package io.github.vsakeclean.hueshift.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import io.github.vsakeclean.hueshift.filter.SystemFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Switches Android's built-in grayscale ("colour correction") and colour inversion through
 * system settings. It needs the WRITE_SECURE_SETTINGS permission, granted once through Shizuku
 * (or `adb shell pm grant`). After that it works without Shizuku, and the system keeps the
 * colours after a reboot, which makes it the fallback for phones that do not let Shizuku touch
 * the colour matrix (e.g. Xiaomi with HyperOS).
 */
class SystemSettingsEngine(private val context: Context) : FilterEngine {

    override val kind = EngineKind.SYSTEM

    private val _state = MutableStateFlow(computeState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _errorDetails = MutableStateFlow<String?>(null)
    override val errorDetails: StateFlow<String?> = _errorDetails.asStateFlow()

    // Remembers which feature hue-shift switched on, so only that one is switched off again.
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun refresh() {
        _state.value = computeState()
    }

    private fun computeState(): EngineState =
        if (hasPermission()) EngineState.READY else EngineState.NEEDS_PERMISSION

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** The command that grants the permission from a computer. */
    val adbGrantCommand: String
        get() = "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"

    /** Grants the permission with Shizuku's privileges. Returns whether it worked. */
    suspend fun grantWithShizuku(): Boolean = withContext(Dispatchers.IO) {
        try {
            check(Shizuku.pingBinder()) { "Shizuku is not running" }
            val result = ShizukuShell.run("pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}")
            check(result.exitCode == 0 && hasPermission()) { result.output.ifBlank { "exit code ${result.exitCode}" } }
            _errorDetails.value = null
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Granting WRITE_SECURE_SETTINGS through Shizuku failed", e)
            _errorDetails.value = "${e.javaClass.simpleName}: ${e.message ?: "-"}"
            false
        } finally {
            refresh()
        }
    }

    override suspend fun show(rendering: Rendering?) = withContext(Dispatchers.IO) {
        val wanted = (rendering as? Rendering.System)?.feature
        try {
            val previous = prefs.getString(KEY_FEATURE, null)?.let { name -> SystemFeature.entries.firstOrNull { it.name == name } }
            if (previous != null && previous != wanted) switch(previous, on = false)
            if (wanted != null) switch(wanted, on = true)
            prefs.edit().putString(KEY_FEATURE, wanted?.name).apply()
            _errorDetails.value = null
        } catch (e: Exception) {
            Log.w(TAG, "Changing the system colour settings failed", e)
            _errorDetails.value = "${e.javaClass.simpleName}: ${e.message ?: "-"}"
            refresh()
            throw e
        }
        Unit
    }

    private fun switch(feature: SystemFeature, on: Boolean) {
        val resolver = context.contentResolver
        when (feature) {
            SystemFeature.GRAYSCALE -> {
                if (on) Settings.Secure.putInt(resolver, DALTONIZER_MODE, DALTONIZER_MONOCHROMACY)
                Settings.Secure.putInt(resolver, DALTONIZER_ENABLED, if (on) 1 else 0)
            }
            SystemFeature.INVERSION -> Settings.Secure.putInt(resolver, INVERSION_ENABLED, if (on) 1 else 0)
        }
    }

    private companion object {
        const val TAG = "HueShift/System"
        const val PREFS = "system_engine"
        const val KEY_FEATURE = "feature"

        const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        const val DALTONIZER_MODE = "accessibility_display_daltonizer"
        const val DALTONIZER_MONOCHROMACY = 0
        const val INVERSION_ENABLED = "accessibility_display_inversion_enabled"
    }
}
