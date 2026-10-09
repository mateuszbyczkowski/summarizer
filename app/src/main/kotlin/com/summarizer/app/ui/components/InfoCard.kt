package com.summarizer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Visual tone of an [InfoCard]; picks the container colour and default icon. */
enum class InfoCardTone {
    Info,
    Tip,
    Success,
    Warning,
    Neutral;

    val defaultIcon: ImageVector
        get() = when (this) {
            Info, Neutral -> Icons.Outlined.Info
            Tip -> Icons.Outlined.Lightbulb
            Success -> Icons.Outlined.CheckCircle
            Warning -> Icons.Outlined.WarningAmber
        }
}

/**
 * A compact, tinted callout with a leading icon, an optional [title] and a [text] body.
 */
@Composable
fun InfoCard(
    text: String,
    modifier: Modifier = Modifier,
    tone: InfoCardTone = InfoCardTone.Info,
    title: String? = null,
    icon: ImageVector = tone.defaultIcon
) {
    val colors = MaterialTheme.colorScheme
    val (containerColor, contentColor) = when (tone) {
        InfoCardTone.Warning -> colors.errorContainer to colors.onErrorContainer
        InfoCardTone.Neutral -> colors.secondaryContainer to colors.onSecondaryContainer
        InfoCardTone.Info, InfoCardTone.Tip, InfoCardTone.Success ->
            colors.tertiaryContainer to colors.onTertiaryContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
