package com.summarizer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Filled count such as new messages; larger than [androidx.compose.material3.Badge] for list rows. */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Surface(
        modifier = modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(text = if (count > 999) "999+" else count.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Small non-interactive chip, e.g. "2 tasks" on a thread row. */
@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer
) {
    Surface(
        modifier = modifier.height(24.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
