package com.summarizer.app.data.ai

import android.content.Context
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import com.summarizer.app.domain.ai.LanguageDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** On-device language detection with the platform TextClassifier (no network, no extra library). */
@Singleton
class AndroidLanguageDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : LanguageDetector {

    private companion object {
        const val MIN_CONFIDENCE = 0.5f
        const val MAX_SAMPLE_CHARS = 4_000
    }

    override suspend fun detectLanguageName(text: String): String? = withContext(Dispatchers.Default) {
        runCatching {
            val classifier = context.getSystemService(TextClassificationManager::class.java).textClassifier
            val result = classifier.detectLanguage(TextLanguage.Request.Builder(text.takeLast(MAX_SAMPLE_CHARS)).build())
            if (result.localeHypothesisCount == 0) return@runCatching null
            val locale = result.getLocale(0)
            if (result.getConfidenceScore(locale) < MIN_CONFIDENCE) return@runCatching null
            locale.toLocale().getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotBlank() }
        }.onFailure { Timber.w(it, "Language detection failed") }.getOrNull()
    }
}
