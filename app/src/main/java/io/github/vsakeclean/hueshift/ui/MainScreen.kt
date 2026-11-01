package io.github.vsakeclean.hueshift.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.filter.HueFilter
import io.github.vsakeclean.hueshift.ui.components.EngineCard
import io.github.vsakeclean.hueshift.ui.components.FilterGrid
import io.github.vsakeclean.hueshift.ui.components.HueLogo
import io.github.vsakeclean.hueshift.ui.components.IntensityCard
import io.github.vsakeclean.hueshift.ui.components.PowerCard
import io.github.vsakeclean.hueshift.ui.components.PreviewCard
import io.github.vsakeclean.hueshift.ui.components.SectionTitle
import io.github.vsakeclean.hueshift.ui.theme.BrandTitleStyle
import io.github.vsakeclean.hueshift.ui.theme.HueColors

/** Everything the main screen can ask for. */
@Stable
class MainActions(
    val onSelectFilter: (HueFilter) -> Unit = {},
    val onToggle: (Boolean) -> Unit = {},
    val onIntensityChange: (Float) -> Unit = {},
    val onIntensityChangeFinished: () -> Unit = {},
    val onRequestShizuku: () -> Unit = {},
    val onOpenShizuku: () -> Unit = {},
    val onGetShizuku: () -> Unit = {},
    val onRequestRoot: () -> Unit = {},
    val onOpenAccessibility: () -> Unit = {},
    val onOpenAppInfo: () -> Unit = {},
    val onOpenDeveloperOptions: () -> Unit = {},
    val onPreferEngine: (EngineKind?) -> Unit = {},
    val onRetry: (EngineKind) -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
)

@Composable
fun MainScreen(
    state: HueState,
    actions: MainActions,
    modifier: Modifier = Modifier,
    initialSheet: Boolean = false,
) {
    var showSetup by rememberSaveable { mutableStateOf(initialSheet) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .background(HueColors.Background)
            .background(
                Brush.radialGradient(
                    listOf(HueColors.BackgroundGlow, HueColors.Background),
                    center = androidx.compose.ui.geometry.Offset(0f, 0f),
                    radius = 1400f,
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Entrance(0) { Header(onAbout = { showAbout = true }) }
                Entrance(1) { PreviewCard(state) }
                Entrance(2) { PowerCard(state, onToggle = actions.onToggle) }
                AnimatedVisibility(
                    visible = state.enabled && state.selected.supportsIntensity,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(250)) + fadeOut(tween(200)),
                ) {
                    IntensityCard(
                        filter = state.selected,
                        intensity = state.intensity,
                        onChange = actions.onIntensityChange,
                        onChangeFinished = actions.onIntensityChangeFinished,
                    )
                }
                Entrance(3) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle(stringResource(R.string.section_filters))
                        FilterGrid(
                            state = state,
                            onSelect = { filter, locked ->
                                actions.onSelectFilter(filter)
                                if (locked) showSetup = true
                            },
                        )
                    }
                }
                Entrance(4) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle(stringResource(R.string.section_mode))
                        EngineCard(state, onOpenSetup = { showSetup = true })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        // Keeps the status bar readable while content scrolls underneath.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(HueColors.Background.copy(alpha = 0.85f)),
        )
    }

    if (showSetup) SetupSheet(state, actions, onDismiss = { showSetup = false })
    if (showAbout) AboutDialog(onDismiss = { showAbout = false }, onOpenUrl = actions.onOpenUrl)
}

@Composable
private fun Header(onAbout: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HueLogo(size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = BrandTitleStyle)
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = HueColors.TextSecondary,
            )
        }
        IconButton(onClick = onAbout) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.action_about), tint = HueColors.TextSecondary)
        }
    }
}

/** Fades and slides a block in when the screen first appears, one block after another. */
@Composable
private fun Entrance(index: Int, content: @Composable () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 420, delayMillis = index * 60),
        label = "entrance",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 40f
        },
    ) { content() }
}
