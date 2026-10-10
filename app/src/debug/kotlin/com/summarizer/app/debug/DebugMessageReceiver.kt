package com.summarizer.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.summarizer.app.data.debug.DebugSampleData
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.model.Message
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.util.ThreadNaming
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug builds only: adds a message as if WhatsApp had posted it, so new-message states (badges,
 * "new since summary", the update button) can be checked on the emulator, which has no WhatsApp:
 *
 * `adb shell am broadcast -a com.summarizer.app.debug.ADD_MESSAGE -p com.summarizer.app.debug
 *  --es sender Anna --es text "Hello"` (optional `--es thread <name>`, default: the sample chat)
 */
@AndroidEntryPoint
class DebugMessageReceiver : BroadcastReceiver() {

    @Inject
    lateinit var messageRepository: MessageRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val threadName = intent.getStringExtra("thread") ?: DebugSampleData.THREAD_NAME
        val sender = intent.getStringExtra("sender") ?: "Debug"
        val text = intent.getStringExtra("text") ?: return
        val now = System.currentTimeMillis()
        val pending = goAsync()
        applicationScope.launch {
            try {
                messageRepository.saveIncomingMessage(
                    Message(
                        threadId = ThreadNaming.threadIdFor(threadName),
                        threadName = threadName,
                        sender = sender,
                        content = text,
                        timestamp = now,
                        messageHash = "debug-adb-$now"
                    )
                )
            } finally {
                pending.finish()
            }
        }
    }
}
