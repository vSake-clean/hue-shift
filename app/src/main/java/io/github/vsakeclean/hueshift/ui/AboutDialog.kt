package io.github.vsakeclean.hueshift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vsakeclean.hueshift.BuildConfig
import io.github.vsakeclean.hueshift.R
import io.github.vsakeclean.hueshift.ui.components.HueLogo
import io.github.vsakeclean.hueshift.ui.theme.HueColors

const val SOURCE_URL = "https://github.com/vSake-clean/hue-shift"

@Composable
fun AboutDialog(onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { HueLogo(size = 52.dp) },
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME) + " · " +
                        stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = HueColors.TextTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = {
            TextButton(onClick = { onOpenUrl(SOURCE_URL) }) { Text(stringResource(R.string.about_source)) }
        },
        containerColor = HueColors.SurfaceRaised,
        titleContentColor = HueColors.TextPrimary,
        textContentColor = HueColors.TextSecondary,
    )
}
