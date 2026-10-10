package com.summarizer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Day of the month over a short weekday ("17 / FRI"), tinted with the error container when
 * [urgent]. Decorative: the surrounding row reads the date out.
 */
@Composable
fun AgendaDateBadge(
    date: LocalDate,
    urgent: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier.size(width = 40.dp, height = 44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (urgent) MaterialTheme.colorScheme.errorContainer else containerColor,
        contentColor = if (urgent) MaterialTheme.colorScheme.onErrorContainer else contentColor
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
            Text(text = weekdayShort(date), style = MaterialTheme.typography.labelSmall)
        }
    }
}
