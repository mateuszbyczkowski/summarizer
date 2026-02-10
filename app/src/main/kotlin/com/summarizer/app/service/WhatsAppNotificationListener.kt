package com.summarizer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.summarizer.app.R
import com.summarizer.app.data.local.entity.MessageEntity
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.usecase.AnalyzeMessageImportanceUseCase
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

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var analyzeMessageImportanceUseCase: AnalyzeMessageImportanceUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).i("🔵 NotificationListenerService onCreate() called - Service is being created!")
    }

    companion object {
        private const val TAG = "WhatsAppListener"
        private const val NOTIFICATION_CHANNEL_ID = "smart_whatsapp_notifications"
        private const val NOTIFICATION_CHANNEL_NAME = "WhatsApp Important Messages"
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

    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.tag(TAG).i("🟢 NotificationListenerService CONNECTED - Ready to receive notifications!")
        com.summarizer.app.util.ServiceHealthMonitor.recordServiceConnected(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Timber.tag(TAG).w("🔴 NotificationListenerService DISCONNECTED - Will not receive notifications!")

        // Request rebind on disconnection (Android 7.0+)
        try {
            requestRebind(android.content.ComponentName(this, WhatsAppNotificationListener::class.java))
            Timber.tag(TAG).i("🔄 Requested service rebind")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to request rebind")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        Timber.tag(TAG).i( "🔔 Notification received from package: ${sbn.packageName}")

        // Check if it's a WhatsApp notification
        if (!isWhatsAppNotification(sbn.packageName)) {
            return
        }

        Timber.tag(TAG).i( "✅ WhatsApp notification confirmed!")

        // Record that we received a notification (for service health monitoring)
        com.summarizer.app.util.ServiceHealthMonitor.recordNotificationReceived(this)

        try {
            val notification = sbn.notification
            val extras = notification.extras

            // Log ALL extras keys for complete debugging
            val allExtrasKeys = extras.keySet()?.joinToString(", ") ?: "none"
            Timber.tag(TAG).d( "🔍 Available extras keys: $allExtrasKeys")

            // Extract ALL notification content for debugging
            val title = extras.getCharSequence("android.title")?.toString()
            val text = extras.getCharSequence("android.text")?.toString()
            val bigText = extras.getCharSequence("android.bigText")?.toString()
            val subText = extras.getCharSequence("android.subText")?.toString()
            val infoText = extras.getCharSequence("android.infoText")?.toString()
            val summaryText = extras.getCharSequence("android.summaryText")?.toString()

            // Also check for conversation data (Android 11+)
            val conversationTitle = extras.getCharSequence("android.conversationTitle")?.toString()

            Timber.tag(TAG).i( """
                |📱 WHATSAPP NOTIFICATION:
                |   Title: '$title'
                |   Text: '$text'
                |   BigText: '$bigText'
                |   SubText: '$subText'
                |   InfoText: '$infoText'
                |   SummaryText: '$summaryText'
                |   ConversationTitle: '$conversationTitle'
                |   Time: ${sbn.postTime}
                |   NotificationKey: ${sbn.key}
                |   GroupKey: ${sbn.groupKey}
            """.trimMargin())

            if (title.isNullOrBlank() && text.isNullOrBlank()) {
                Timber.tag(TAG).w( "⚠️ Empty notification, skipping")
                return
            }

            // Use bigText if available (contains full message for long texts)
            val messageText = bigText ?: text ?: ""
            val notificationTitle = title ?: ""

            // Skip summary notifications (e.g., "8 new messages")
            if (isSummaryNotification(messageText, summaryText)) {
                Timber.tag(TAG).d( "⚠️ Skipping summary notification: '$messageText'")
                return
            }

            // TEMPORARILY: Accept ALL WhatsApp messages for debugging
            // TODO: Re-enable group filtering after debugging
            val isGroup = true // isGroupMessage(notificationTitle, messageText)
            Timber.tag(TAG).i( "📝 Processing message (isGroup check: $isGroup)")

            // Parse and save message
            Timber.tag(TAG).i( "💾 Attempting to save to database...")
            parseAndSaveGroupMessage(notificationTitle, messageText, sbn.postTime)
            Timber.tag(TAG).i( "✅ Message saved successfully!")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ ERROR processing notification")
            e.printStackTrace()
        }
    }

    private fun isSummaryNotification(text: String, summaryText: String?): Boolean {
        // WhatsApp sends summary notifications like "8 new messages" when there are multiple unread messages
        // These should be ignored as they don't contain actual message content

        val lowerText = text.lowercase().trim()

        // Pattern 1: "X new messages" or "X messages"
        val newMessagesPattern = Regex("^\\d+\\s+(new\\s+)?messages?$")
        if (newMessagesPattern.matches(lowerText)) {
            return true
        }

        // Pattern 2: SummaryText matches the same pattern
        if (!summaryText.isNullOrBlank()) {
            val lowerSummary = summaryText.lowercase().trim()
            if (newMessagesPattern.matches(lowerSummary)) {
                return true
            }
        }

        // Pattern 3: Text is exactly the same as summaryText (often indicates a summary)
        if (!summaryText.isNullOrBlank() && text.trim() == summaryText.trim()) {
            return true
        }

        return false
    }

    private fun isGroupMessage(title: String, text: String): Boolean {
        // WhatsApp group messages have format:
        // Title: "Group Name" (no colon usually)
        // Text: "Sender: Message content" (colon after sender name)

        // Very permissive check - if text has a colon anywhere, likely a group message
        // Personal chats don't have "Sender:" format
        val textHasColon = text.contains(":")
        val notUrl = !title.startsWith("http") && !text.startsWith("http")

        // Additional indicators
        val hasGroupIndicator = text.contains("@") || // Mentions
                               text.contains("messages") || // "N new messages"
                               title.contains("(") // Group names often have parens

        val isGroup = (textHasColon && notUrl) || hasGroupIndicator

        Timber.tag(TAG).d( "isGroupMessage - Title: '$title', Text: '$text', HasColon: $textHasColon, IsGroup: $isGroup")
        return isGroup
    }

    private fun parseAndSaveGroupMessage(title: String, text: String, timestamp: Long) {
        serviceScope.launch {
            try {
                // Extract group name and sender
                val (groupName, sender, messageContent) = parseNotificationContent(title, text)

                if (groupName.isBlank() || messageContent.isBlank()) {
                    Timber.tag(TAG).w( "Invalid group message, skipping")
                    return@launch
                }

                // Detect message type
                val messageType = detectMessageType(messageContent)
                val isDeleted = isDeletedMessage(messageContent)

                // Generate thread ID (hash of normalized group name)
                // Normalize to handle slight variations in group name
                val normalizedGroupName = normalizeGroupName(groupName)
                val threadId = normalizedGroupName.hashCode().toString()

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
                    Timber.tag(TAG).d( "Created new thread: $groupName")
                } else {
                    val newCount = existingThread.messageCount + 1
                    threadRepository.updateThreadStats(threadId, newCount, timestamp)
                    Timber.tag(TAG).d( "Updated thread: $groupName (messages: $newCount)")
                }

                Timber.tag(TAG).d( "Processed message from $sender in $groupName (type: $messageType)")

                // Smart Notifications: Analyze importance and show notification if needed
                handleSmartNotification(groupName, sender, messageContent, timestamp)

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error saving message: ${e.message}")
            }
        }
    }

    /**
     * Handle smart notification - analyze message importance and show notification if important enough.
     */
    private suspend fun handleSmartNotification(
        threadName: String,
        senderName: String,
        messageContent: String,
        timestamp: Long
    ) {
        try {
            val smartNotificationsEnabled = preferencesRepository.isSmartNotificationsEnabled()

            if (!smartNotificationsEnabled) {
                Timber.tag(TAG).d("Smart notifications disabled, skipping importance analysis")
                return
            }

            Timber.tag(TAG).d("Analyzing message importance...")
            val shouldNotify = analyzeMessageImportanceUseCase.shouldNotify(
                messageContent = messageContent,
                senderName = senderName
            )

            if (shouldNotify) {
                // Get the full importance score to determine notification priority
                val importanceScore = analyzeMessageImportanceUseCase.execute(messageContent, senderName) ?: 0.5f
                showSmartNotification(threadName, senderName, messageContent, importanceScore)
                Timber.tag(TAG).i("✅ Showed notification for important message (score: $importanceScore)")
            } else {
                Timber.tag(TAG).d("⏭️ Message not important enough, notification suppressed")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in smart notification handling, showing notification as fallback")
            // Fallback: show notification on error to avoid missing important messages
            showSmartNotification(threadName, senderName, messageContent, 0.7f)
        }
    }

    /**
     * Show a smart notification with appropriate priority based on importance score.
     */
    private fun showSmartNotification(
        threadName: String,
        senderName: String,
        messageContent: String,
        importanceScore: Float
    ) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Create notification channel if needed
            createNotificationChannel(notificationManager)

            // Determine priority based on importance score
            // 0.0-0.6: Default priority
            // 0.6-0.8: High priority
            // 0.8-1.0: Max priority (heads-up)
            val priority = when {
                importanceScore >= 0.8f -> NotificationCompat.PRIORITY_MAX
                importanceScore >= 0.6f -> NotificationCompat.PRIORITY_HIGH
                else -> NotificationCompat.PRIORITY_DEFAULT
            }

            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(threadName)
                .setContentText("$senderName: $messageContent")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("$senderName: $messageContent"))
                .setPriority(priority)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()

            // Use timestamp as notification ID to allow multiple notifications
            val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            notificationManager.notify(notificationId, notification)

            Timber.tag(TAG).d("Notification shown with priority: $priority (score: $importanceScore)")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error showing smart notification")
        }
    }

    /**
     * Create notification channel for smart notifications.
     */
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important WhatsApp messages filtered by AI"
            enableVibration(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(channel)
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
            Timber.tag(TAG).e(e, "Error parsing notification content")
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

    private fun normalizeGroupName(groupName: String): String {
        // Normalize group name to ensure consistent thread IDs
        var normalized = groupName.trim()

        // Remove message count suffix patterns like "(5 messages)", "(1 message)", etc.
        normalized = normalized.replace(Regex("\\s*\\(\\d+\\s+messages?\\)\\s*$", RegexOption.IGNORE_CASE), "")

        // Remove other common notification patterns
        normalized = normalized.replace(Regex("\\s*\\(\\d+\\s+new\\)\\s*$", RegexOption.IGNORE_CASE), "")
        normalized = normalized.replace(Regex("\\s*\\[\\d+\\s+messages?\\]\\s*$", RegexOption.IGNORE_CASE), "")

        return normalized
            .trim()                          // Remove leading/trailing whitespace again
            .replace(Regex("\\s+"), " ")     // Normalize multiple spaces to single space
            .lowercase()                      // Case-insensitive grouping
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
