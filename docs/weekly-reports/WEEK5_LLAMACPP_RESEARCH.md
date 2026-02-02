# Week 5: llama.cpp Android Integration Research

**Date**: 2026-01-31
**Project**: WhatsApp Thread Summarizer
**Target**: Local LLM inference for on-device summarization
**Status**: Research Complete - Ready for Implementation

---

## Executive Summary

After comprehensive research of available llama.cpp Android integration options, **kotlinllamacpp** is the recommended library for this project. It provides the best balance of simplicity, performance, and Android-specific optimizations for ARM devices.

**Key Findings:**
- **Recommended**: kotlinllamacpp (v0.2.0)
- **Alternative**: Llamatik (v0.14.0) for KMP projects
- **Expected Performance**: 8-15 tokens/second on mid-range Android devices
- **Memory Requirements**: ~1-1.5GB for TinyLlama 1.1B Q4_K_M
- **Model Load Time**: 1-3 seconds (with mmap optimization)
- **Integration Complexity**: Low (Maven dependency + ViewModel integration)

---

## 1. Library Comparison

### Available Options

| Library | Version | Architecture | GGUF Support | Streaming | Cancellation | Android Optimization | Maintenance |
|---------|---------|--------------|--------------|-----------|--------------|---------------------|-------------|
| **kotlinllamacpp** | 0.2.0 | Native JNI | ✅ Yes | ✅ Yes (Flow) | ✅ Yes | ✅ ARM-specific | 🟢 Active |
| **Llamatik** | 0.14.0 | KMP + K/N | ✅ Yes | ✅ Yes (Callback) | ✅ Yes | ⚠️ Generic | 🟢 Active |
| **llama-cpp-kt** | - | JNA | ✅ Yes | ⚠️ Manual | ⚠️ Manual | ❌ No | 🟡 Moderate |
| **Official Binding** | - | Native | ✅ Yes | ✅ Yes (Flow) | ⚠️ Limited | ✅ Yes | 🟢 Active |

### Detailed Comparison

#### 1. kotlinllamacpp
**Repository**: https://github.com/ljcamargo/kotlinllamacpp
**Maven**: `io.github.ljcamargo:llamacpp-kotlin:0.2.0`

**Pros:**
- Built specifically for Android ARM devices (arm64-v8a)
- Automatic CPU feature detection (i8mm, dotprod)
- Helper class for lifecycle management
- Jetpack Compose integration examples
- ContentResolver support for Android file access
- Context shift support for long conversations
- Real-time callback via Kotlin Flow
- Stop prediction mid-generation

**Cons:**
- Alpha version (API may change)
- ARM-only (no x86/x86_64 support)
- Single context limitation
- Limited documentation

**Best For:** Native Android apps targeting ARM devices (our use case)

---

#### 2. Llamatik
**Repository**: https://github.com/ferranpons/Llamatik
**Maven**: `com.llamatik:library:0.14.0`

**Pros:**
- Kotlin Multiplatform (Android, iOS, Desktop)
- Clean Kotlin-first API
- JSON schema-constrained generation
- Both streaming and non-streaming modes
- Embeddings support
- Active development (Jan 2026 release)
- MIT License

**Cons:**
- Single model limitation
- No concurrent model loading
- Generic optimization (not ARM-specific)
- Larger library size (KMP overhead)
- Composable annotations (UI coupling)

**Best For:** KMP projects or teams planning multi-platform expansion

---

#### 3. llama-cpp-kt
**Repository**: https://github.com/hurui200320/llama-cpp-kt
**Technology**: JNA (Java Native Access)

**Pros:**
- Low-level control over C functions
- Direct access to llama.cpp API
- No abstraction overhead

**Cons:**
- Requires manual libllama.so build
- JNA runtime dependency configuration
- No Android-specific features
- Manual lifecycle management
- Grammar support missing
- Complex setup process

**Best For:** Projects requiring low-level control or custom llama.cpp features

---

#### 4. Official llama.cpp Android Binding
**Repository**: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
**Path**: `examples/llama.android`

**Pros:**
- Official support from llama.cpp maintainers
- Hardware acceleration (SME2 for ARM, AMX for x86-64)
- Automatic CPU feature detection
- Kotlin Flow integration
- GGUF metadata reader
- Active maintenance

