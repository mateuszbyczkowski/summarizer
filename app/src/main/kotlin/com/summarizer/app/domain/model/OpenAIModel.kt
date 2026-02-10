package com.summarizer.app.domain.model

/**
 * Available OpenAI models for summarization with their pricing and characteristics.
 *
 * Pricing is per 1M tokens (input/output) as of February 2026.
 * Actual costs may vary - check https://openai.com/pricing for latest pricing.
 */
enum class OpenAIModel(
    val modelId: String,
    val displayName: String,
    val description: String,
    val contextWindow: Int, // Maximum context length in tokens
    val inputPricePer1MTokens: Double, // USD per 1M input tokens
    val outputPricePer1MTokens: Double, // USD per 1M output tokens
    val estimatedCostPerSummary: Double // Estimated cost per typical summary (100 messages ~2k input, 500 output)
) {
    GPT_4O_MINI(
        modelId = "gpt-4o-mini",
        displayName = "GPT-4o Mini",
        description = "Smallest and most affordable - best value for most users",
        contextWindow = 128_000,
        inputPricePer1MTokens = 0.15, // $0.15 per 1M input tokens
        outputPricePer1MTokens = 0.60, // $0.60 per 1M output tokens
        estimatedCostPerSummary = 0.0006 // ~$0.0006 per summary (2k input + 500 output)
    ),

    GPT_4O(
        modelId = "gpt-4o",
        displayName = "GPT-4o",
        description = "Balanced quality and speed - high-performance model",
        contextWindow = 128_000,
        inputPricePer1MTokens = 2.50, // $2.50 per 1M input tokens
        outputPricePer1MTokens = 10.00, // $10.00 per 1M output tokens
        estimatedCostPerSummary = 0.010 // ~$0.01 per summary
    ),

    O1_MINI(
        modelId = "o1-mini",
        displayName = "o1-mini",
        description = "Advanced reasoning model - best for complex analysis",
        contextWindow = 128_000,
        inputPricePer1MTokens = 3.00, // $3.00 per 1M input tokens
        outputPricePer1MTokens = 12.00, // $12.00 per 1M output tokens
        estimatedCostPerSummary = 0.012 // ~$0.012 per summary
    );

    companion object {
        /**
         * Get model by ID, with fallback to default.
         */
        fun fromModelId(modelId: String): OpenAIModel {
            return values().firstOrNull { it.modelId == modelId } ?: GPT_4O_MINI
        }

        /**
         * Default recommended model.
         */
        val DEFAULT = GPT_4O_MINI
    }

    /**
     * Format pricing for display.
     */
    fun formatPricing(): String {
        return "~$${String.format("%.4f", estimatedCostPerSummary)} per summary"
    }

    /**
     * Format detailed pricing breakdown.
     */
    fun formatDetailedPricing(): String {
        return """
            Input: $${String.format("%.2f", inputPricePer1MTokens)} / 1M tokens
            Output: $${String.format("%.2f", outputPricePer1MTokens)} / 1M tokens
            Estimated: ~$${String.format("%.4f", estimatedCostPerSummary)} per summary
        """.trimIndent()
    }
}
