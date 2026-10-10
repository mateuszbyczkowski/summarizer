package com.summarizer.app.ui.screens.thread

import android.text.format.DateUtils
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
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.summarizer.app.domain.model.GenerationStage
import com.summarizer.app.domain.usecase.SummaryGenerationCoordinator
import kotlinx.coroutines.delay

/**
 * Progress of a summary being generated: the current stage (announced by TalkBack), the steps
 * done and to come, time elapsed against the usual duration, the overview as it is written,
 * a note that the user may leave, and Cancel.
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
                    ElapsedText(running.startedAt)
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
                    GenerationStage.entries.forEach { step -> StepRow(step, current = stage) }
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

@Composable
private fun ElapsedText(startedAt: Long?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = startedAt?.let { ((now - it) / 1_000).coerceAtLeast(0) } ?: 0
    Text(
        text = stringResource(R.string.thread_elapsed, DateUtils.formatElapsedTime(seconds)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Ticks every second: keep it out of TalkBack's way
        modifier = Modifier.clearAndSetSemantics { }
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
                StepStatus.CURRENT -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
    GenerationStage.LOADING_MODEL -> R.string.summary_stage_loading_model
    GenerationStage.READING -> R.string.summary_stage_reading
    GenerationStage.WRITING -> R.string.summary_stage_writing
}

private fun stepLabel(stage: GenerationStage): Int = when (stage) {
    GenerationStage.PREPARING -> R.string.thread_step_preparing
    GenerationStage.LOADING_MODEL -> R.string.thread_step_loading_model
    GenerationStage.READING -> R.string.thread_step_reading
    GenerationStage.WRITING -> R.string.thread_step_writing
}

/** Rough share of the work done when a stage starts; the stages are real, the bar is a guide. */
private fun stageProgress(stage: GenerationStage): Float = when (stage) {
    GenerationStage.PREPARING -> 0.08f
    GenerationStage.LOADING_MODEL -> 0.2f
    GenerationStage.READING -> 0.4f
    GenerationStage.WRITING -> 0.7f
}
