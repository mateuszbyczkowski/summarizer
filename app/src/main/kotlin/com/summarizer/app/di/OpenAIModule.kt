package com.summarizer.app.di

import com.summarizer.app.BuildConfig
import com.summarizer.app.data.api.OpenAIService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for OpenAI API dependencies.
 *
 * Provides Retrofit configuration for calling OpenAI Chat Completions API.
 */
@Module
@InstallIn(SingletonComponent::class)
object OpenAIModule {

    private const val BASE_URL = "https://api.openai.com/"

    @Provides
    @Singleton
    @Named("openai")
    fun provideOpenAIOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS // Log headers only (don't leak API key in body)
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named("openai")
    fun provideOpenAIRetrofit(
        @Named("openai") okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAIService(
        @Named("openai") retrofit: Retrofit
    ): OpenAIService {
        return retrofit.create(OpenAIService::class.java)
    }
}
