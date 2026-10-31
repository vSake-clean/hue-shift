package io.github.vsakeclean.hueshift.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.util.Log
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
        refresh()
    }

    override suspend fun show(rendering: Rendering?) = withContext(Dispatchers.IO) {
        val transform = (rendering as? Rendering.Matrix)?.transform
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
            if (lastError != null) {
                lastError = null
                refresh()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Setting the colour matrix through Shizuku failed", e)
            lastError = e
            refresh()
            throw e
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    companion object {
        const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST_CODE = 0x4853
        private const val TAG = "HueShift/Shizuku"
    }
}