**Cons:**
- Requires building from source
- More complex integration
- Minimal documentation
- No helper abstractions
- Manual Android Studio import

**Best For:** Projects needing bleeding-edge features or contributing back to llama.cpp

---

## 2. Recommendation: kotlinllamacpp

### Why kotlinllamacpp?

1. **Perfect Architecture Match**
   - Built for Android-native apps (our stack)
   - ARM-specific optimizations (target devices)
   - Jetpack Compose integration (our UI framework)
   - ViewModel lifecycle support (our architecture)

2. **Lowest Integration Complexity**
   - Single Maven dependency
   - Pre-built native binaries
   - Helper class abstracts llama.cpp complexity
   - ContentResolver integration for Android file access

3. **Performance Optimizations**
   - ARM CPU feature detection (i8mm, dotprod)
   - Context shift for memory efficiency
   - Batch interruption for UI responsiveness
   - Direct targeting of Snapdragon, MediaTek, Exynos, Tensor

4. **API Design**
   - Kotlin Flow for reactive updates
   - Coroutine-based async operations
   - Clean separation of concerns
   - Simple lifecycle management

5. **Project Requirements Alignment**
   - GGUF support (TinyLlama Q4_K_M)
   - Min SDK 31 (library compatible)
   - 4GB+ RAM devices (optimized for this tier)
   - Single model use case (library limitation is non-issue)

---

## 3. Installation & Setup

### Step 1: Add Maven Dependency

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.ljcamargo:llamacpp-kotlin:0.2.0")

    // Existing dependencies
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### Step 2: Sync Gradle

No additional NDK setup required - native binaries are bundled in the AAR.

### Step 3: ProGuard/R8 Rules

Add to `app/proguard-rules.pro`:

```proguard
# Keep JNI methods for kotlinllamacpp
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep kotlinllamacpp classes
-keep class io.github.ljcamargo.llamacpp.** { *; }

# Keep for debugging
-keepattributes SourceFile,LineNumberTable
```

### Step 4: Verify Build

```bash
./gradlew clean build
```

No additional configuration needed - library handles JNI loading automatically.

---

## 4. API Usage & Code Examples

### Architecture Integration

```kotlin
// File: data/ai/LlamaInferenceEngine.kt
package com.summarizer.app.data.ai

import android.content.ContentResolver
import io.github.ljcamargo.llamacpp.LlamaHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaInferenceEngine @Inject constructor(
    private val contentResolver: ContentResolver
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _llmEvents = MutableSharedFlow<LlamaHelper.LLMEvent>()
    val llmEvents: SharedFlow<LlamaHelper.LLMEvent> = _llmEvents.asSharedFlow()

    private lateinit var llamaHelper: LlamaHelper
    private var isModelLoaded = false

    init {
        llamaHelper = LlamaHelper(
            contentResolver = contentResolver,
            scope = scope,
            sharedFlow = _llmEvents
        )
    }

    /**
     * Load GGUF model from file path
     * @param modelPath Absolute path to .gguf file
     * @param contextLength Token context window (2048 recommended for TinyLlama)
     * @param onLoaded Callback when model loads successfully
     * @param onError Callback on load failure
     */
    fun loadModel(
        modelPath: String,
        contextLength: Int = 2048,
        onLoaded: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            Timber.d("Loading model from: $modelPath")
            llamaHelper.load(
                path = modelPath,
                contextLength = contextLength
            ) { contextId ->
                isModelLoaded = true
                Timber.i("Model loaded successfully. Context ID: $contextId")
                onLoaded(contextId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load model")
            onError(e.message ?: "Unknown error loading model")
        }
    }

    /**
     * Generate text from prompt
     * Results streamed via llmEvents Flow
     */
    suspend fun generate(prompt: String) {
        if (!isModelLoaded) {
            Timber.e("Cannot generate: model not loaded")
            return
        }

        try {
            Timber.d("Starting generation with prompt: ${prompt.take(50)}...")
            llamaHelper.predict(prompt)
        } catch (e: Exception) {
            Timber.e(e, "Generation failed")
        }
    }

    /**
     * Stop ongoing generation
     */
    fun stopGeneration() {
        llamaHelper.stopPrediction()
        Timber.d("Generation stopped")
    }

    /**
     * Abort any ongoing operation
     */
    fun abort() {
        llamaHelper.abort()
        Timber.d("Operation aborted")
    }

    /**
     * Release resources (call in onCleared)
     */
    fun release() {
        llamaHelper.abort()
        llamaHelper.release()
        isModelLoaded = false
        Timber.d("Resources released")
    }
}
```

