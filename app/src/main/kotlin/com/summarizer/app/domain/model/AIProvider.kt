package com.summarizer.app.domain.model

/**
 * AI provider options for message summarization.
 */
enum class AIProvider {
    /**
     * Local on-device LLM using Llamatik (RealAIEngine).
     * - Privacy-first: All processing on-device
     * - Offline: Works without internet
     * - Free: No API costs
     * - Storage: Requires 700MB-1.8GB for model file
     */
    LOCAL,

    /**
     * Cloud-based OpenAI API using gpt-4o-mini.
     * - Quality: State-of-the-art model
     * - Speed: Fast cloud inference
     * - Cost: ~$0.0006 per summary (pay-per-use)
     * - Privacy: Messages sent to OpenAI servers
     * - Requires: API key and internet connection
     */
    OPENAI
}
