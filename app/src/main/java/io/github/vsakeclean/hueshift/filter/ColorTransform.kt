package io.github.vsakeclean.hueshift.filter

import androidx.compose.runtime.Immutable
import java.util.Locale
import kotlin.math.abs

/**
 * An affine colour transform working on RGB values in the `0..1` range. Alpha is never touched.
 *
 * ```
 * R' = m00·R + m01·G + m02·B + m03
 * G' = m10·R + m11·G + m12·B + m13
 * B' = m20·R + m21·G + m22·B + m23
 * ```
 *
 * The very same transform drives the in-app preview (as an Android `ColorMatrix`) and the
 * system-wide filter (as the 4×4 matrix SurfaceFlinger expects), which is why the preview always
 * matches what the screen is going to look like.
 */
@Immutable
class ColorTransform private constructor(private val m: FloatArray) {

    init {
        require(m.size == SIZE) { "A colour transform needs exactly $SIZE values" }
    }

    /** Coefficient at [row] (0 = red, 1 = green, 2 = blue) and [column] (0..2 = R, G, B, 3 = offset). */
    operator fun get(row: Int, column: Int): Float {
        require(row in 0..2 && column in 0..3) { "Index out of range: [$row, $column]" }
        return m[row * 4 + column]
    }

    val isIdentity: Boolean get() = approximatelyEquals(Identity)

    /**
     * Composition: `(this * other)` applies [other] first and then this transform,
     * exactly like multiplying the underlying matrices.
     */
    operator fun times(other: ColorTransform): ColorTransform {
        val out = FloatArray(SIZE)
        for (row in 0..2) {
            for (column in 0..3) {
                var sum = if (column == 3) this[row, 3] else 0f
                for (k in 0..2) sum += this[row, k] * other[k, column]
                out[row * 4 + column] = sum
            }
        }
        return ColorTransform(out)
    }

    /** Linear interpolation between this transform (`fraction = 0`) and [target] (`fraction = 1`). */
    fun lerp(target: ColorTransform, fraction: Float): ColorTransform {
        if (fraction <= 0f) return this
        if (fraction >= 1f) return target
        return ColorTransform(FloatArray(SIZE) { i -> m[i] + (target.m[i] - m[i]) * fraction })
    }

    /** Determinant of the colour part: how much the transform shrinks (|det| < 1) the colour space. */
    val determinant: Float
        get() {
            val a = this[0, 0]; val b = this[0, 1]; val c = this[0, 2]
            val d = this[1, 0]; val e = this[1, 1]; val f = this[1, 2]
            val g = this[2, 0]; val h = this[2, 1]; val i = this[2, 2]
            return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        }