### ViewModel Integration

```kotlin
// File: ui/screens/summary/SummaryGenerationViewModel.kt
package com.summarizer.app.ui.screens.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.data.ai.LlamaInferenceEngine
import com.summarizer.app.domain.model.Thread
import com.summarizer.app.domain.repository.ModelRepository
import com.summarizer.app.domain.repository.SummaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ljcamargo.llamacpp.LlamaHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SummaryUiState(
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val generatedText: String = "",
    val error: String? = null,
    val modelLoaded: Boolean = false,
    val progress: Float = 0f
)

@HiltViewModel
class SummaryGenerationViewModel @Inject constructor(
    private val inferenceEngine: LlamaInferenceEngine,
    private val modelRepository: ModelRepository,
    private val summaryRepository: SummaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    init {
        observeLlamaEvents()
        loadModelIfAvailable()
    }

    private fun loadModelIfAvailable() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Get active model from database
            val activeModel = modelRepository.getActiveModel()

            if (activeModel == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "No model available. Please download a model first."
                    )
                }
                return@launch
            }

            // Load model into inference engine
            inferenceEngine.loadModel(
                modelPath = activeModel.localPath,
                contextLength = 2048,
                onLoaded = { contextId ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            modelLoaded = true,
                            error = null
                        )
                    }
                    Timber.i("Model ${activeModel.name} loaded with context $contextId")
                },
                onError = { errorMessage ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load model: $errorMessage"
                        )
                    }
                    Timber.e("Model load failed: $errorMessage")
                }
            )
        }
    }

    private fun observeLlamaEvents() {
        viewModelScope.launch {
            inferenceEngine.llmEvents.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Started -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = true,
                                generatedText = "",
                                error = null
                            )
                        }
                        Timber.d("Generation started")
                    }

                    is LlamaHelper.LLMEvent.Token -> {
                        // Append token to generated text
                        _uiState.update {
                            it.copy(generatedText = it.generatedText + event.word)
                        }
                    }

                    is LlamaHelper.LLMEvent.Done -> {
                        _uiState.update {
                            it.copy(isGenerating = false)
                        }
                        Timber.i("Generation complete: ${_uiState.value.generatedText.length} chars")
                    }

                    is LlamaHelper.LLMEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                error = event.message
                            )
                        }
                        Timber.e("Generation error: ${event.message}")
                    }
                }
            }
        }
    }

    fun generateSummary(thread: Thread, messages: List<String>) {
        if (!_uiState.value.modelLoaded) {
            _uiState.update { it.copy(error = "Model not loaded") }
            return
        }

        viewModelScope.launch {
            val prompt = buildSummaryPrompt(thread.name, messages)
            inferenceEngine.generate(prompt)
        }
    }

    private fun buildSummaryPrompt(threadName: String, messages: List<String>): String {
        return """
            |<|system|>
            |You are a helpful assistant that summarizes WhatsApp group conversations concisely.
            |</|system|>
            |<|user|>
            |Summarize this WhatsApp group conversation from "$threadName":
            |
            |${messages.joinToString("\n")}
            |
            |Provide a concise summary in 2-3 sentences highlighting the main topics and decisions.
            |</|user|>
            |<|assistant|>
            """.trimMargin()
    }

    fun stopGeneration() {
        inferenceEngine.stopGeneration()
    }

    override fun onCleared() {
        super.onCleared()
        inferenceEngine.release()
    }
}
```

### UI Integration (Jetpack Compose)

```kotlin
// File: ui/screens/summary/SummaryGenerationScreen.kt
package com.summarizer.app.ui.screens.summary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SummaryGenerationScreen(
    threadId: String,
    viewModel: SummaryGenerationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate Summary") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Model status
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (uiState.modelLoaded) "Model Ready" else "Loading Model...",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (uiState.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Generated summary
            if (uiState.generatedText.isNotEmpty()) {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Summary",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = uiState.generatedText)
                    }
                }
            }

            // Generation status
            if (uiState.isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Error display
            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* viewModel.generateSummary() */ },
                    enabled = uiState.modelLoaded && !uiState.isGenerating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Generate Summary")
                }

                if (uiState.isGenerating) {
                    OutlinedButton(
                        onClick = { viewModel.stopGeneration() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}
```

