package io.github.vsakeclean.hueshift.ui

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.engine.EngineState
import io.github.vsakeclean.hueshift.ui.components.EngineIcon
import io.github.vsakeclean.hueshift.ui.components.Pill
import io.github.vsakeclean.hueshift.ui.components.hueCard
import io.github.vsakeclean.hueshift.ui.components.stateColor
import io.github.vsakeclean.hueshift.ui.components.stateText
import io.github.vsakeclean.hueshift.ui.components.summary
import io.github.vsakeclean.hueshift.ui.components.title
import io.github.vsakeclean.hueshift.ui.theme.HueColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupSheet(state: HueState, actions: MainActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HueColors.Surface,
        contentColor = HueColors.TextPrimary,
    ) {
        SetupContent(state, actions)
    }
}

/** Explains the three modes and offers the next step for each of them. */
@Composable
fun SetupContent(state: HueState, actions: MainActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.setup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = HueColors.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        EngineKind.entries.forEach { kind -> EngineOption(kind, state, actions) }
        if (state.settings.enginePreference != null) {
            TextButton(onClick = { actions.onPreferEngine(null) }) {
                Text(stringResource(R.string.action_auto_mode))
            }
        } else {
            Text(
                stringResource(R.string.setup_auto_hint),
                style = MaterialTheme.typography.bodySmall,
                color = HueColors.TextTertiary,
            )
        }
        Text(
            stringResource(R.string.setup_system_colours_note),
            style = MaterialTheme.typography.bodySmall,
            color = HueColors.TextTertiary,
        )
    }
}

/** Xiaomi, Redmi and POCO phones (MIUI / HyperOS) need an extra developer option for Shizuku. */
private val isXiaomi: Boolean =
    Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
        Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineOption(kind: EngineKind, state: HueState, actions: MainActions) {
    val engineState = state.stateOf(kind)
    val inUse = engineState.isReady && (state.engineForSelected ?: state.bestEngine) == kind
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .hueCard(shape = shape, color = HueColors.SurfaceRaised)
            .then(if (inUse) Modifier.border(1.5.dp, HueColors.Success.copy(alpha = 0.7f), shape) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EngineIcon(kind, tint = if (engineState.isReady) HueColors.Success else HueColors.Violet)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(kind.title), style = MaterialTheme.typography.titleMedium)
                    if (kind == EngineKind.SHIZUKU) {
                        Spacer(Modifier.width(8.dp))
                        Pill(stringResource(R.string.engine_recommended), HueColors.Teal)
                    }
                }
                Pill(
                    text = stringResource(if (inUse) R.string.engine_in_use else stateText(kind, engineState)),
                    color = stateColor(engineState),
                    dot = true,
                )
            }
        }
        Text(stringResource(kind.summary), style = MaterialTheme.typography.bodyMedium, color = HueColors.TextSecondary)
        val error = state.engineErrors[kind]
        if (engineState == EngineState.ERROR && error != null) {
            SelectionContainer {
                Text(
                    stringResource(R.string.setup_error_details, error),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = HueColors.Danger,
                )
            }
        }
        if (!engineState.isReady) {
            // Shizuku is already set up when the system refuses a change: skip the setup steps then.
            if (engineState != EngineState.ERROR) {
                steps(kind).forEach { step ->
                    Text(stringResource(step), style = MaterialTheme.typography.bodySmall, color = HueColors.TextSecondary)
                }
            }
            if (kind == EngineKind.SHIZUKU && isXiaomi) {
                Text(
                    stringResource(R.string.setup_shizuku_xiaomi),
                    style = MaterialTheme.typography.bodySmall,
                    color = HueColors.Warning,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EngineButtons(kind, engineState, inUse, actions)
        }
    }
}

private fun steps(kind: EngineKind): List<Int> = when (kind) {
    EngineKind.SHIZUKU -> listOf(R.string.setup_shizuku_steps, R.string.setup_shizuku_reboot)
    EngineKind.ROOT -> listOf(R.string.setup_root_steps)
    EngineKind.OVERLAY -> listOf(R.string.setup_overlay_steps, R.string.setup_overlay_restricted)
}

@Composable
private fun EngineButtons(kind: EngineKind, engineState: EngineState, inUse: Boolean, actions: MainActions) {
    when {
        engineState.isReady && !inUse -> PrimaryButton(R.string.action_use_mode) { actions.onPreferEngine(kind) }
        engineState.isReady -> Unit
        kind == EngineKind.SHIZUKU -> {
            when (engineState) {
                EngineState.NOT_INSTALLED, EngineState.UNSUPPORTED ->
                    PrimaryButton(R.string.action_get_shizuku, actions.onGetShizuku)
                EngineState.NOT_RUNNING, EngineState.DENIED ->
                    PrimaryButton(R.string.action_open_shizuku, actions.onOpenShizuku)
                EngineState.NEEDS_PERMISSION -> PrimaryButton(R.string.action_grant_access, actions.onRequestShizuku)
                else -> PrimaryButton(R.string.action_try_again) { actions.onRetry(kind) }
            }
            if (engineState != EngineState.NOT_INSTALLED) {
                SecondaryButton(R.string.action_developer_options, actions.onOpenDeveloperOptions)
            }
        }
        kind == EngineKind.ROOT -> PrimaryButton(
            if (engineState == EngineState.NEEDS_PERMISSION) R.string.action_use_root else R.string.action_try_again,
            actions.onRequestRoot,
        )
        else -> {
            PrimaryButton(R.string.action_open_accessibility, actions.onOpenAccessibility)
            SecondaryButton(R.string.action_app_info, actions.onOpenAppInfo)
        }
    }
}

@Composable
private fun PrimaryButton(text: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = HueColors.Violet, contentColor = HueColors.Background),
    ) { Text(stringResource(text)) }
}

@Composable
private fun SecondaryButton(text: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(stringResource(text), color = HueColors.TextPrimary) }
}
