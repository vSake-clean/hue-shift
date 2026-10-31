package io.github.vsakeclean.hueshift.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.engine.EngineKind
import io.github.vsakeclean.hueshift.filter.Filters
import io.github.vsakeclean.hueshift.filter.HueFilter
import io.github.vsakeclean.hueshift.ui.ScreenCompensation
import io.github.vsakeclean.hueshift.ui.swatchColors
import io.github.vsakeclean.hueshift.ui.theme.HueColors

/** All filter cards in a responsive grid (2 columns on phones, 3 on wide screens). */
@Composable
fun FilterGrid(state: HueState, onSelect: (HueFilter, locked: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val compensation = ScreenCompensation.forState(state)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 560.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Filters.all.chunked(columns).forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { filter ->
                        val engine = state.filterEngines[filter.id]
                        val selected = if (filter.isOff) !state.enabled else state.enabled && state.selected == filter
                        val locked = !filter.isOff && engine == null && state.bestEngine != null
                        val intensity = state.settings.intensityOf(filter)
                        FilterCard(
                            filter = filter,
                            selected = selected,
                            locked = locked,
                            approximate = engine == EngineKind.OVERLAY,
                            swatch = swatchColors(filter, intensity, engine, compensation),
                            onClick = { onSelect(filter, locked) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun FilterCard(
    filter: HueFilter,
    selected: Boolean,
    locked: Boolean,
    approximate: Boolean,
    swatch: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(filter.accent)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "cardScale",
    )
    val borderColor by animateColorAsState(if (selected) accent else HueColors.Outline, tween(300), label = "cardBorder")
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(300), label = "cardBorderWidth")
    val container by animateColorAsState(
        if (selected) HueColors.SurfaceRaised else HueColors.Surface,
        tween(300),
        label = "cardContainer",
    )
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(450), label = "cardGlow")
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(container)
            .drawBehind {
                if (glow > 0f) {
                    val center = Offset(size.width * 0.9f, 0f)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.24f * glow), Color.Transparent),
                            center = center,
                            radius = size.width,
                        ),
                        radius = size.width,
                        center = center,
                    )
                }
            }
            .border(borderWidth, borderColor, shape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = ripple(color = accent),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(filter.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            SelectionBadge(selected = selected, locked = locked, accent = accent)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(filter.title),
            style = MaterialTheme.typography.titleMedium,
            color = HueColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(filter.description),
            style = MaterialTheme.typography.bodySmall,
            color = HueColors.TextSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        // Pushes the swatch to the bottom, so all cards in a row line up.
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(swatch)),
        )
        if (locked || approximate) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (locked) R.string.filter_needs_full_mode else R.string.filter_approximation),
                style = MaterialTheme.typography.labelSmall,
                color = if (locked) HueColors.Warning else HueColors.TextTertiary,
                maxLines = 2,
            )
        }
    }
}

/** Check mark for the selected card, padlock for filters the current mode cannot draw. */
@Composable
private fun SelectionBadge(selected: Boolean, locked: Boolean, accent: Color) {
    Box(contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) + fadeIn(),
            exit = scaleOut(tween(150)) + fadeOut(tween(150)),
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = HueColors.Background,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (locked && !selected) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.filter_needs_full_mode),
                tint = HueColors.TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
