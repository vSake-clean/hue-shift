package io.github.vsakeclean.hueshift.quick

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import io.github.vsakeclean.hueshift.graph
import kotlinx.coroutines.launch

/**
 * Invisible activity behind the launcher shortcut "Toggle filter". It lets touches through,
 * toggles the filter and closes itself (or opens the app when setup is still needed).
 */
class ToggleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        val graph = graph
        graph.scope.launch {
            val controller = graph.controller
            if (controller.quickToggle()) {
                val text = QuickToggles.feedback(applicationContext, controller.state.value)
                Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
            } else {
                startActivity(QuickToggles.openAppIntent(this@ToggleActivity))
            }
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "io.github.vsakeclean.hueshift.action.SHORTCUT_TOGGLE"
    }
}
