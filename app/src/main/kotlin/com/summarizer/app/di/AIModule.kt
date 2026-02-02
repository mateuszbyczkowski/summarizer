package com.summarizer.app.di

import com.summarizer.app.data.ai.AIEngineProvider
import com.summarizer.app.domain.ai.AIEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for AI engine dependency injection.
 *
 * Week 7: Switched to RealAIEngine using Llamatik 0.13.0 for actual LLM inference.
 * Week 8: Added AIEngineProvider to support dynamic switching between Local and OpenAI.
 *
 * The AIEngineProvider delegates to either:
 * - RealAIEngine (Local): On-device LLM using Llamatik 0.13.0
 * - OpenAIEngine (OpenAI): Cloud-based API using gpt-4o-mini
 *
 * Users can switch providers in Settings without restarting the app.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AIModule {

    @Binds
    @Singleton
    abstract fun bindAIEngine(
        provider: AIEngineProvider
    ): AIEngine
}
