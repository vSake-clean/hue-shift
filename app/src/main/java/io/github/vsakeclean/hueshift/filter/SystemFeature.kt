package io.github.vsakeclean.hueshift.filter

/**
 * Colour features built into Android itself (Settings → Accessibility). Apps may switch them
 * once they have been given the WRITE_SECURE_SETTINGS permission, which works on phones where
 * the colour matrix is off-limits even for Shizuku (e.g. Xiaomi with HyperOS).
 *
 * [transform] is the matrix Android applies for the feature, used for the in-app preview.
 */
enum class SystemFeature(val transform: ColorTransform) {
    /** "Colour correction" in grayscale (monochromacy) mode. */
    GRAYSCALE(ColorTransform.luminance(1f, 1f, 1f, Luma.Rec709)),

    /** "Colour inversion". */
    INVERSION(ColorTransform.luminanceInversion(Luma.Rec601)),
}
