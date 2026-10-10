package com.summarizer.app.domain.ai

/**
 * Detects the dominant language of a text, so prompts can name it explicitly: small models
 * often ignore "answer in the language of the messages" and default to English.
 */
interface LanguageDetector {
    /** English name of the language (e.g. "Polish"), or null when uncertain. */
    suspend fun detectLanguageName(text: String): String?
}
