package io.github.vsakeclean.hueshift.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import io.github.vsakeclean.hueshift.filter.ColorTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/**
 * Applies exact colour matrices through Shizuku: the SurfaceFlinger call is forwarded by the
 * Shizuku service, which runs with shell (ADB) or root privileges.
 */
class ShizukuEngine(private val context: Context) : FilterEngine {

    override val kind = EngineKind.SHIZUKU

    private val _state = MutableStateFlow(EngineState.NOT_RUNNING)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile private var lastError: Throwable? = null
    @Volatile private var surfaceFlinger: IBinder? = null

    /** Set when the direct binder call is refused but the shell command works (e.g. some HyperOS builds). */
    @Volatile private var useShellCommand = false

    private val _errorDetails = MutableStateFlow<String?>(null)
    override val errorDetails: StateFlow<String?> = _errorDetails.asStateFlow()

    // Shell commands are slower, so let every frame wait for the previous one instead.
    override val frameIntervalMillis: Long get() = if (useShellCommand) 0L else 16L

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        lastError = null
        refresh()
    }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    override fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    override fun refresh() {
        _state.value = computeState()
    }

    private fun computeState(): EngineState = try {
        when {
            !Shizuku.pingBinder() -> if (isManagerInstalled()) EngineState.NOT_RUNNING else EngineState.NOT_INSTALLED
            Shizuku.isPreV11() -> EngineState.UNSUPPORTED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                if (lastError == null) EngineState.READY else EngineState.ERROR
            Shizuku.shouldShowRequestPermissionRationale() -> EngineState.DENIED
            else -> EngineState.NEEDS_PERMISSION
        }
    } catch (e: RuntimeException) {
        // The binder can die between two calls.
        EngineState.NOT_RUNNING
    }

    fun isManagerInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun managerLaunchIntent(): Intent? =
        context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not request the Shizuku permission", e)
        }
        refresh()
    }

    /** Clears a previous error so the engine is tried again. */
    fun retry() {
        lastError = null
        _errorDetails.value = null
        refresh()
    }

    override suspend fun show(rendering: Rendering?) = withContext(Dispatchers.IO) {
        val transform = (rendering as? Rendering.Matrix)?.transform
        try {
            if (useShellCommand) {
                runShellCommand(transform)
            } else {
                try {
                    transactDirectly(transform)
                } catch (direct: Exception) {
                    Log.w(TAG, "Direct SurfaceFlinger call failed, trying `service call`", direct)
                    try {
                        runShellCommand(transform)
                    } catch (shell: Exception) {
                        throw IllegalStateException("binder: ${direct.describe()}\nservice call: ${shell.describe()}", shell)
                    }
                    // The command works on this phone: keep using it.
                    useShellCommand = true
                }
            }
            if (lastError != null) {
                lastError = null
                _errorDetails.value = null
                refresh()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Setting the colour matrix through Shizuku failed", e)
            lastError = e
            _errorDetails.value = e.describe()
            refresh()
            throw e
        }
    }

    /** Sends transaction 1015 straight to SurfaceFlinger, forwarded by the Shizuku service. */
    private fun transactDirectly(transform: ColorTransform?) {
        val binder = surfaceFlinger?.takeIf { it.isBinderAlive }
            ?: SurfaceFlinger.findService()?.also { surfaceFlinger = it }
            ?: error("SurfaceFlinger service not found")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            SurfaceFlinger.writeColorMatrix(data, transform)
            ShizukuBinderWrapper(binder).transact(SurfaceFlinger.TRANSACTION_SET_COLOR_MATRIX, data, reply, 0)
            // Errors raised on the Shizuku side (e.g. revoked permission) come back in the reply.
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** The same change through the `service call` shell command, run with Shizuku's privileges. */
    private fun runShellCommand(transform: ColorTransform?) {
        val result = ShizukuShell.run(SurfaceFlinger.shellCommand(transform))
        check(result.exitCode == 0 && !result.output.contains("Error")) {
            result.output.ifBlank { "exit code ${result.exitCode}" }
        }
    }

    private fun Throwable.describe(): String = "${javaClass.simpleName}: ${message ?: "-"}"

    companion object {
        const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST_CODE = 0x4853
        private const val TAG = "HueShift/Shizuku"
    }
}
