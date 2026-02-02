# Week 8: OpenAI API Integration Plan
# WhatsApp Summarizer - Alternative AI Provider

**Status**: ✅ IMPLEMENTED (2026-02-02)
**Implementation Time**: ~2 hours (vs 1-2 days estimated)
**Build Status**: ✅ BUILD SUCCESSFUL
**See**: [WEEK8_COMPLETION.md](WEEK8_COMPLETION.md) for implementation details

---

## ⚠️ ARCHIVED - IMPLEMENTATION COMPLETE

This was the planning document for Week 8. The actual implementation is documented in:
- **[WEEK8_COMPLETION.md](WEEK8_COMPLETION.md)** - Full completion report with build results
- **[PROGRESS.md](PROGRESS.md#week-8-openai-api-integration-complete---2026-02-02)** - Updated progress tracking
- **[DECISIONS.md](DECISIONS.md#week-8---openai-api-integration)** - Decision rationale

**What Was Built**:
- ✅ OpenAIEngine with gpt-4o-mini ($0.0006/summary)
- ✅ AIEngineProvider for dynamic Local/OpenAI switching
- ✅ Secure API key storage (EncryptedSharedPreferences AES256-GCM)
- ✅ Settings screen with Material 3 UI
- ✅ API key validation and error handling
- ✅ 9 files created, 6 modified
- ✅ BUILD SUCCESSFUL in 1m 35s, 0 errors

---

# Original Planning Document (Reference Only)

The content below is the original plan. It has been fully implemented.

---

## 🎯 Overview

Add OpenAI API integration as an alternative to local LLM inference, giving users the choice between:
- **Local LLM** (Current): Privacy-first, offline, one-time model download
- **OpenAI API** (New): Cloud-based, requires API key, pay-per-use

---

## 🏗️ Architecture Changes

### 1. New AIEngine Implementation

**File**: `app/src/main/kotlin/com/summarizer/app/data/ai/OpenAIEngine.kt`

```kotlin
@Singleton
class OpenAIEngine @Inject constructor(
    private val openAIService: OpenAIService,
    private val preferencesRepository: PreferencesRepository
) : AIEngine {

    companion object {
        private const val MODEL = "gpt-4o-mini" // Cost-effective, fast
        private const val TIMEOUT_MS = 30_000L // 30 seconds
    }

    override suspend fun loadModel(modelPath: String): Result<Unit> {
        // No-op for API-based inference (validate API key instead)
        return validateApiKey()
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Result<String> {
        // Call OpenAI Chat Completions API
    }

    override fun generateStream(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Flow<GenerationEvent> {
        // Use OpenAI streaming API with Server-Sent Events
    }

    override suspend fun generateJson(
        prompt: String,
        jsonSchema: String?
    ): Result<String> {
        // Use OpenAI JSON mode or structured outputs
    }

    override fun cancelGeneration() {
        // Cancel HTTP request
    }

    override suspend fun unloadModel() {
        // No-op for API-based inference
    }

    override fun isModelLoaded(): Boolean {
        // Return true if API key is configured
        return getApiKey() != null
    }

    override fun getModelInfo(): ModelInfo? {
        return ModelInfo(
            name = MODEL,
            path = "OpenAI API",
            contextLength = 128_000 // gpt-4o-mini context window
        )
    }
}
```

---

### 2. OpenAI API Service

**File**: `app/src/main/kotlin/com/summarizer/app/data/api/OpenAIService.kt`

```kotlin
interface OpenAIService {

    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    @Streaming
    @POST("v1/chat/completions")
    suspend fun createChatCompletionStream(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ResponseBody
}

data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float = 0.7f,
    val max_tokens: Int = 512,
    val stream: Boolean = false,
    val response_format: ResponseFormat? = null // For JSON mode
)

data class Message(
    val role: String, // "system", "user", "assistant"
    val content: String
)

data class ResponseFormat(
    val type: String // "json_object"
)

data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage
)

data class Choice(
    val message: Message,
    val finish_reason: String
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)
```

---

### 3. Secure API Key Storage

**Update**: `app/src/main/kotlin/com/summarizer/app/domain/repository/PreferencesRepository.kt`

```kotlin
interface PreferencesRepository {
    // Existing methods...

    // AI Provider settings
    suspend fun getAIProvider(): AIProvider
    suspend fun setAIProvider(provider: AIProvider)

    // OpenAI API Key (stored in EncryptedSharedPreferences)
    suspend fun getOpenAIApiKey(): String?
    suspend fun setOpenAIApiKey(apiKey: String)
    suspend fun clearOpenAIApiKey()
}

enum class AIProvider {
    LOCAL,  // RealAIEngine (Llamatik)
    OPENAI  // OpenAIEngine
}
```

**Update**: `app/src/main/kotlin/com/summarizer/app/data/repository/PreferencesRepositoryImpl.kt`

```kotlin
@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferencesRepository {

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "encrypted_prefs",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private object PreferencesKeys {
        // Existing keys...
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        const val OPENAI_API_KEY = "openai_api_key" // EncryptedSharedPreferences
    }

    override suspend fun getAIProvider(): AIProvider {
        return context.dataStore.data.map { preferences ->
            val providerString = preferences[PreferencesKeys.AI_PROVIDER]
            when (providerString) {
                "OPENAI" -> AIProvider.OPENAI
                else -> AIProvider.LOCAL
            }
        }.first()
    }

    override suspend fun setAIProvider(provider: AIProvider) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AI_PROVIDER] = provider.name
        }
    }

    override suspend fun getOpenAIApiKey(): String? {
        return withContext(Dispatchers.IO) {
            encryptedPrefs.getString(PreferencesKeys.OPENAI_API_KEY, null)
        }
    }

    override suspend fun setOpenAIApiKey(apiKey: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit {
                putString(PreferencesKeys.OPENAI_API_KEY, apiKey)
            }
        }
    }

    override suspend fun clearOpenAIApiKey() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit {
                remove(PreferencesKeys.OPENAI_API_KEY)
            }
        }
    }
}
```

---

### 4. Dynamic AIEngine Provider

**Update**: `app/src/main/kotlin/com/summarizer/app/di/AIModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    @Provides
    @Singleton
    fun provideAIEngine(
        realEngine: RealAIEngine,
        openAIEngine: OpenAIEngine,
        preferencesRepository: PreferencesRepository
    ): AIEngine {
        // Runtime decision based on user preference
        return AIEngineProvider(realEngine, openAIEngine, preferencesRepository)
    }
}

/**
 * Wrapper that delegates to the appropriate AIEngine based on user settings.
 */
@Singleton
class AIEngineProvider @Inject constructor(
    private val realEngine: RealAIEngine,
    private val openAIEngine: OpenAIEngine,
    private val preferencesRepository: PreferencesRepository
) : AIEngine {

    private suspend fun getActiveEngine(): AIEngine {
        return when (preferencesRepository.getAIProvider()) {
            AIProvider.LOCAL -> realEngine
            AIProvider.OPENAI -> openAIEngine
        }
    }

    override suspend fun loadModel(modelPath: String): Result<Unit> {
        return getActiveEngine().loadModel(modelPath)
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        temperature: Float
    ): Result<String> {
        return getActiveEngine().generate(prompt, systemPrompt, maxTokens, temperature)
    }

    // Delegate all other methods similarly...
}
```

---

### 5. Network Module for OpenAI

**File**: `app/src/main/kotlin/com/summarizer/app/di/OpenAIModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object OpenAIModule {

    private const val BASE_URL = "https://api.openai.com/"

    @Provides
    @Singleton
    @Named("openai")
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
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
    fun provideOpenAIService(retrofit: Retrofit): OpenAIService {
        return retrofit.create(OpenAIService::class.java)
    }
}
```

---

### 6. Settings Screen

**File**: `app/src/main/kotlin/com/summarizer/app/ui/screens/settings/SettingsScreen.kt`

New Settings screen with:
- AI Provider selection (Local vs OpenAI)
- OpenAI API key input (password field)
- API key validation button
- Cost estimation display
- Privacy notice for cloud-based processing

**File**: `app/src/main/kotlin/com/summarizer/app/ui/screens/settings/SettingsViewModel.kt`

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val openAIEngine: OpenAIEngine
) : ViewModel() {

    val aiProvider = preferencesRepository.getAIProvider().asStateFlow()
    val hasApiKey = preferencesRepository.getOpenAIApiKey().map { it != null }.asStateFlow()

    fun setAIProvider(provider: AIProvider) {
        viewModelScope.launch {
            preferencesRepository.setAIProvider(provider)
        }
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            preferencesRepository.setOpenAIApiKey(apiKey)
        }
    }

    fun validateApiKey() {
        viewModelScope.launch {
            // Test API call to validate key
            val result = openAIEngine.generate("Test", maxTokens = 5)
            // Show success/error to user
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            preferencesRepository.clearOpenAIApiKey()
            preferencesRepository.setAIProvider(AIProvider.LOCAL)
        }
    }
}
```

---

### 7. Update Navigation

**Update**: `app/src/main/kotlin/com/summarizer/app/ui/navigation/NavGraph.kt`

Add Settings route:
```kotlin
composable("settings") {
    SettingsScreen(
        onBackPressed = { navController.popBackStack() }
    )
}
```

Add Settings menu item to ThreadListScreen.

---

## 📦 New Dependencies

**Update**: `app/build.gradle.kts`

```kotlin
dependencies {
    // Existing dependencies...

    // Retrofit for OpenAI API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Already have OkHttp from Week 4 (model download)
    // implementation("com.squareup.okhttp3:okhttp:4.12.0") // ✅ Already added
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Already have EncryptedSharedPreferences (for PIN storage)
    // implementation("androidx.security:security-crypto:1.1.0-alpha06") // ✅ Already added
}
```

---

## 🎨 User Experience Flow

### Onboarding (Updated)

**Current Flow**:
1. Welcome → Permission → PIN → Storage → Model Download → App

**New Flow**:
1. Welcome → Permission → PIN → **AI Provider Selection** → Storage/API Key → App

**AI Provider Selection Screen**:
- Option 1: **Local AI** (Privacy-first, offline, free)
  - Shows model download screen
- Option 2: **OpenAI** (Cloud-based, requires API key)
  - Shows API key input screen with cost warning

### Settings Screen

- **AI Provider** section:
  - Radio buttons: Local / OpenAI
  - Current provider displayed

- **OpenAI Configuration** (if OpenAI selected):
  - API Key input field (password-masked)
  - "Save & Validate" button
  - Status indicator (Valid ✓ / Invalid ✗)
  - Clear API key button

- **Privacy Notice**:
  - "OpenAI: Messages sent to cloud for processing"
  - "Local: All processing on-device, fully private"

- **Cost Estimation** (OpenAI only):
  - "Estimated: $0.0001 per summary (gpt-4o-mini)"
  - "~10,000 summaries per $1"

---

## 💰 Cost Optimization

### Model Selection: gpt-4o-mini

**Why gpt-4o-mini**:
- **Cost**: $0.150 per 1M input tokens, $0.600 per 1M output tokens
- **Speed**: Fastest OpenAI model (low latency)
- **Quality**: Sufficient for WhatsApp message summarization
- **Context**: 128k tokens (handles very long threads)

**Cost Example** (50 messages, ~2000 tokens input, ~500 tokens output):
- Input: 2000 tokens × $0.150 / 1M = $0.0003
- Output: 500 tokens × $0.600 / 1M = $0.0003
- **Total per summary: ~$0.0006** (very affordable)

**Alternative Models** (future consideration):
- **gpt-4o**: Higher quality, 4x more expensive
- **gpt-3.5-turbo**: Deprecated, not recommended

---

## 🔒 Security & Privacy

### API Key Security
- ✅ Stored in **EncryptedSharedPreferences** (AES256-GCM)
- ✅ Never logged or exposed in UI (password field)
- ✅ User can clear/revoke anytime

### Data Privacy
- ⚠️ **OpenAI**: Messages sent to OpenAI servers (user must consent)
- ✅ **Local**: Messages never leave device (privacy-first default)

### Privacy Consent
- Explicit warning during provider selection
- Clearly label "Cloud-based" vs "On-device"
- Default to Local provider (privacy-first)

---

## 📝 Implementation Checklist

### Phase 1: Backend (1 day)
- [ ] Create `OpenAIEngine.kt` implementing `AIEngine` interface
- [ ] Create `OpenAIService.kt` with Retrofit interface
- [ ] Add API request/response data classes
- [ ] Update `PreferencesRepository` with AI provider methods
- [ ] Update `PreferencesRepositoryImpl` with API key storage
- [ ] Create `AIEngineProvider.kt` for dynamic selection
- [ ] Update `AIModule.kt` to provide `AIEngineProvider`
- [ ] Create `OpenAIModule.kt` for Retrofit DI
- [ ] Add Retrofit/OkHttp/Gson dependencies

### Phase 2: UI (0.5 days)
- [ ] Create `SettingsScreen.kt` with provider selection
- [ ] Create `SettingsViewModel.kt` with API key management
- [ ] Add Settings route to `NavGraph.kt`
- [ ] Add Settings menu item to `ThreadListScreen.kt`
- [ ] Update onboarding to include AI provider selection screen
- [ ] Add API key validation UI feedback

### Phase 3: Testing (0.5 days)
- [ ] Test OpenAI API integration with real API key
- [ ] Test switching between Local and OpenAI providers
- [ ] Test API key validation
- [ ] Test error handling (invalid key, network errors)
- [ ] Test cost calculation display
- [ ] Verify EncryptedSharedPreferences security
- [ ] Test onboarding flow with both providers

---

## 🧪 Testing Strategy

### Manual Testing
1. **API Key Setup**:
   - Enter valid OpenAI API key → Should validate successfully
   - Enter invalid key → Should show error
   - Clear API key → Should revert to Local provider

2. **Summary Generation**:
   - Generate summary with OpenAI → Should call API, return real summary
   - Switch to Local → Should use RealAIEngine
   - Switch back to OpenAI → Should resume API usage

3. **Error Handling**:
   - Disable WiFi → Should show network error
   - Invalid API key → Should show authentication error
   - Rate limit exceeded → Should show clear error message

### Unit Testing (Optional for Week 8)
- Mock OpenAIService for `OpenAIEngine` tests
- Test API key storage/retrieval
- Test provider switching logic

---

## 🚀 Future Enhancements (Post-Week 8)

### Additional Features
1. **Cost Tracking**:
   - Store actual token usage per summary
   - Display lifetime cost in Settings
   - Monthly cost reports

2. **Model Selection**:
   - Allow user to choose gpt-4o-mini vs gpt-4o
   - Display cost difference

3. **Hybrid Mode**:
   - Use Local for quick summaries
   - Use OpenAI for complex analysis

4. **Caching**:
   - Cache OpenAI responses to avoid duplicate API calls
   - Reduce costs for re-summarization

5. **Batch Processing**:
   - Summarize multiple threads in one API call
   - Optimize token usage

---

## 📊 Success Metrics

### Week 8 Goals
- ✅ OpenAI API integration functional
- ✅ Secure API key storage implemented
- ✅ Settings UI for provider switching
- ✅ Onboarding updated with provider selection
- ✅ Cost estimation displayed to users
- ✅ Error handling for API failures
- ✅ Privacy warnings for cloud processing

### User Impact
- **Flexibility**: Users choose privacy (Local) vs convenience (OpenAI)
- **Accessibility**: Users without high-end phones can use OpenAI
- **Cost**: OpenAI option is affordable (~$0.0006 per summary)
- **Quality**: OpenAI may provide better summaries than TinyLlama

---

## 🔄 Migration Strategy

### Existing Users (Post-I1 Beta)
- Default to Local provider (no breaking changes)
- Show Settings hint: "Try OpenAI for faster summaries"
- Opt-in migration (user must enter API key)

### New Users
- Choose provider during onboarding
- Default suggestion: Local (privacy-first)
- Show comparison: Local vs OpenAI

---

## ⚠️ Risks & Mitigations

### Risk 1: API Costs
- **Mitigation**: Clear cost display, per-summary estimation
- **Fallback**: User can switch back to Local anytime

### Risk 2: Privacy Concerns
- **Mitigation**: Explicit warnings, default to Local
- **Transparency**: Clear data flow explanation

### Risk 3: API Rate Limits
- **Mitigation**: Implement exponential backoff, show clear errors
- **Fallback**: Suggest switching to Local for unlimited usage

### Risk 4: Network Dependency
- **Mitigation**: Check network before API call, show offline message
- **Fallback**: Local provider always available offline

---

## 📚 Documentation Updates

### Files to Update
- [ ] `CURRENT_STATUS.md` - Add Week 8 completion
- [ ] `PROGRESS.md` - Add Week 8 tasks
- [ ] `DECISIONS.md` - Document OpenAI integration decision
- [ ] `README.md` - Update feature list with AI provider options
- [ ] `WEEK8_COMPLETION.md` - Create completion summary

---

## 🎯 Next Steps

1. **Week 8 Implementation**: Follow this plan to add OpenAI integration
2. **User Testing**: Beta test with both providers
3. **Cost Monitoring**: Track actual costs with real usage
4. **Performance Comparison**: Local vs OpenAI quality/speed
5. **Future Iterations**: Consider additional cloud providers (Anthropic Claude, Gemini, etc.)

---

**Status**: 📋 Ready for Implementation
**Target Week**: Week 8
**Effort Estimate**: 1-2 days
**Dependencies**: None (all prerequisites met in Week 1-7)
**Priority**: Medium (Post-I1 enhancement, not critical for MVP)

---

## 🤝 User Benefits

### Local LLM (Current)
✅ **Privacy**: All data on-device
✅ **Offline**: Works without internet
✅ **Cost**: Free after model download
✅ **Unlimited**: No usage limits
❌ **Storage**: Requires 700MB-1.8GB space
❌ **Performance**: Slower on low-end devices

### OpenAI API (New)
✅ **Quality**: State-of-the-art model (gpt-4o-mini)
✅ **Speed**: Fastest inference (cloud-powered)
✅ **Storage**: No local model needed
✅ **Updates**: Always latest model version
❌ **Privacy**: Messages sent to OpenAI
❌ **Cost**: Pay per summary (~$0.0006 each)
❌ **Internet**: Requires active connection

---

**Prepared By**: Claude Code
**Date**: 2026-02-02
**Version**: 1.0