### Hilt Dependency Injection

```kotlin
// File: di/AIModule.kt
package com.summarizer.app.di

import android.content.ContentResolver
import android.content.Context
import com.summarizer.app.data.ai.LlamaInferenceEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    @Provides
    @Singleton
    fun provideContentResolver(
        @ApplicationContext context: Context
    ): ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun provideLlamaInferenceEngine(
        contentResolver: ContentResolver
    ): LlamaInferenceEngine = LlamaInferenceEngine(contentResolver)
}
```

---

## 5. Performance Expectations

### Model: TinyLlama 1.1B Q4_K_M (~700MB)

#### Load Time
- **Expected**: 1-3 seconds
- **Optimization**: mmap enabled (memory-mapped file access)
- **First Load**: Slightly slower (file caching)
- **Subsequent Loads**: Near-instant (OS file cache)

#### Memory Usage
- **Model Size**: 700MB (Q4_K_M quantization)
- **Runtime Overhead**: 200-500MB (context, buffers)
- **Total RAM**: ~1-1.5GB
- **Safe for**: 4GB+ devices (as targeted)

#### Inference Speed (Tokens/Second)

| Device Tier | Processor Example | Expected Speed | Use Case Suitability |
|-------------|-------------------|----------------|---------------------|
| Budget | Snapdragon 680 | 5-8 t/s | ⚠️ Acceptable (slow) |
| Mid-Range | Snapdragon 778G | 10-15 t/s | ✅ Good (recommended) |
| Flagship | Snapdragon 8 Gen 2 | 20-30 t/s | ✅ Excellent (fast) |

**Summary Length Estimate:**
- 50 words = 65-80 tokens
- At 10 t/s: ~7 seconds
- At 15 t/s: ~5 seconds
- **User Experience**: Acceptable for batch summarization

#### Benchmarking Context
- Raspberry Pi 4 (4GB): 8-12 t/s
- Orange Pi 5 Pro (ARM): 27 t/s
- Desktop i5/M1 (16GB): 40-60 t/s

### Performance Optimizations

1. **ARM-Specific**
   - Automatic i8mm/dotprod detection
   - Native arm64-v8a compilation
   - NEON SIMD utilization

2. **Memory Management**
   - Context shift for long conversations
   - Batch interruption (UI responsiveness)
   - mmap for zero-copy model loading

3. **Android Integration**
   - Background thread execution (Dispatchers.IO)
   - Flow-based streaming (no UI blocking)
   - Lifecycle-aware cleanup

---

## 6. Error Handling & Edge Cases

### Model Loading Errors

```kotlin
sealed class ModelLoadError {
    data class FileNotFound(val path: String) : ModelLoadError()
    data class InvalidFormat(val message: String) : ModelLoadError()
    data class InsufficientMemory(val required: Long, val available: Long) : ModelLoadError()
    data class UnsupportedArchitecture(val detected: String) : ModelLoadError()
    object Unknown : ModelLoadError()
}

fun LlamaInferenceEngine.loadModelSafely(
    modelPath: String,
    contextLength: Int = 2048
): Result<Int> = runCatching {
    // Pre-flight checks
    val file = File(modelPath)
    if (!file.exists()) {
        throw ModelLoadError.FileNotFound(modelPath)
    }

    if (!file.name.endsWith(".gguf", ignoreCase = true)) {
        throw ModelLoadError.InvalidFormat("Not a GGUF file: ${file.name}")
    }

    // Check available memory
    val runtime = Runtime.getRuntime()
    val availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    val requiredMemory = file.length() + (200 * 1024 * 1024) // Model + 200MB overhead

    if (availableMemory < requiredMemory) {
        throw ModelLoadError.InsufficientMemory(requiredMemory, availableMemory)
    }

    // Attempt load
    var contextId: Int? = null
    loadModel(
        modelPath = modelPath,
        contextLength = contextLength,
        onLoaded = { contextId = it },
        onError = { error -> throw ModelLoadError.InvalidFormat(error) }
    )

    contextId ?: throw ModelLoadError.Unknown
}
```

