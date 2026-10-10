package com.summarizer.app.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Text with some spans marked, e.g. dates in a summary: each span is drawn medium weight on a
 * small rounded tonal background ([markColor], by default a faint tint of the text colour).
 *
 * @param marks Character ranges of [text] to mark; out-of-range or overlapping ranges are ignored
 */
@Composable
fun HighlightedText(
    text: String,
    marks: List<IntRange>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    markColor: Color = color.copy(alpha = 0.12f)
) {
    val valid = remember(text, marks) {
        marks.filter { it.first >= 0 && it.last < text.length && !it.isEmpty() }
            .sortedBy { it.first }
            .fold(mutableListOf<IntRange>()) { kept, range ->
                if (kept.none { it.last >= range.first }) kept += range
                kept
            }
    }
    val annotated = remember(text, valid) {
        buildAnnotatedString {
            append(text)
            valid.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Medium), it.first, it.last + 1) }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val result = layout ?: return@drawBehind
            if (result.layoutInput.text.text != text) return@drawBehind
            val padX = 3.dp.toPx()
            val padY = 1.dp.toPx()
            val radius = CornerRadius(6.dp.toPx())
            valid.forEach { range ->
                val end = range.last + 1
                val firstLine = result.getLineForOffset(range.first)
                val lastLine = result.getLineForOffset(range.last)
                for (line in firstLine..lastLine) {
                    val left = if (line == firstLine) result.getHorizontalPosition(range.first, true) else result.getLineLeft(line)
                    // The end of a span that ends a wrapped line belongs to the next line
                    val endsHere = line == lastLine && end < text.length && result.getLineForOffset(end) == line
                    val right = if (endsHere) result.getHorizontalPosition(end, true) else result.getLineRight(line)
                    val top = result.getLineTop(line)
                    val bottom = result.getLineBottom(line)
                    val x = minOf(left, right) - padX
                    drawRoundRect(
                        color = markColor,
                        topLeft = Offset(x, top + padY),
                        size = Size(kotlin.math.abs(right - left) + 2 * padX, bottom - top - 2 * padY),
                        cornerRadius = radius
                    )
                }
            }
        }
    )
}
