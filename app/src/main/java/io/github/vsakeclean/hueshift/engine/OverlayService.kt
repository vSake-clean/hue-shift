package io.github.vsakeclean.hueshift.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.MainThread
import io.github.vsakeclean.hueshift.filter.OverlayTint
import io.github.vsakeclean.hueshift.graph

/**
 * Accessibility service used only to draw the colour overlay of the basic mode.
 *
 * Accessibility overlays are the only windows a regular app may place above the status bar,
 * the navigation bar and the notification shade. The service does not read the screen
 * content and does not listen to any accessibility events.
 */
class OverlayService : AccessibilityService() {

    private var overlay: View? = null

    private val engine: OverlayEngine get() = graph.overlay

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine.attach(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeOverlay()
        engine.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeOverlay()
        engine.detach(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The screen may have grown (foldables, resolution change): resize the overlay.
        val view = overlay ?: return
        val windowManager = getSystemService(WindowManager::class.java) ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val side = largestDisplaySide(windowManager)
        if (params.width != side) {
            params.width = side
            params.height = side
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    @MainThread
    fun showTint(tint: OverlayTint?) {
        if (tint == null || !tint.isVisible) {
            removeOverlay()
            return
        }
        val view = overlay ?: addOverlay() ?: return
        view.setBackgroundColor(tint.toArgb())
    }

    private fun addOverlay(): View? {
        val windowManager = getSystemService(WindowManager::class.java) ?: return null
        val side = largestDisplaySide(windowManager)
        val params = WindowManager.LayoutParams(
            side,
            side,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "hue-shift"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
                fitInsetsSides = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val view = View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        return try {
            windowManager.addView(view, params)
            overlay = view
            view
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not add the overlay window", e)
            null
        }
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        overlay = null
        runCatching { getSystemService(WindowManager::class.java)?.removeViewImmediate(view) }
    }

    /** A square with the longest display side covers the screen in every orientation. */
    private fun largestDisplaySide(windowManager: WindowManager): Int {
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
        return maxOf(width, height) + EXTRA_PIXELS
    }

    private companion object {
        const val TAG = "HueShift/Overlay"

        // Covers rounding at the edges and the area behind rounded corners.
        const val EXTRA_PIXELS = 16
    }
}
