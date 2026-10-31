package io.github.vsakeclean.hueshift.engine

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Parcel
import io.github.vsakeclean.hueshift.filter.ColorTransform
import java.util.Locale

/**
 * Talks to SurfaceFlinger, the Android process that composes everything you see on screen.
 *
 * Transaction 1015 sets a global colour matrix that is applied to the final image — the same
 * mechanism Android itself uses for Night Light, grayscale and colour inversion. Only
 * privileged callers (shell or root) may use it, which is why hue-shift needs Shizuku or root
 * for exact filters.
 */
internal object SurfaceFlinger {

    const val SERVICE_NAME = "SurfaceFlinger"
    const val INTERFACE_DESCRIPTOR = "android.ui.ISurfaceComposer"
    const val TRANSACTION_SET_COLOR_MATRIX = 1015

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun findService(): IBinder? = try {
        Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, SERVICE_NAME) as? IBinder
    } catch (e: ReflectiveOperationException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    /** Writes the 1015 payload. `null` (or identity) resets the matrix. */
    fun writeColorMatrix(parcel: Parcel, transform: ColorTransform?) {
        parcel.writeInterfaceToken(INTERFACE_DESCRIPTOR)
        val matrix = matrixOrNull(transform)
        if (matrix == null) {
            parcel.writeInt(0)
        } else {
            parcel.writeInt(1)
            matrix.forEach(parcel::writeFloat)
        }
    }

    /** The equivalent `service call` command, used with root. Always uses a dot as decimal separator. */
    fun shellCommand(transform: ColorTransform?): String = buildString {
        append("service call ").append(SERVICE_NAME).append(' ').append(TRANSACTION_SET_COLOR_MATRIX)
        val matrix = matrixOrNull(transform)
        if (matrix == null) {
            append(" i32 0")
        } else {
            append(" i32 1")
            matrix.forEach { append(" f ").append(String.format(Locale.ROOT, "%.6f", it)) }
        }
    }

    private fun matrixOrNull(transform: ColorTransform?): FloatArray? =
        transform?.takeUnless { it.isIdentity }?.toSurfaceFlingerMatrix()
}
