package com.summarizer.app.domain.model

/**
 * Defines how summaries should be generated for a thread.
 */
enum class SummarizationMode {
    /**
     * Summarize only new messages received since the last summary was generated.
     * This is the default and most efficient mode.
     */
    INCREMENTAL,

    /**
     * Summarize all messages in the thread, regardless of previous summaries.
     * Useful for getting a complete overview or when message history has changed.
     */
    FULL
}
