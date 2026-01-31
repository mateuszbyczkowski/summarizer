package com.summarizer.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.summarizer.app.data.local.entity.MessageEntity
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WhatsAppNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var messageRepository: MessageRepository

    @Inject
    lateinit var threadRepository: ThreadRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "WhatsAppListener"
        private val DELETED_MESSAGE_PATTERNS = listOf(
            "This message was deleted",
            "You deleted this message",
            "Message deleted",
            "🚫 This message was deleted"
        )
        private val MEDIA_INDICATORS = listOf(
            "📷 Photo",
            "📹 Video",
            "🎵 Audio",
            "📄 Document",
            "📍 Location",
            "👤 Contact",
            "image omitted",
            "video omitted",
            "audio omitted",
            "document omitted",
            "GIF omitted",
            "sticker omitted"
        )
        private val SYSTEM_MESSAGE_PATTERNS = listOf(
            "created group",
            "added",
            "removed",
            "left",
            "changed the subject",
            "changed this group's icon",
            "You're now an admin",
            "changed the group description"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // Check if it's a WhatsApp notification
        if (!isWhatsAppNotification(sbn.packageName)) {
            return
        }

        try {
            val notification = sbn.notification
            val extras = notification.extras

            // Extract notification content
            val title = extras.getCharSequence("android.title")?.toString()
            val text = extras.getCharSequence("android.text")?.toString()
            val bigText = extras.getCharSequence("android.bigText")?.toString()

            if (title.isNullOrBlank() && text.isNullOrBlank()) {
                Timber.d(TAG, "Empty notification, skipping")
                return
            }

            // Use bigText if available (contains full message for long texts)
            val messageText = bigText ?: text ?: ""
            val notificationTitle = title ?: ""

            // Check if it's a group message
            if (!isGroupMessage(notificationTitle, messageText)) {
                Timber.d(TAG, "Skipping personal/non-group message")
                return
            }

            // Parse and save group message
            parseAndSaveGroupMessage(notificationTitle, messageText, sbn.postTime)

        } catch (e: Exception) {
            Timber.e(TAG, e, "Error processing notification: ${e.message}")
        }
    }

    private fun isGroupMessage(title: String, text: String): Boolean {
        // Group messages typically have ":" in title or text
        // But we need to be careful not to match URLs or other colons
        return when {
            title.contains(":") && !title.startsWith("http") -> true
            text.contains(":") && text.indexOf(":") < 50 -> true
            else -> false
        }
    }

    private fun parseAndSaveGroupMessage(title: String, text: String, timestamp: Long) {
        serviceScope.launch {
            try {
                // Extract group name and sender
                val (groupName, sender, messageContent) = parseNotificationContent(title, text)

                if (groupName.isBlank() || messageContent.isBlank()) {
                    Timber.w(TAG, "Invalid group message, skipping")
                    return@launch
                }

                // Detect message type
                val messageType = detectMessageType(messageContent)
                val isDeleted = isDeletedMessage(messageContent)

                // Generate thread ID (hash of group name)
                val threadId = groupName.hashCode().toString()

                // Generate message hash for deduplication
                val messageHash = MessageEntity.generateHash(threadId, sender, messageContent, timestamp)

                // Create message object
                val message = Message(
                    threadId = threadId,
                    threadName = groupName,
                    sender = sender,
                    content = messageContent,
                    timestamp = timestamp,
                    messageHash = messageHash,
                    messageType = messageType,
                    isDeleted = isDeleted
                )

                // Check for duplicate before saving
                // The database will handle this with IGNORE conflict strategy
                messageRepository.saveMessage(message)

                // Create or update thread
                val existingThread = threadRepository.getThread(threadId)
                if (existingThread == null) {
                    val newThread = Thread(
                        threadId = threadId,
                        threadName = groupName,
                        messageCount = 1,
                        lastMessageTimestamp = timestamp
                    )
                    threadRepository.saveThread(newThread)
                    Timber.d(TAG, "Created new thread: $groupName")
                } else {
                    val newCount = existingThread.messageCount + 1
                    threadRepository.updateThreadStats(threadId, newCount, timestamp)
                    Timber.d(TAG, "Updated thread: $groupName (messages: $newCount)")
                }

                Timber.d(TAG, "Processed message from $sender in $groupName (type: $messageType)")

            } catch (e: Exception) {
                Timber.e(TAG, e, "Error saving message: ${e.message}")
            }
        }
    }

    private fun parseNotificationContent(title: String, text: String): Triple<String, String, String> {
        return try {
            when {
                // Format 1: "GroupName: SenderName" in title, message in text
                title.contains(":") && !title.startsWith("http") -> {
                    val parts = title.split(":", limit = 2)
                    val group = parts[0].trim()
                    val sender = parts.getOrNull(1)?.trim() ?: "Unknown"
                    Triple(group, sender, text.trim())
                }
                // Format 2: "SenderName: Message" in text, group in title
                text.contains(":") && text.indexOf(":") < 50 -> {
                    val parts = text.split(":", limit = 2)
                    val sender = parts[0].trim()
                    val content = parts.getOrNull(1)?.trim() ?: text
                    Triple(title.trim(), sender, content)
                }
                // Format 3: Fallback - use title as group, try to parse sender from text
                else -> {
                    Triple(title.trim(), "Unknown", text.trim())
                }
            }
        } catch (e: Exception) {
            Timber.e(TAG, e, "Error parsing notification content")
            Triple("Unknown Group", "Unknown", text)
        }
    }

    private fun detectMessageType(content: String): MessageType {
        return when {
            isDeletedMessage(content) -> MessageType.DELETED
            isSystemMessage(content) -> MessageType.SYSTEM
            MEDIA_INDICATORS.any { content.contains(it, ignoreCase = true) } -> {
                when {
                    content.contains("Photo", ignoreCase = true) ||
                    content.contains("image", ignoreCase = true) -> MessageType.IMAGE
                    content.contains("Video", ignoreCase = true) -> MessageType.VIDEO
                    content.contains("Audio", ignoreCase = true) -> MessageType.AUDIO
                    content.contains("Document", ignoreCase = true) -> MessageType.DOCUMENT
                    content.contains("Location", ignoreCase = true) -> MessageType.LOCATION
                    content.contains("Contact", ignoreCase = true) -> MessageType.CONTACT
                    content.contains("sticker", ignoreCase = true) -> MessageType.STICKER
                    else -> MessageType.UNKNOWN
                }
            }
            else -> MessageType.TEXT
        }
    }

    private fun isDeletedMessage(content: String): Boolean {
        return DELETED_MESSAGE_PATTERNS.any {
            content.contains(it, ignoreCase = true)
        }
    }

    private fun isSystemMessage(content: String): Boolean {
        return SYSTEM_MESSAGE_PATTERNS.any {
            content.contains(it, ignoreCase = true)
        }
    }

    private fun isWhatsAppNotification(packageName: String): Boolean {
        return packageName == Constants.WHATSAPP_PACKAGE ||
               packageName == Constants.WHATSAPP_BUSINESS_PACKAGE
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
