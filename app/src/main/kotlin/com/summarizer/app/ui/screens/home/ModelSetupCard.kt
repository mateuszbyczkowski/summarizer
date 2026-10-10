package com.summarizer.app.ui.screens.home

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.summarizer.app.R
import com.summarizer.app.domain.model.ModelSetupState
import com.summarizer.app.domain.model.SetupStopReason
import com.summarizer.app.ui.screens.models.ModelProfiles
import kotlin.math.roundToInt

/**
 * The model picked in onboarding, downloading in the background: name, percent, size and
 * Cancel; when the download stopped, why and Try again. Disappears once the model is ready.
 */
@Composable
fun ModelSetupCard(
    state: ModelSetupState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val name = ModelProfiles.technicalName(state.model)
    val stopped = state as? ModelSetupState.Stopped
    // Paused or waiting for Wi-Fi is not an error; a failure is
    val failed = stopped != null && stopped.reason in FAILURES
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (stopped == null) colors.secondaryContainer else colors.surfaceContainerHigh,
        contentColor = if (stopped == null) colors.onSecondaryContainer else colors.onSurface
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        stopped == null -> Icons.Outlined.Download
                        failed -> Icons.Outlined.ErrorOutline
                        else -> Icons.Outlined.PauseCircle
                    },
                    contentDescription = null,
                    tint = if (failed) colors.error else colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(
                        when {
                            stopped == null -> R.string.model_setup_downloading_title
                            failed -> R.string.model_setup_failed_title
                            else -> R.string.model_setup_paused_title
                        },
                        name
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
            }

            val totalBytes = state.model.sizeInMB * 1_000_000L
            when (state) {
                is ModelSetupState.Downloading -> {
                    val percent = (state.progress * 100).roundToInt()
                    val progressDescription = stringResource(R.string.model_setup_progress_cd, percent)
                    Text(
                        text = stringResource(R.string.model_setup_downloading_body),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp, end = 8.dp)
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        // The default track is the card's own color
                        trackColor = colors.onSecondaryContainer.copy(alpha = 0.16f),
                        modifier = Modifier
                            .padding(top = 12.dp, end = 8.dp)
                            .fillMaxWidth()
                            .semantics { contentDescription = progressDescription }
                    )
                    Text(
                        text = stringResource(
                            R.string.model_setup_progress,
                            percent,
                            Formatter.formatShortFileSize(context, state.downloadedBytes),
                            Formatter.formatShortFileSize(context, state.totalBytes.takeIf { it > 0 } ?: totalBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                is ModelSetupState.Stopped -> Text(
                    text = when (state.reason) {
                        SetupStopReason.WIFI_REQUIRED -> stringResource(R.string.model_setup_wifi_body)
                        SetupStopReason.NO_SPACE -> stringResource(
                            R.string.model_setup_space_body,
                            Formatter.formatShortFileSize(context, totalBytes)
                        )
                        SetupStopReason.INTERRUPTED -> stringResource(R.string.model_setup_interrupted_body)
                        SetupStopReason.PAUSED -> stringResource(R.string.model_setup_paused_body)
                        SetupStopReason.FAILED -> stringResource(R.string.model_setup_failed_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, end = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.model_setup_cancel)) }
                if (stopped != null) {
                    val resume = stopped.reason == SetupStopReason.PAUSED || stopped.reason == SetupStopReason.INTERRUPTED
                    TextButton(onClick = onRetry) {
                        Text(stringResource(if (resume) R.string.model_setup_resume else R.string.model_setup_retry))
                    }
                }
            }
        }
    }
}

private val FAILURES = setOf(SetupStopReason.FAILED, SetupStopReason.NO_SPACE)
