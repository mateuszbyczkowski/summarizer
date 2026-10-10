package com.summarizer.app.util

object Constants {
    // WhatsApp package names
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    // Model catalog now lives in ModelDownloadViewModel.DEFAULT_MODELS (Gemma 4 on LiteRT-LM + LFM2.5 on llama.cpp).

    // Database
    const val DATABASE_NAME = "summarizer_database"

    // Preferences
    const val PREFS_NAME = "summarizer_prefs"
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_PIN_SALT = "pin_salt" // Legacy SHA-256 scheme only
    const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
    const val KEY_PIN_LOCKED_UNTIL = "pin_locked_until"
    const val KEY_LAST_ACTIVE_AT = "last_active_at"

    // PIN
    const val PIN_LENGTH = 6
}