### Generation Errors

```kotlin
sealed class GenerationError {
    object ModelNotLoaded : GenerationError()
    data class InvalidPrompt(val reason: String) : GenerationError()
    data class ContextOverflow(val tokens: Int, val maxTokens: Int) : GenerationError()
    object Cancelled : GenerationError()
    data class Unknown(val message: String) : GenerationError()
}

fun SummaryGenerationViewModel.generateSummarySafely(
    thread: Thread,
    messages: List<String>
): Result<Unit> = runCatching {
    if (!uiState.value.modelLoaded) {
        throw GenerationError.ModelNotLoaded
    }

    if (messages.isEmpty()) {
        throw GenerationError.InvalidPrompt("No messages to summarize")
    }

    val prompt = buildSummaryPrompt(thread.name, messages)
    val estimatedTokens = prompt.length / 4 // Rough estimate

    if (estimatedTokens > 2048) {
        throw GenerationError.ContextOverflow(estimatedTokens, 2048)
    }

    viewModelScope.launch {
        inferenceEngine.generate(prompt)
    }
}
```

### Lifecycle Edge Cases

```kotlin
class SummaryGenerationViewModel {
    // Prevent multiple simultaneous generations
    private var generationJob: Job? = null

    fun generateSummary(thread: Thread, messages: List<String>) {
        generationJob?.cancel() // Cancel previous if exists

        generationJob = viewModelScope.launch {
            try {
                inferenceEngine.generate(buildSummaryPrompt(thread.name, messages))
            } catch (e: CancellationException) {
                Timber.d("Generation cancelled")
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        inferenceEngine.release()
    }
}
```

---

## 7. Potential Issues & Mitigations

### Issue 1: Library Alpha Status
**Risk**: API changes in future versions
**Mitigation**:
- Pin exact version in Gradle: `io.github.ljcamargo:llamacpp-kotlin:0.2.0`
- Document API usage patterns
- Prepare wrapper abstraction for easy migration

**Impact**: Low (API is stable enough for production)

---

### Issue 2: ARM-Only Support
**Risk**: Won't work on x86 emulators or tablets
**Mitigation**:
- Target real ARM devices (already in requirements)
- Use ARM emulator images for testing
- Document architecture requirement

**Impact**: None (our target is ARM devices)

---

### Issue 3: Single Context Limitation
**Risk**: Can't run multiple inferences simultaneously
**Mitigation**:
- Queue summarization requests
- Cancel previous before starting new
- Use single-threaded generation approach

**Impact**: None (our use case is sequential summaries)

---

### Issue 4: No ProGuard Documentation
**Risk**: Release builds may strip native symbols
**Mitigation**:
- Add conservative keep rules (shown in Section 3)
- Test release builds thoroughly
- Monitor crash reports for JNI errors

**Impact**: Low (standard JNI keep rules should suffice)

---

### Issue 5: Model Load Time on Slow Storage
**Risk**: 3+ second load on older devices with slow eMMC
**Mitigation**:
- Load model on app startup (amortize cost)
- Show loading indicator
- Cache model in memory (singleton pattern)
- Use internal storage (faster than SD card)

**Impact**: Medium (acceptable for batch processing)

---

### Issue 6: Memory Pressure on 4GB Devices
**Risk**: OOM kills on devices with many background apps
**Mitigation**:
- Check available memory before loading
- Release model after inactivity timeout
- Advise users to close background apps
- Consider smaller model variant if needed

**Impact**: Medium (testing will reveal if critical)

---

### Issue 7: Background Processing Limits
**Risk**: Android may kill background summarization
**Mitigation**:
- Use Foreground Service for long summaries
- Show persistent notification during inference
- Save partial progress
- Implement resume capability

**Impact**: Low (summaries complete in 5-10 seconds)

---

## 8. Testing Strategy

### Unit Tests

