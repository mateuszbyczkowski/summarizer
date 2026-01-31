package com.summarizer.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.summarizer.app.domain.model.Message
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
            val title = extras.getCharSequence("android.title")?.toString() ?: return
            val text = extras.getCharSequence("android.text")?.toString() ?: return

            // Check if it's a group message (contains ":")
            if (!title.contains(":") && !text.contains(":")) {
                Timber.d("Skipping personal message")
                return
            }

            // Parse group message
            parseAndSaveGroupMessage(title, text, sbn.postTime)

        } catch (e: Exception) {
            Timber.e(e, "Error processing notification")
        }
    }

    private fun parseAndSaveGroupMessage(title: String, text: String, timestamp: Long) {
        serviceScope.launch {
            try {
                // Extract group name and sender
                // Format is typically: "Group Name: Sender Name" or just "Group Name"
                val (groupName, sender, messageContent) = when {
                    title.contains(":") -> {
                        // Format: "GroupName: SenderName"
                        val parts = title.split(":", limit = 2)
                        val group = parts[0].trim()
                        val senderName = parts.getOrNull(1)?.trim() ?: "Unknown"
                        Triple(group, senderName, text)
                    }
                    text.contains(":") -> {
                        // Message text contains sender
                        val parts = text.split(":", limit = 2)
                        val senderName = parts[0].trim()
                        val content = parts.getOrNull(1)?.trim() ?: text
                        Triple(title, senderName, content)
                    }
                    else -> {
                        Triple(title, "Unknown", text)
                    }
                }

                // Generate thread ID (hash of group name)
                val threadId = groupName.hashCode().toString()

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
                    Timber.d("Created new thread: $groupName")
                } else {
                    val newCount = existingThread.messageCount + 1
                    threadRepository.updateThreadStats(threadId, newCount, timestamp)
                }

                // Save message
                val message = Message(
                    threadId = threadId,
                    threadName = groupName,
                    sender = sender,
                    content = messageContent,
                    timestamp = timestamp
                )
                messageRepository.saveMessage(message)

                Timber.d("Saved message from $sender in $groupName")

            } catch (e: Exception) {
                Timber.e(e, "Error saving message")
            }
        }
    }

    private fun isWhatsAppNotification(packageName: String): Boolean {
        return packageName == Constants.WHATSAPP_PACKAGE
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
