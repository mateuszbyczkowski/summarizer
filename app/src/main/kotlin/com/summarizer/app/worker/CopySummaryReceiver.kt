package com.summarizer.app.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.repository.SummaryRepository
import com.summarizer.app.util.SummaryClipboard
import com.summarizer.app.util.SummaryFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * "Copy" action of the summary-ready notification: puts the summary as text on the clipboard
 * (marked sensitive, so the clipboard preview hides it) and dismisses the notification.
 */
@AndroidEntryPoint
class CopySummaryReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_SUMMARY_ID = "summary_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    @Inject
    lateinit var summaryRepository: SummaryRepository

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val summaryId = intent.getLongExtra(EXTRA_SUMMARY_ID, -1)
        if (summaryId < 0) return
        val pending = goAsync()
        applicationScope.launch {
            try {
                // A notification posted before the app lock was turned on must not bypass it
                if (preferencesRepository.isAppLockEnabled()) return@launch
                val summary = summaryRepository.getSummary(summaryId) ?: return@launch
                val text = SummaryFormatter.toPlainText(context, summary)
                withContext(Dispatchers.Main) {
                    SummaryClipboard.copy(context, text)
                    context.getSystemService(NotificationManager::class.java)
                        .cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
