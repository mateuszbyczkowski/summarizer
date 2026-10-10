package com.summarizer.app.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import com.summarizer.app.R
import com.summarizer.app.di.ApplicationScope
import com.summarizer.app.domain.repository.SummaryRepository
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
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }

    @Inject
    lateinit var summaryRepository: SummaryRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val summaryId = intent.getLongExtra(EXTRA_SUMMARY_ID, -1)
        if (summaryId < 0) return
        val pending = goAsync()
        applicationScope.launch {
            try {
                val summary = summaryRepository.getSummary(summaryId) ?: return@launch
                val text = SummaryFormatter.toPlainText(context, summary)
                withContext(Dispatchers.Main) {
                    val clip = ClipData.newPlainText(context.getString(R.string.summary_copy_label), text).apply {
                        description.extras = PersistableBundle().apply {
                            // ClipDescription.EXTRA_IS_SENSITIVE (API 33); harmless on older versions
                            putBoolean(EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    // Android 13+ confirms copies itself
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, R.string.summary_copied, Toast.LENGTH_SHORT).show()
                    }
                    context.getSystemService(NotificationManager::class.java)
                        .cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
