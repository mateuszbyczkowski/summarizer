package com.summarizer.app.ui.screens.thread

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.summarizer.app.R
import com.summarizer.app.ui.components.GroupedListDefaults
import com.summarizer.app.ui.components.rememberDateTimeFormats
import com.summarizer.app.domain.model.GenerationStage
import com.summarizer.app.domain.usecase.SummaryGenerationCoordinator
import java.time.Instant
import java.time.ZoneId

/**
 * Progress of a summary being generated: the current stage (announced by TalkBack), the steps
 * done and to come, when it started against the usual duration, the overview as it is written,
 * a note that the user may leave, and Cancel.
 *
 * Nothing animates on its own (no spinner, no ticking clock): while an on-device model reads
 * the chat the GPU is so busy that each frame can wait seconds, and a screen that keeps drawing
 * freezes until Android reports the app as not responding. The card changes only with the stage
 * and the text being written.
 */
@Composable
internal fun GenerationCard(
    running: SummaryGenerationCoordinator.State.Running,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val stage = running.stage
    val progress by animateFloatAsState(targetValue = stageProgress(stage), label = "generationProgress")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        text = stringResource(
                            if (running.startedAt == null) R.string.thread_waiting else stageTitle(stage)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        }
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 6.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StartedText(running.startedAt)
                    Text(
                        text = stringResource(R.string.thread_usual_time),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GenerationStage.entries.filter { it != GenerationStage.WAITING }
                        .forEach { step -> StepRow(step, current = stage) }
                }
                val partial = running.partialText
                if (!partial.isNullOrBlank()) {
                    Text(
                        text = partial,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = GroupedListDefaults.itemColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.thread_leave_app),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.summary_cancel_generation))
            }
        }
    }
}

/** "Started at 17:02"; empty until the job starts. */
@Composable
private fun StartedText(startedAt: Long?) {
    val formats = rememberDateTimeFormats()
    val time = startedAt?.let { formats.time(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()) }
    Text(
        text = time?.let { stringResource(R.string.thread_started_at, it) }.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StepRow(step: GenerationStage, current: GenerationStage) {
    val colors = MaterialTheme.colorScheme
    val status = when {
        step.ordinal < current.ordinal -> StepStatus.DONE
        step == current -> StepStatus.CURRENT
        else -> StepStatus.PENDING
    }
    val label = stringResource(stepLabel(step))
    val statusLabel = stringResource(
        when (status) {
            StepStatus.DONE -> R.string.thread_step_done
            StepStatus.CURRENT -> R.string.thread_step_current
            StepStatus.PENDING -> R.string.thread_step_pending
        }
    )
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$label, $statusLabel" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (status) {
                StepStatus.DONE -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.primary)
                StepStatus.CURRENT -> Icon(
                    Icons.Filled.RadioButtonChecked,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
                StepStatus.PENDING -> Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(2.dp, colors.outlineVariant, CircleShape)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (status == StepStatus.PENDING) colors.onSurfaceVariant else colors.onSurface
        )
    }
}

private enum class StepStatus { DONE, CURRENT, PENDING }

private fun stageTitle(stage: GenerationStage): Int = when (stage) {
    GenerationStage.PREPARING -> R.string.summary_stage_preparing
    GenerationStage.WAITING -> R.string.summary_stage_waiting
    GenerationStage.LOADING_MODEL -> R.string.summary_stage_loading_model
    GenerationStage.READING -> R.string.summary_stage_reading
    GenerationStage.WRITING -> R.string.summary_stage_writing
}

private fun stepLabel(stage: GenerationStage): Int = when (stage) {
    GenerationStage.PREPARING -> R.string.thread_step_preparing
    // Never listed as a step
    GenerationStage.WAITING -> R.string.summary_stage_waiting
    GenerationStage.LOADING_MODEL -> R.string.thread_step_loading_model
    GenerationStage.READING -> R.string.thread_step_reading
    GenerationStage.WRITING -> R.string.thread_step_writing
}

/** Rough share of the work done when a stage starts; the stages are real, the bar is a guide. */
private fun stageProgress(stage: GenerationStage): Float = when (stage) {
    GenerationStage.PREPARING -> 0.08f
    GenerationStage.WAITING -> 0.1f
    GenerationStage.LOADING_MODEL -> 0.2f
    GenerationStage.READING -> 0.4f
    GenerationStage.WRITING -> 0.7f
}
