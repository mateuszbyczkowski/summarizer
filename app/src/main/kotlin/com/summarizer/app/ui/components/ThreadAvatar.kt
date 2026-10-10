package com.summarizer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.summarizer.app.util.ThreadNaming
import kotlin.math.absoluteValue

/**
 * Round tonal avatar with the first letter or digit of a thread's name. The tone (primary,
 * secondary or tertiary container) is derived from the name, so a chat keeps its colour.
 * Decorative: the row around it announces the name.
 */
@Composable
fun ThreadAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val (container, content) = avatarColors(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ThreadNaming.initial(name),
            style = MaterialTheme.typography.titleMedium,
            color = content
        )
    }
}

@Composable
private fun avatarColors(name: String): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (name.hashCode().absoluteValue % 3) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
}
