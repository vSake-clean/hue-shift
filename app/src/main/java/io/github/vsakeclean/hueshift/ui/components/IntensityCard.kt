package io.github.vsakeclean.hueshift.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.filter.HueFilter
import io.github.vsakeclean.hueshift.settings.HueSettings
import io.github.vsakeclean.hueshift.ui.theme.HueColors
import kotlin.math.roundToInt

@Composable
fun IntensityCard(
    filter: HueFilter,
    intensity: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(Color(filter.accent), tween(400), label = "intensityAccent")
    val title = stringResource(R.string.intensity_title)
    Column(
        modifier
            .fillMaxWidth()
            .hueCard()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.intensity_value, (intensity * 100).roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
        }
        Slider(
            value = intensity,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = HueSettings.MIN_INTENSITY..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = HueColors.SurfaceHigh,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}
