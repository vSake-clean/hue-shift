package io.github.vsakeclean.hueshift.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SettingsAccessibility
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.engine.EngineState
import io.github.vsakeclean.hueshift.ui.theme.HueColors

val EngineKind.icon: ImageVector
    get() = when (this) {
        EngineKind.SHIZUKU -> Icons.Rounded.Adb
        EngineKind.ROOT -> Icons.Rounded.Terminal
        EngineKind.SYSTEM -> Icons.Rounded.SettingsAccessibility
        EngineKind.OVERLAY -> Icons.Rounded.Layers
    }

@get:StringRes
val EngineKind.title: Int
    get() = when (this) {
        EngineKind.SHIZUKU -> R.string.engine_shizuku
        EngineKind.ROOT -> R.string.engine_root
        EngineKind.SYSTEM -> R.string.engine_system
        EngineKind.OVERLAY -> R.string.engine_overlay
    }

@get:StringRes
val EngineKind.summary: Int
    get() = when (this) {
        EngineKind.SHIZUKU -> R.string.engine_shizuku_summary
        EngineKind.ROOT -> R.string.engine_root_summary
        EngineKind.SYSTEM -> R.string.engine_system_summary
        EngineKind.OVERLAY -> R.string.engine_overlay_summary
    }

@StringRes
fun stateText(kind: EngineKind, state: EngineState): Int = when (state) {
    EngineState.READY -> R.string.state_ready
    EngineState.NOT_INSTALLED -> R.string.state_not_installed
    EngineState.NOT_RUNNING -> R.string.state_not_running
    EngineState.NEEDS_PERMISSION -> when (kind) {
        EngineKind.SHIZUKU, EngineKind.SYSTEM -> R.string.state_needs_permission
        EngineKind.ROOT -> R.string.state_root_not_set_up
        EngineKind.OVERLAY -> R.string.state_overlay_off
    }
    EngineState.DENIED -> if (kind == EngineKind.ROOT) R.string.state_denied_root else R.string.state_denied_shizuku
    EngineState.UNSUPPORTED -> R.string.state_unsupported
    EngineState.ERROR -> R.string.state_error
}

fun stateColor(state: EngineState): Color = when (state) {
    EngineState.READY -> HueColors.Success
    EngineState.DENIED, EngineState.ERROR, EngineState.UNSUPPORTED -> HueColors.Danger
    else -> HueColors.Warning
}

@Composable
fun EngineIcon(kind: EngineKind, modifier: Modifier = Modifier, tint: Color = HueColors.Violet) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kind.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** "How it works": a call to action while nothing is set up, otherwise the active mode. */
@Composable
fun EngineCard(state: HueState, onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    val active = state.engineForSelected ?: state.bestEngine
    if (active == null) {
        SetupCallToAction(onOpenSetup, modifier)
        return
    }
    Column(
        modifier
            .fillMaxWidth()
            .hueCard()
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EngineIcon(active, tint = HueColors.Success)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.engine_active, stringResource(active.title)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(active.summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = HueColors.TextSecondary,
                )
            }
            TextButton(onClick = onOpenSetup) { Text(stringResource(R.string.action_change)) }
        }
        if (state.selectedNeedsFullMode) {
            Spacer(Modifier.padding(top = 10.dp))
            Text(
                stringResource(R.string.engine_needs_full_mode, stringResource(state.selected.title)),
                style = MaterialTheme.typography.bodySmall,
                color = HueColors.Warning,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun SetupCallToAction(onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(HueColors.Pink.copy(alpha = 0.16f), HueColors.Violet.copy(alpha = 0.12f), HueColors.Teal.copy(alpha = 0.10f)),
                ),
            )
            .border(1.dp, Brush.linearGradient(HueColors.BrandColors.map { it.copy(alpha = 0.7f) }), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = HueColors.Pink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.engine_setup_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(R.string.engine_setup_text),
            style = MaterialTheme.typography.bodyMedium,
            color = HueColors.TextSecondary,
        )
        Button(
            onClick = onOpenSetup,
            colors = ButtonDefaults.buttonColors(containerColor = HueColors.Violet, contentColor = HueColors.Background),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.action_setup))
        }
    }
}
