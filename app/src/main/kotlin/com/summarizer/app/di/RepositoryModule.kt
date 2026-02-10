package com.summarizer.app.di

import com.summarizer.app.data.repository.AuthRepositoryImpl
import com.summarizer.app.data.repository.MessageRepositoryImpl
import com.summarizer.app.data.repository.ModelRepositoryImpl
import com.summarizer.app.data.repository.PreferencesRepositoryImpl
import com.summarizer.app.data.repository.SummaryRepositoryImpl
import com.summarizer.app.data.repository.ThreadRepositoryImpl
import com.summarizer.app.data.repository.ThreadSettingsRepositoryImpl
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.domain.repository.MessageRepository
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.domain.repository.SummaryRepository
import com.summarizer.app.domain.repository.ThreadRepository
import com.summarizer.app.domain.repository.ThreadSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        messageRepositoryImpl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindThreadRepository(
        threadRepositoryImpl: ThreadRepositoryImpl
    ): ThreadRepository

    @Binds
    @Singleton
    abstract fun bindSummaryRepository(
        summaryRepositoryImpl: SummaryRepositoryImpl
    ): SummaryRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        preferencesRepositoryImpl: PreferencesRepositoryImpl
    ): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindModelRepository(
        modelRepositoryImpl: ModelRepositoryImpl
    ): ModelRepository

    @Binds
    @Singleton
    abstract fun bindThreadSettingsRepository(
        threadSettingsRepositoryImpl: ThreadSettingsRepositoryImpl
    ): ThreadSettingsRepository
}
