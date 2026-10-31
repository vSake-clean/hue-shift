package io.github.vsakeclean.hueshift.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException

/** Applies exact colour matrices with `su` (Magisk, KernelSU, APatch, ...). */
class RootEngine : FilterEngine {

    override val kind = EngineKind.ROOT

    // Every frame waits for the previous command to finish, so no extra pause is needed.
    override val frameIntervalMillis = 0L

    private val _state = MutableStateFlow(EngineState.NEEDS_PERMISSION)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var shell: RootShell? = null

    /** Opens a root shell; the first time this shows the root manager's prompt. */
    suspend fun connect(): Boolean = mutex.withLock { openShellLocked() != null }

    /** Forgets root access (the user switched to another mode). */
    suspend fun disconnect() = mutex.withLock {
        shell?.close()
        shell = null
        _state.value = EngineState.NEEDS_PERMISSION
    }

    private suspend fun openShellLocked(): RootShell? {
        shell?.takeIf { it.isAlive }?.let { return it }
        val opened = withContext(Dispatchers.IO) { RootShell.open() }
        shell = opened
        _state.value = if (opened != null) EngineState.READY else EngineState.DENIED
        return opened
    }

    override suspend fun show(rendering: Rendering?) = mutex.withLock {
        val root = openShellLocked() ?: error("Root access is not available")
        val command = SurfaceFlinger.shellCommand((rendering as? Rendering.Matrix)?.transform)
        val ok = withContext(Dispatchers.IO) { root.run(command) }
        if (!ok) {
            shell = null
            _state.value = EngineState.ERROR
            error("The root shell stopped responding")
        }
    }
}

/** A long-lived `su` shell, so that animation frames do not have to start a new process each. */
internal class RootShell private constructor(private val process: Process) : Closeable {

    private val input: BufferedWriter = process.outputStream.bufferedWriter()
    private val output: BufferedReader = process.inputStream.bufferedReader()
    private var counter = 0

    val isAlive: Boolean get() = process.isAlive

    /** Runs [command], waits for it to finish and returns `false` if the shell is gone. */
    @Synchronized
    fun run(command: String): Boolean = try {
        val marker = "__hue_shift_${counter++}__"
        input.write("$command >/dev/null 2>&1; echo $marker\n")
        input.flush()
        readUntil(marker) != null
    } catch (e: IOException) {
        Log.w(TAG, "Root command failed", e)
        false
    }

    private fun readUntil(marker: String): List<String>? {
        val lines = mutableListOf<String>()
        while (true) {
            val line = output.readLine() ?: return null
            if (line.trim() == marker) return lines
            lines += line
        }
    }

    override fun close() {
        try {
            input.write("exit\n")
            input.flush()
        } catch (_: IOException) {
            // Already closed.
        }
        process.destroy()
    }

    companion object {
        private const val TAG = "HueShift/Root"
        private const val READY_MARKER = "__hue_shift_ready__"

        /** Starts `su` and checks that it really runs as uid 0. Blocks until the user answers the prompt. */
        fun open(timeoutMillis: Long = 30_000): RootShell? {
            val process = try {
                ProcessBuilder("su").redirectErrorStream(true).start()
            } catch (e: IOException) {
                return null
            }
            val shell = RootShell(process)
            // A root prompt that never gets an answer must not block us forever.
            val watchdog = Thread {
                try {
                    Thread.sleep(timeoutMillis)
                    process.destroy()
                } catch (_: InterruptedException) {
                    // Finished in time.
                }
            }.apply {
                isDaemon = true
                start()
            }
            val isRoot = try {
                shell.input.write("id -u; echo $READY_MARKER\n")
                shell.input.flush()
                shell.readUntil(READY_MARKER)?.any { it.trim() == "0" } == true
            } catch (e: IOException) {
                false
            } finally {
                watchdog.interrupt()
            }
            return if (isRoot) {
                shell
            } else {
                process.destroy()
                null
            }
        }
    }
}
