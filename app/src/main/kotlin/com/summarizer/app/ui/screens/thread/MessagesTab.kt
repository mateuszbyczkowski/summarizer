package com.summarizer.app.ui.screens.thread

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.summarizer.app.R
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType
import com.summarizer.app.ui.components.EmptyState
import com.summarizer.app.ui.components.ThreadAvatar
import java.time.LocalDate
import kotlin.math.absoluteValue

private val AvatarSize = 32.dp
private val BubbleRadius = 18.dp
private val GroupedRadius = 4.dp

/**
 * The chat as bubbles: grouped by sender, the user's own messages on the right, day headers,
 * and a "New since summary" divider.
 */
@Composable
internal fun MessagesTab(
    timeline: List<TimelineItem>,
    today: LocalDate,
    listState: LazyListState,
    bottomPadding: Dp
) {
    if (timeline.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Outlined.Chat,
            title = stringResource(R.string.thread_messages_empty_title),
            body = stringResource(R.string.thread_messages_empty_body),
            modifier = Modifier.padding(bottom = bottomPadding)
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomPadding + 88.dp)
    ) {
        items(timeline, key = { it.key }, contentType = { it::class }) { item ->
            when (item) {
                is TimelineItem.DateSeparator -> DayHeader(dayHeader(item.date, today))
                TimelineItem.NewDivider -> NewDivider()
                is TimelineItem.Bubble -> when {
                    item.isSystem -> SystemMessage(item.message)
                    else -> MessageBubble(item)
                }
            }
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .semantics { heading() }
            )
        }
    }
}

@Composable
private fun NewDivider() {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = color)
        Text(
            text = stringResource(R.string.thread_new_since_summary),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.semantics { heading() }
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = color)
    }
}

@Composable
private fun SystemMessage(message: Message) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageBubble(item: TimelineItem.Bubble) {
    val message = item.message
    val colors = MaterialTheme.colorScheme
    val deleted = message.isDeleted || message.messageType == MessageType.DELETED
    val container = when {
        deleted -> Color.Transparent
        item.fromMe -> colors.primaryContainer
        else -> colors.surfaceContainerHigh
    }
    val content = when {
        deleted -> colors.onSurfaceVariant
        item.fromMe -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    // The corner next to the neighbouring bubble of the same group is tighter
    val near = if (item.fromMe) "end" else "start"
    val shape = RoundedCornerShape(
        topStart = if (near == "start" && !item.firstInGroup) GroupedRadius else BubbleRadius,
        topEnd = if (near == "end" && !item.firstInGroup) GroupedRadius else BubbleRadius,
        bottomStart = if (near == "start" && !item.lastInGroup) GroupedRadius else BubbleRadius,
        bottomEnd = if (near == "end" && !item.lastInGroup) GroupedRadius else BubbleRadius
    )
    val sender = if (item.fromMe) stringResource(R.string.thread_sender_you) else message.sender
    val time = timeOfDay(message.timestamp)
    val media = mediaLabel(message.messageType)
    val caption = MessageTimeline.mediaCaption(message)
    val spokenText = listOfNotNull(media?.let { stringResource(it.second) }, if (media != null) caption else message.content)
        .joinToString(". ")
    val description = stringResource(R.string.thread_message_cd, sender, time, spokenText)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (item.firstInGroup) 8.dp else 2.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = if (item.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!item.fromMe) {
            if (item.firstInGroup) {
                ThreadAvatar(name = message.sender, size = AvatarSize)
            } else {
                Spacer(modifier = Modifier.size(AvatarSize))
            }
            Spacer(modifier = Modifier.size(8.dp))
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
        Surface(
            shape = shape,
            color = container,
            contentColor = content,
            border = if (deleted) BorderStroke(1.dp, colors.outlineVariant) else null,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                if (item.firstInGroup && !item.fromMe) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelLarge,
                        color = senderColor(message.sender),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when {
                    deleted -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
                        )
                    }
                    media != null -> {
                        MediaPlaceholder(icon = media.first, label = stringResource(media.second), fromMe = item.fromMe)
                        if (caption != null) {
                            Text(text = caption, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    else -> Text(text = message.content, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp)
                )
            }
        }
        if (!item.fromMe) Spacer(modifier = Modifier.size(32.dp))
    }
}

/** Media is not stored, only its kind: a tonal tile with an icon stands in for it. */
@Composable
private fun MediaPlaceholder(icon: ImageVector, label: String, fromMe: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (fromMe) colors.primary.copy(alpha = 0.12f) else colors.surfaceContainerHighest,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun mediaLabel(type: MessageType): Pair<ImageVector, Int>? = when (type) {
    MessageType.IMAGE -> Icons.Outlined.Image to R.string.thread_media_image
    MessageType.VIDEO -> Icons.Outlined.Videocam to R.string.thread_media_video
    MessageType.AUDIO -> Icons.Outlined.Mic to R.string.thread_media_audio
    MessageType.DOCUMENT -> Icons.Outlined.Description to R.string.thread_media_document
    MessageType.LOCATION -> Icons.Outlined.Place to R.string.thread_media_location
    MessageType.CONTACT -> Icons.Outlined.Person to R.string.thread_media_contact
    MessageType.STICKER -> Icons.Outlined.EmojiEmotions to R.string.thread_media_sticker
    MessageType.TEXT, MessageType.DELETED, MessageType.SYSTEM, MessageType.UNKNOWN -> null
}

/** A stable colour per sender, so names are easier to tell apart in a busy group. */
@Composable
private fun senderColor(sender: String): Color {
    val colors = MaterialTheme.colorScheme
    return when (sender.hashCode().absoluteValue % 3) {
        0 -> colors.primary
        1 -> colors.tertiary
        else -> colors.secondary
    }
}
