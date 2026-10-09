package com.summarizer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.summarizer.app.MainActivity
import com.summarizer.app.R
import com.summarizer.app.data.local.entity.MessageEntity
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.model.MessageType
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.usecase.AnalyzeMessageImportanceUseCase
import com.summarizer.app.util.Constants
import com.summarizer.app.util.ServiceHealthMonitor
import com.summarizer.app.util.ThreadNaming
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
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var analyzeMessageImportanceUseCase: AnalyzeMessageImportanceUseCase

    // Single-threaded so messages are stored in order and thread stats never race
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    // Importance analysis may wait for the AI engine; it must never hold up storing messages
    private val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).i("NotificationListenerService created")
    }

    companion object {
        private const val TAG = "WhatsAppListener"
        // Older messages (e.g. history re-sent in a notification) never trigger a smart notification
        private const val SMART_NOTIFICATION_MAX_AGE_MS = 15 * 60_000L
        private const val NOTIFICATION_CHANNEL_ID = "smart_whatsapp_notifications"
        private const val NOTIFICATION_CHANNEL_NAME = "WhatsApp Important Messages"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.tag(TAG).i("🟢 NotificationListenerService CONNECTED - Ready to receive notifications!")
        ServiceHealthMonitor.recordServiceConnected(this)
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
        if (!isWhatsAppNotification(sbn.packageName)) return

        // Record that we received a notification (for service health monitoring)
        ServiceHealthMonitor.recordNotificationReceived(this)

        val snapshot = try {
            sbn.toSnapshot()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Could not read WhatsApp notification")
            return
        }

        val messages = WhatsAppNotificationParser.parse(snapshot)
        if (messages.isEmpty()) return

        serviceScope.launch {
            messages.forEach { saveMessage(it) }
        }
    }

    private fun StatusBarNotification.toSnapshot(): NotificationSnapshot {
        val extras = notification.extras
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        return NotificationSnapshot(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            conversationTitle = (style?.conversationTitle ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))?.toString(),
            isGroupConversation = style?.isGroupConversation ?: extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION),
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            category = notification.category,
            timestamp = notification.`when`.takeIf { it > 0 } ?: postTime,
            messages = style?.messages.orEmpty().map { message ->
                SnapshotMessage(
                    sender = message.person?.name?.toString(),
                    text = message.text?.toString(),
                    timestamp = message.timestamp
                )
            }
        )
    }

    private suspend fun saveMessage(parsed: ParsedMessage) {
        try {
            val threadId = ThreadNaming.threadIdFor(parsed.threadName)
            val message = Message(
                threadId = threadId,
                threadName = parsed.threadName,
                sender = parsed.sender,
                content = parsed.content,
                timestamp = parsed.timestamp,
                messageHash = MessageEntity.generateHash(threadId, parsed.sender, parsed.content, parsed.timestamp),
                messageType = parsed.messageType,
                isDeleted = parsed.isDeleted
            )

            // Re-posted notifications repeat earlier messages; only new ones are stored
            if (!messageRepository.saveIncomingMessage(message)) return
            Timber.tag(TAG).d("Stored new ${parsed.messageType} message in thread $threadId")

            val isRecent = System.currentTimeMillis() - parsed.timestamp < SMART_NOTIFICATION_MAX_AGE_MS
            if (parsed.messageType == MessageType.TEXT && parsed.sender != "You" && isRecent) {
                analysisScope.launch {
                    handleSmartNotification(threadId, parsed.threadName, parsed.sender, parsed.content)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error saving message")
        }
    }

    /**
     * Handle smart notification - analyze message importance and show notification if important enough.
     */
    private suspend fun handleSmartNotification(
        threadId: String,
        threadName: String,
        senderName: String,
        messageContent: String
    ) {
        try {
            val smartNotificationsEnabled = preferencesRepository.isSmartNotificationsEnabled()

            if (!smartNotificationsEnabled) {
                Timber.tag(TAG).d("Smart notifications disabled, skipping importance analysis")
                return
            }

            val importanceScore = analyzeMessageImportanceUseCase.execute(messageContent, senderName)
            if (analyzeMessageImportanceUseCase.meetsThreshold(importanceScore)) {
                showSmartNotification(threadId, threadName, senderName, messageContent, importanceScore)
                Timber.tag(TAG).i("Showed notification for important message (score: $importanceScore)")
            } else {
                Timber.tag(TAG).d("⏭️ Message not important enough, notification suppressed")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in smart notification handling, showing notification as fallback")
            // Fallback: show notification on error to avoid missing important messages
            showSmartNotification(threadId, threadName, senderName, messageContent, 0.7f)
        }
    }

    /**
     * Show a smart notification with appropriate priority based on importance score.
     */
    private fun showSmartNotification(
        threadId: String,
        threadName: String,
        senderName: String,
        messageContent: String,
        importanceScore: Float
    ) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Create notification channel if needed
            createNotificationChannel(notificationManager)

            // Create intent to open thread detail when notification is clicked
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                threadId.hashCode(), // Unique request code per thread
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

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
                .setContentIntent(pendingIntent)
                .build()

            // One notification per thread; a newer important message replaces the previous one
            notificationManager.notify(threadId.hashCode(), notification)

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

    private fun isWhatsAppNotification(packageName: String): Boolean {
        return packageName == Constants.WHATSAPP_PACKAGE ||
               packageName == Constants.WHATSAPP_BUSINESS_PACKAGE
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        analysisScope.cancel()
    }
}
