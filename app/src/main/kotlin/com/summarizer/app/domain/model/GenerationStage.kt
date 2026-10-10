package com.summarizer.app.domain.model

/** Steps of generating one summary, in order. */
enum class GenerationStage {
    /** Collecting the messages to summarize */
    PREPARING,

    /** Loading the on-device model into memory (or checking the API key) */
    LOADING_MODEL,

    /** The model reads the conversation; on-device this is the slow part before the first word */
    READING,

    /** The model writes the summary */
    WRITING
}
