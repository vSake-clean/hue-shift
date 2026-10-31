package io.github.vsakeclean.hueshift.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.core.HueState
import io.github.vsakeclean.hueshift.ui.PreviewEffect
import io.github.vsakeclean.hueshift.ui.animatePreviewEffect
import io.github.vsakeclean.hueshift.ui.previewEffect
import io.github.vsakeclean.hueshift.ui.theme.HueColors

private enum class PreviewStatus { LIVE, SIMULATED, NORMAL }

/** The small window at the top showing how the screen looks with the chosen filter. */
@Composable
fun PreviewCard(state: HueState, modifier: Modifier = Modifier) {
    val effect by animatePreviewEffect(PreviewEffect.forPreview(state))
    val status = when {
        state.isAppliedSystemWide -> PreviewStatus.LIVE
        state.enabled -> PreviewStatus.SIMULATED
        else -> PreviewStatus.NORMAL
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.preview_label), Modifier.weight(1f))
            AnimatedContent(
                targetState = status,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "previewStatus",
            ) { current ->
                when (current) {
                    PreviewStatus.LIVE -> Pill(stringResource(R.string.preview_status_live), HueColors.Success, dot = true)
                    PreviewStatus.SIMULATED -> Pill(stringResource(R.string.preview_status_simulated), HueColors.Warning, dot = true)
                    PreviewStatus.NORMAL -> Pill(stringResource(R.string.preview_status_normal), HueColors.TextSecondary, dot = true)
                }
            }
        }
        val shape = RoundedCornerShape(28.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
                .clip(shape)
                .border(1.dp, HueColors.Outline, shape),
        ) {
            PreviewScene(Modifier.fillMaxSize().previewEffect { effect })
        }
    }
}

/** A colourful mock home screen: every filter is easy to judge on it. */
@Composable
private fun PreviewScene(modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) { drawWallpaper() }

        // Status bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.preview_time),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            listOf(Icons.Rounded.SignalCellularAlt, Icons.Rounded.Wifi, Icons.Rounded.BatteryFull).forEach {
                Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }

        // Notification
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 34.dp)
                .width(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.93f))
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34C759)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ChatBubble, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    stringResource(R.string.preview_contact),
                    color = Color(0xFF1C1C1E),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.preview_message),
                    color = Color(0xFF48484A),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Weather
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 32.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("23°", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
            }
            Text(stringResource(R.string.preview_weather), color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
        }

        // Dock
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            AppIcon(Color(0xFFFF3B30), Icons.Rounded.Call)
            AppIcon(Color(0xFFFF9500), Icons.Rounded.PhotoCamera)
            AppIcon(Color(0xFFFFCC00), Icons.Rounded.WbSunny)
            AppIcon(Color(0xFF34C759), Icons.Rounded.Map)
            AppIcon(Color(0xFF0A84FF), Icons.Rounded.Public)
            AppIcon(Color(0xFFBF5AF2), Icons.Rounded.MusicNote)
        }
    }
}

@Composable
private fun AppIcon(color: Color, icon: ImageVector) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.25f), color))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

private fun DrawScope.drawWallpaper() {
    val w = size.width
    val h = size.height
    // Sunset sky
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF1D2B64),
            0.42f to Color(0xFF7F53AC),
            0.72f to Color(0xFFFF6F91),
            1f to Color(0xFFFFC371),
        ),
    )
    // Sun with a soft glow
    val sun = Offset(w * 0.68f, h * 0.6f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0x80FFE29A), Color.Transparent), center = sun, radius = h * 0.5f),
        radius = h * 0.5f,
        center = sun,
    )
    drawCircle(Color(0xFFFFE29A), radius = h * 0.12f, center = sun)
    // Distant mountains
    val mountains = Path().apply {
        moveTo(0f, h * 0.7f)
        lineTo(w * 0.16f, h * 0.53f)
        lineTo(w * 0.31f, h * 0.66f)
        lineTo(w * 0.5f, h * 0.47f)
        lineTo(w * 0.72f, h * 0.69f)
        lineTo(w * 0.88f, h * 0.57f)
        lineTo(w, h * 0.64f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(mountains, Brush.verticalGradient(listOf(Color(0xFF5B45A8), Color(0xFF2E2A6B)), startY = h * 0.47f, endY = h))
    // Green hills
    val hills = Path().apply {
        moveTo(0f, h * 0.8f)
        cubicTo(w * 0.2f, h * 0.68f, w * 0.4f, h * 0.86f, w * 0.62f, h * 0.76f)
        cubicTo(w * 0.8f, h * 0.68f, w * 0.92f, h * 0.78f, w, h * 0.74f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(hills, Brush.verticalGradient(listOf(Color(0xFF2ECC71), Color(0xFF0E7C66)), startY = h * 0.68f, endY = h))
}