```kotlin
@Test
fun `test model loads successfully with valid GGUF`() = runTest {
    val engine = LlamaInferenceEngine(contentResolver)
    val modelPath = "/sdcard/Download/tinyllama.gguf"

    var loadedContextId: Int? = null
    engine.loadModel(
        modelPath = modelPath,
        contextLength = 2048,
        onLoaded = { loadedContextId = it },
        onError = { fail("Model load should succeed") }
    )

    assertNotNull(loadedContextId)
    assertTrue(engine.isModelLoaded)
}

@Test
fun `test generation emits tokens via Flow`() = runTest {
    val engine = LlamaInferenceEngine(contentResolver)
    // ... load model ...

    val events = mutableListOf<LlamaHelper.LLMEvent>()
    val job = launch {
        engine.llmEvents.take(5).toList(events)
    }

    engine.generate("Test prompt")
    job.join()

    assertTrue(events.any { it is LlamaHelper.LLMEvent.Started })
    assertTrue(events.any { it is LlamaHelper.LLMEvent.Token })
}
```

### Integration Tests

```kotlin
@Test
fun `test end-to-end summary generation`() = runTest {
    val viewModel = SummaryGenerationViewModel(
        inferenceEngine = inferenceEngine,
        modelRepository = modelRepository,
        summaryRepository = summaryRepository
    )

    val thread = Thread(id = "test", name = "Test Group")
    val messages = listOf("Hello", "How are you?", "Great!")

    viewModel.generateSummary(thread, messages)

    advanceTimeBy(10_000) // Wait for generation

    val finalState = viewModel.uiState.value
    assertFalse(finalState.isGenerating)
    assertTrue(finalState.generatedText.isNotEmpty())
    assertNull(finalState.error)
}
```

### Manual Testing Checklist

