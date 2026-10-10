package com.summarizer.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import com.summarizer.app.R

/**
 * Puts a summary as text on the clipboard, marked sensitive so the clipboard preview hides it.
 * Call on the main thread.
 */
object SummaryClipboard {

    // ClipDescription.EXTRA_IS_SENSITIVE (API 33); harmless on older versions
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    fun copy(context: Context, text: String) {
        val clip = ClipData.newPlainText(context.getString(R.string.summary_copy_label), text).apply {
            description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        // Android 13+ confirms copies itself
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.summary_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
