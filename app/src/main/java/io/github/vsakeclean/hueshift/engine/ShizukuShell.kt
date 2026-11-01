package io.github.vsakeclean.hueshift.engine

import rikka.shizuku.Shizuku
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Runs a shell command with Shizuku's privileges. Used as a fallback when the direct binder call
 * to SurfaceFlinger is refused, and to get a readable error message from the system.
 */
internal object ShizukuShell {

    data class Result(val exitCode: Int, val output: String)

    // Not part of the public Shizuku API any more, but still supported by the Shizuku service.
    private val newProcess: Method by lazy {
        Shizuku::class.java
            .getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            .apply { isAccessible = true }
    }

    fun run(command: String): Result {
        val process = try {
            newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
        try {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val errors = process.errorStream.bufferedReader().use { it.readText() }
            return Result(process.waitFor(), (output + errors).trim())
        } finally {
            process.destroy()
        }
    }
}