- [ ] Model loads successfully from internal storage
- [ ] Model loads successfully from external storage
- [ ] Generation produces coherent summary
- [ ] Streaming updates UI in real-time
- [ ] Stop button cancels generation mid-stream
- [ ] Error displayed if model file missing
- [ ] Error displayed if insufficient memory
- [ ] ViewModel cleanup releases resources
- [ ] App rotation preserves generation state
- [ ] Background/foreground transition handling
- [ ] Release build works (ProGuard doesn't break JNI)
- [ ] Performance acceptable on mid-range device
- [ ] Memory usage stays under 1.5GB

---

## 9. Alternative Considered: Llamatik

If kotlinllamacpp proves problematic, **Llamatik** is the recommended fallback:

### Migration Path

```kotlin
// Replace LlamaHelper with LlamaBridge
import com.llamatik.LlamaBridge

class LlamaInferenceEngine @Inject constructor() {

    fun loadModel(modelPath: String, onLoaded: () -> Unit, onError: (String) -> Unit) {
        val result = LlamaBridge.initGenerateModel(modelPath)
        if (result) {
            onLoaded()
        } else {
            onError("Failed to initialize model")
        }
    }

    suspend fun generate(prompt: String, onToken: (String) -> Unit) {
        LlamaBridge.generateStream(prompt, object : LlamaBridge.GenStream {
            override fun onDelta(text: String) = onToken(text)
            override fun onComplete() { /* handle completion */ }
            override fun onError(message: String) { /* handle error */ }
        })
    }

    fun release() {
        LlamaBridge.shutdown()
    }
}
```

### Llamatik Advantages Over kotlinllamacpp
- More active maintenance
- KMP future-proofing
- JSON schema support (future features)
- Cleaner API design

### Llamatik Disadvantages
- Less Android-specific optimization
- Larger library size
- Single model limitation (same as kotlinllamacpp)

---

## 10. Implementation Timeline

### Day 1: Setup (2 hours)
- [x] Research complete
- [ ] Add Maven dependency
- [ ] Create AIModule for Hilt
- [ ] Create LlamaInferenceEngine wrapper
- [ ] Add ProGuard rules
- [ ] Test dependency integration

### Day 2: Core Integration (4 hours)
- [ ] Implement model loading logic
- [ ] Create SummaryGenerationViewModel
- [ ] Wire up Flow observation
- [ ] Implement prompt template
- [ ] Add error handling
- [ ] Test basic generation

### Day 3: UI Integration (3 hours)
- [ ] Create SummaryGenerationScreen
- [ ] Add "Summarize" button to ThreadDetailScreen
- [ ] Implement streaming UI updates
- [ ] Add loading states
- [ ] Add error display
- [ ] Add stop button

### Day 4: Testing & Polish (3 hours)
- [ ] Manual testing on device
- [ ] Performance profiling
- [ ] Memory usage analysis
- [ ] Edge case handling
- [ ] Release build testing
- [ ] Documentation

**Total Estimated Time**: 12 hours (1.5 days)

---

## 11. Success Criteria

### Functional Requirements
✅ Model loads from downloaded GGUF file
✅ Generation produces coherent summaries
✅ Streaming updates UI in real-time
✅ User can cancel generation mid-stream
✅ Errors handled gracefully
✅ Resources released properly

### Performance Requirements
✅ Model loads in <3 seconds
✅ Generation speed >8 tokens/second on mid-range device
✅ Memory usage <1.5GB
✅ No UI freezing during generation
✅ App remains responsive

### Quality Requirements
✅ No crashes in release build
✅ ProGuard doesn't break native code
✅ Lifecycle transitions handled correctly
✅ State preserved across rotations
✅ Clear error messages for users

---

## 12. Final Recommendation

**Use kotlinllamacpp v0.2.0** for the following reasons:

1. **Perfect Architecture Fit**: Built specifically for Android native apps with Jetpack Compose
2. **ARM Optimization**: Direct targeting of our device tier (Snapdragon, MediaTek, etc.)
3. **Lowest Friction**: Single Maven dependency, no manual builds required
4. **Clean Integration**: ViewModel lifecycle support, Kotlin Flow, coroutines
5. **Proven Performance**: ARM-specific optimizations outweigh generic KMP approaches
6. **Low Risk**: Alpha status is acceptable given API stability and wrapper abstraction
7. **Quick Implementation**: Estimated 1.5 days vs. 3+ days for official binding

**Contingency**: If issues arise, Llamatik provides a clean migration path with minimal code changes.

---

## 13. References & Sources

### Primary Sources
- [kotlinllamacpp GitHub Repository](https://github.com/ljcamargo/kotlinllamacpp)
- [Llamatik GitHub Repository](https://github.com/ferranpons/Llamatik)
- [llama-cpp-kt GitHub Repository](https://github.com/hurui200320/llama-cpp-kt)
- [Official llama.cpp Android Documentation](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md)

### Technical Articles
- [How to Run LLMs Offline on Android Using Kotlin - DEV Community](https://dev.to/ferranpons/how-to-run-llms-offline-on-android-using-kotlin-407g)
- [How to compile LLM on Android using LLama.cpp - Medium](https://medium.com/@mmonteirojs/how-to-compile-any-llm-on-android-using-llama-cpp-46885569768d)
- [Run Gemma and VLMs on mobile with llama.cpp - Medium](https://farmaker47.medium.com/run-gemma-and-vlms-on-mobile-with-llama-cpp-dbb6e1b19a93)

### Performance & Optimization
- [Large Language Model Performance Benchmarking on Mobile Platforms](https://arxiv.org/html/2410.03613v1)
- [LLaMA Now Goes Faster on CPUs - Justine Tunney](https://justine.lol/matmul/)
- [The Practical Quantization Guide for iPhone and Mac - Enclave AI](https://enclaveai.app/blog/2025/11/12/practical-quantization-guide-iphone-mac-gguf/)
- [Best Sub-3B GGUF Models for Mid-Range CPUs](https://ggufloader.github.io/2025-07-07-top-10-gguf-models-i5-16gb.html)

### Android Development
- [ViewModel overview - Android Developers](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Handling lifecycles with lifecycle-aware components - Android Developers](https://developer.android.com/topic/libraries/architecture/lifecycle)
- [Jetpack Compose Performance - Android Developers](https://developer.android.com/develop/ui/compose/performance)
- [Configure and troubleshoot R8 Keep Rules - Android Developers Blog](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html)

### Community Resources
- [Llamatik Website](https://www.llamatik.com/)
- [Llamatik on Maven Central](https://libraries.io/maven/com.llamatik:library-android)
- [GitHub - shubham0204/SmolChat-Android](https://github.com/shubham0204/SmolChat-Android)
- [GitHub - JackZeng0208/llama.cpp-android-tutorial](https://github.com/JackZeng0208/llama.cpp-android-tutorial)

---

**Document Status**: Complete
**Next Action**: Begin implementation (Week 5 tasks)
**Owner**: WhatsApp Summarizer Development Team
**Last Updated**: 2026-01-31
