package io.github.vsakeclean.hueshift.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vsakeclean.hueshift.ui.theme.HueColors

/** The standard hue-shift card look: dark surface, hairline border, large corners. */
fun Modifier.hueCard(
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = HueColors.Surface,
    borderColor: Color = HueColors.Outline,
): Modifier = this
    .clip(shape)
    .background(color)
    .border(1.dp, borderColor, shape)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = HueColors.TextTertiary,
        modifier = modifier.padding(start = 4.dp),
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** Small rounded label, e.g. "Recommended". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, dot: Boolean = false) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) StatusDot(color, size = 7.dp)
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** The hue-shift mark: half colour wheel, half grayscale. */
@Composable
fun HueLogo(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Canvas(modifier.size(size)) {
        val gap = this.size.minDimension * 0.035f
        val diameter = this.size.minDimension - 2 * gap
        val arcSize = Size(diameter, diameter)
        val wheel = Brush.sweepGradient(
            0f to HueColors.Pink,
            0.25f to HueColors.Pink,
            0.4f to Color(0xFFFFA94D),
            0.5f to Color(0xFFFFE066),
            0.62f to HueColors.Teal,
            0.75f to HueColors.Violet,
            1f to HueColors.Pink,
            center = Offset(diameter / 2f, gap + diameter / 2f),
        )
        drawArc(
            brush = wheel,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(0f, gap),
            size = arcSize,
        )
        drawArc(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFF4F4F8), Color(0xFF4A4A5E)),
                startY = gap,
                endY = gap + diameter,
            ),
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(2 * gap, gap),
            size = arcSize,
        )
    }
}
