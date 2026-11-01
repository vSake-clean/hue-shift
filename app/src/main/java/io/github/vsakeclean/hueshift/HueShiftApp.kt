package io.github.vsakeclean.hueshift

import android.app.Application
import android.content.Context
import io.github.vsakeclean.hueshift.core.FilterController
import io.github.vsakeclean.hueshift.core.SystemColourWatcher
import io.github.vsakeclean.hueshift.engine.OverlayEngine
import io.github.vsakeclean.hueshift.engine.RootEngine
import io.github.vsakeclean.hueshift.engine.ShizukuEngine
import io.github.vsakeclean.hueshift.engine.SystemSettingsEngine
import io.github.vsakeclean.hueshift.quick.QuickToggles
import io.github.vsakeclean.hueshift.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HueShiftApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.start()
    }
}

/** Tiny hand-written dependency container: one instance of everything per process. */
class AppGraph(private val app: Application) {

    /** Lives as long as the process; work started here survives activities and services. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val shizuku = ShizukuEngine(app)
    val root = RootEngine()
    val system = SystemSettingsEngine(app)
    val overlay = OverlayEngine()

    val controller = FilterController(
        scope = scope,
        store = SettingsStore(app),
        engines = listOf(shizuku, root, system, overlay),
    )

    fun start() {
        controller.start()
        SystemColourWatcher(app, controller).start()
        QuickToggles.keepUpdated(app, scope, controller)
    }
}

val Context.graph: AppGraph
    get() = (applicationContext as HueShiftApp).graph
