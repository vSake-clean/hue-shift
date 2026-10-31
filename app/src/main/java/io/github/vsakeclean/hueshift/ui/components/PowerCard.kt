package io.github.vsakeclean.hueshift.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.ui.theme.HueColors

/** The big on/off switch. The whole card is one toggle target. */
@Composable
fun PowerCard(state: HueState, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val on = state.enabled
    val accent by animateColorAsState(Color(state.selected.accent), tween(400), label = "accent")
    val glow by animateFloatAsState(if (on) 1f else 0f, tween(450), label = "glow")
    val border by animateColorAsState(
        if (on) accent.copy(alpha = 0.6f) else HueColors.Outline,
        tween(400),
        label = "border",
    )
    val title = stringResource(state.selected.title)
    val (subtitle, subtitleColor) = when {
        !on -> stringResource(R.string.power_subtitle_off, title) to HueColors.TextSecondary
        state.isAppliedSystemWide -> stringResource(R.string.power_subtitle_applied, title) to HueColors.TextSecondary
        state.needsSetup -> stringResource(R.string.power_subtitle_needs_setup) to HueColors.Warning
        else -> stringResource(R.string.power_subtitle_needs_full_mode, title) to HueColors.Warning
    }
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HueColors.Surface)
            .drawBehind {
                if (glow > 0f) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.28f * glow), accent.copy(alpha = 0.04f * glow)),
                        ),
                    )
                }
            }
            .border(1.dp, border, shape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(lerp(HueColors.SurfaceHigh, accent, glow)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = lerp(HueColors.TextSecondary, HueColors.Background, glow),
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = on,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically { -it / 3 })
                },
                label = "powerTitle",
            ) { isOn ->
                Text(
                    stringResource(if (isOn) R.string.power_on else R.string.power_off),
                    style = MaterialTheme.typography.titleLarge,
                    color = HueColors.TextPrimary,
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        HueSwitch(checked = on, accent = accent)
    }
}

/** A large, springy switch. Purely visual: the surrounding card handles the input. */
@Composable
fun HueSwitch(checked: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val transition = updateTransition(checked, label = "switch")
    val thumbOffset by transition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow) },
        label = "thumb",
    ) { if (it) 28.dp else 0.dp }
    val fill by transition.animateFloat(transitionSpec = { tween(300) }, label = "fill") { if (it) 1f else 0f }
    Box(
        modifier
            .size(width = 62.dp, height = 34.dp)
            .clip(CircleShape)
            .background(HueColors.SurfaceHigh)
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(accent, HueColors.Violet)), alpha = fill)
            }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(26.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = lerp(HueColors.TextTertiary, accent, fill),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
