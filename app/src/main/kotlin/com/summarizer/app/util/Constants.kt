package com.summarizer.app.util

object Constants {
    // WhatsApp package names
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    // Model catalog now lives in ModelDownloadViewModel.DEFAULT_MODELS (Qwen3.5 + LFM2.5).

    // Database
    const val DATABASE_NAME = "summarizer_database"

    // Preferences
    const val PREFS_NAME = "summarizer_prefs"
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_PIN_SALT = "pin_salt"
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    const val KEY_MODEL_DOWNLOADED = "model_downloaded"
    const val KEY_MODEL_PATH = "model_path"

    // PIN
    const val PIN_LENGTH = 6

    // Summarization
    const val MAX_MESSAGES_FOR_SUMMARY = 100
    const val SUMMARY_TIMEOUT_SECONDS = 45L
}
