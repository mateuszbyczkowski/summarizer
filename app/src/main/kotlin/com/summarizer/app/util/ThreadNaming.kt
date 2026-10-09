package com.summarizer.app.util

/**
 * Derives stable thread identifiers from WhatsApp conversation titles.
 */
object ThreadNaming {

    private val COUNT_SUFFIXES = listOf(
        Regex("\\s*\\(\\d+\\s+messages?\\)\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\s*\\(\\d+\\s+new\\)\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\s*\\[\\d+\\s+messages?\\]\\s*$", RegexOption.IGNORE_CASE)
    )
    private val WHITESPACE = Regex("\\s+")

    /**
     * Strip notification decorations such as "(5 messages)" so the same chat always maps to
     * the same thread, and normalize whitespace and case.
     */
    fun normalize(threadName: String): String =
        COUNT_SUFFIXES
            .fold(threadName.trim()) { name, suffix -> name.replace(suffix, "") }
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase()

    /** Display name without notification decorations (case preserved). */
    fun displayName(threadName: String): String =
        COUNT_SUFFIXES
            .fold(threadName.trim()) { name, suffix -> name.replace(suffix, "") }
            .trim()
            .replace(WHITESPACE, " ")

    /**
     * Thread ID for a conversation title. Kept identical to the historical scheme so existing
     * threads keep their IDs.
     */
    fun threadIdFor(threadName: String): String = normalize(threadName).hashCode().toString()
}
