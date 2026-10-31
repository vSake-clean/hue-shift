package io.github.vsakeclean.hueshift.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.filter.ColorTransform
import io.github.vsakeclean.hueshift.filter.Filters
import io.github.vsakeclean.hueshift.ui.components.HueLogo
import io.github.vsakeclean.hueshift.ui.theme.BrandTitleStyle
import io.github.vsakeclean.hueshift.ui.theme.HueColors
import io.github.vsakeclean.hueshift.ui.theme.HueShiftTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the README images into `docs/screenshots/<language>/`.
 *
 * Skipped by default; run with `./gradlew testDebugUnitTest -Phueshift.screenshots`.
 * Screens with a system-wide filter are recoloured afterwards, exactly like the phone does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
abstract class ScreenshotTest(private val language: String) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun onlyWhenRequested() {
        assumeTrue(System.getProperty("hueshift.screenshots") == "true")
    }

    protected fun capture(name: String, systemEffect: ColorTransform? = null, content: @Composable () -> Unit) {
        compose.setContent { HueShiftTheme(content) }
        compose.waitForIdle()
        // Draw the window in software; Robolectric has no GPU frames to read back.
        val root = compose.activity.window.decorView
        val rendered = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(rendered))
        val image = systemEffect?.let { rendered.recoloured(it) } ?: rendered
        val dir = File(requireNotNull(System.getProperty("hueshift.screenshotDir")), language).apply { mkdirs() }
        File(dir, "$name.webp").outputStream().use { image.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it) }
    }

    /** Applies the colour matrix to the whole image, like SurfaceFlinger does on the phone. */
    private fun Bitmap.recoloured(transform: ColorTransform): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(transform.toColorMatrixValues()) }
        Canvas(out).drawBitmap(this, 0f, 0f, paint)
        return out
    }
}

/** Phone-sized screenshots of the main states. */
@Config(qualifiers = "w411dp-h891dp-xhdpi")
abstract class PhoneScreenshots(language: String) : ScreenshotTest(language) {

    @Test
    fun firstStart() = capture("01_start") { MainScreen(SampleStates.firstStart(), MainActions()) }

    @Test
    fun grayscale() = capture("02_grayscale", Filters.Grayscale.transformAt(1f)) {
        MainScreen(SampleStates.live(Filters.Grayscale), MainActions())
    }

    @Test
    fun orange() = capture("03_orange", Filters.Orange.transformAt(0.7f)) {
        MainScreen(SampleStates.live(Filters.Orange, 0.7f), MainActions())
    }

    @Test
    fun negative() = capture("04_negative", Filters.Negative.transformAt(1f)) {
        MainScreen(SampleStates.live(Filters.Negative), MainActions())
    }

    @Test
    fun redscale() = capture("05_redscale", Filters.Redscale.transformAt(1f)) {
        MainScreen(SampleStates.live(Filters.Redscale), MainActions())
    }

    @Test
    fun previewOnly() = capture("06_preview_only") {
        MainScreen(SampleStates.previewOnly(Filters.Inverted), MainActions())
    }

    @Test
    fun setup() = capture("07_setup") {
        Box(
            Modifier
                .fillMaxSize()
                .background(HueColors.Surface)
                .padding(top = 24.dp),
        ) { SetupContent(SampleStates.setup(), MainActions()) }
    }
}

/** The banner at the top of the README: one phone without a filter, one in grayscale, one in orange. */
@Config(qualifiers = "w1500dp-h760dp-mdpi")
abstract class BannerScreenshot(language: String) : ScreenshotTest(language) {

    @Test
    fun banner() = capture("00_banner") {
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(HueColors.BackgroundGlow, HueColors.Background),
                        radius = 1400f,
                    ),
                )
                .padding(horizontal = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            Column(Modifier.width(300.dp)) {
                HueLogo(size = 88.dp)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.app_name), style = BrandTitleStyle.copy(fontSize = 64.sp))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.titleLarge,
                    color = HueColors.TextSecondary,
                )
            }
            Phone(SampleStates.firstStart(), effect = null)
            Phone(SampleStates.live(Filters.Grayscale), effect = Filters.Grayscale.transformAt(1f))
            Phone(SampleStates.live(Filters.Orange, 0.7f), effect = Filters.Orange.transformAt(0.7f))
        }
    }

    @Composable
    private fun Phone(state: HueState, effect: ColorTransform?) {
        val shape = RoundedCornerShape(40.dp)
        Box(
            Modifier
                .size(width = 320.dp, height = 660.dp)
                .clip(shape)
                .border(6.dp, HueColors.OutlineStrong, shape)
                .previewEffect { PreviewEffect("banner", transform = effect) },
        ) {
            MainScreen(state, MainActions())
        }
    }
}

@Config(qualifiers = "+pl")
class PhoneScreenshotsPl : PhoneScreenshots("pl")

@Config(qualifiers = "+en")
class PhoneScreenshotsEn : PhoneScreenshots("en")

@Config(qualifiers = "+pl")
class BannerScreenshotPl : BannerScreenshot("pl")

@Config(qualifiers = "+en")
class BannerScreenshotEn : BannerScreenshot("en")