    /** The transform that undoes this one, or `null` when it collapses colours (e.g. full grayscale). */
    fun inverse(): ColorTransform? {
        val a = this[0, 0]; val b = this[0, 1]; val c = this[0, 2]
        val d = this[1, 0]; val e = this[1, 1]; val f = this[1, 2]
        val g = this[2, 0]; val h = this[2, 1]; val i = this[2, 2]
        val det = determinant
        if (abs(det) < SINGULAR_EPSILON) return null
        val inv = floatArrayOf(
            (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
            (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det,
        )
        val out = FloatArray(SIZE)
        for (row in 0..2) {
            var offset = 0f
            for (k in 0..2) {
                out[row * 4 + k] = inv[row * 3 + k]
                offset -= inv[row * 3 + k] * this[k, 3]
            }
            out[row * 4 + 3] = offset
        }
        return ColorTransform(out)
    }

    /** Applies the transform to a colour and clamps the result to `0..1`. Returns `[r, g, b]`. */
    fun apply(red: Float, green: Float, blue: Float): FloatArray = FloatArray(3) { row ->
        (this[row, 0] * red + this[row, 1] * green + this[row, 2] * blue + this[row, 3]).coerceIn(0f, 1f)
    }

    /**
     * The 4×4 matrix in the column-major layout used by SurfaceFlinger (`mat4`), where the last
     * column holds the offsets and the last row is `(0, 0, 0, 1)`.
     */
    fun toSurfaceFlingerMatrix(): FloatArray {
        val out = FloatArray(16)
        for (row in 0..2) {
            for (column in 0..3) out[column * 4 + row] = this[row, column]
        }
        out[15] = 1f
        return out
    }

    /**
     * The 4×5 row-major values of an Android / Compose `ColorMatrix`. Those expect the offsets
     * in the `0..255` range, hence the scaling.
     */
    fun toColorMatrixValues(): FloatArray {
        val out = FloatArray(20)
        for (row in 0..2) {
            out[row * 5 + 0] = this[row, 0]
            out[row * 5 + 1] = this[row, 1]
            out[row * 5 + 2] = this[row, 2]
            out[row * 5 + 4] = this[row, 3] * 255f
        }
        out[18] = 1f
        return out
    }

    fun approximatelyEquals(other: ColorTransform, epsilon: Float = 1e-4f): Boolean =
        m.indices.all { abs(m[it] - other.m[it]) <= epsilon }

    override fun equals(other: Any?): Boolean = other is ColorTransform && m.contentEquals(other.m)

    override fun hashCode(): Int = m.contentHashCode()

    override fun toString(): String = buildString {
        append("ColorTransform(")
        for (row in 0..2) {
            if (row > 0) append(" | ")
            append((0..3).joinToString(" ") { String.format(Locale.ROOT, "%.3f", this@ColorTransform[row, it]) })
        }
        append(")")
    }

    companion object {
        private const val SIZE = 12
        private const val SINGULAR_EPSILON = 1e-3f

        val Identity = ColorTransform(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
            ),
        )

        /** Builds a transform from its three rows; each row is `[r, g, b, offset]`. */
        fun fromRows(red: FloatArray, green: FloatArray, blue: FloatArray): ColorTransform {
            require(red.size == 4 && green.size == 4 && blue.size == 4) { "Each row needs 4 values" }
            return ColorTransform(red + green + blue)
        }

        /** Multiplies every channel by its own factor (like a colour temperature shift). */
        fun scale(red: Float, green: Float, blue: Float): ColorTransform = fromRows(
            floatArrayOf(red, 0f, 0f, 0f),
            floatArrayOf(0f, green, 0f, 0f),
            floatArrayOf(0f, 0f, blue, 0f),
        )

        /**
         * Replaces every colour with its brightness (luma) painted in the given tint:
         * `(1, 1, 1)` gives grayscale, `(1, 0, 0)` gives a red scale and so on.
         */
        fun luminance(tintRed: Float, tintGreen: Float, tintBlue: Float, luma: Luma = Luma.Rec709): ColorTransform =
            fromRows(
                floatArrayOf(luma.red * tintRed, luma.green * tintRed, luma.blue * tintRed, 0f),
                floatArrayOf(luma.red * tintGreen, luma.green * tintGreen, luma.blue * tintGreen, 0f),
                floatArrayOf(luma.red * tintBlue, luma.green * tintBlue, luma.blue * tintBlue, 0f),
            )

        /** Classic photographic negative: every channel becomes `1 - value`. */
        fun negative(): ColorTransform = fromRows(
            floatArrayOf(-1f, 0f, 0f, 1f),
            floatArrayOf(0f, -1f, 0f, 1f),
            floatArrayOf(0f, 0f, -1f, 1f),
        )

        /**
         * Inverts brightness but keeps hues: `c' = c + (1 - 2·Y)`. With [Luma.Rec601] this is the
         * exact matrix Android uses for its built-in "colour inversion" accessibility feature.
         */
        fun luminanceInversion(luma: Luma = Luma.Rec601): ColorTransform {
            val r = 2f * luma.red
            val g = 2f * luma.green
            val b = 2f * luma.blue
            return fromRows(
                floatArrayOf(1f - r, -g, -b, 1f),
                floatArrayOf(-r, 1f - g, -b, 1f),
                floatArrayOf(-r, -g, 1f - b, 1f),
            )
        }
    }
}

/** Weights used to compute the perceived brightness (luma) of a colour. */
enum class Luma(val red: Float, val green: Float, val blue: Float) {
    /** HDTV / sRGB weights; Android uses them for its grayscale mode. */
    Rec709(0.2126f, 0.7152f, 0.0722f),

    /** SDTV weights; Android uses them for its colour inversion. */
    Rec601(0.299f, 0.587f, 0.114f),
}
