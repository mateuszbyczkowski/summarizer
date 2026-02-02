# Technical Specification
# WhatsApp Thread Summarizer

## 1. Technical Overview

### 1.1 Technology Stack

#### Core Framework
- **Language**: Kotlin 1.9+
- **Minimum SDK**: Android API 29 (Android 10.0)
- **Target SDK**: Android API 34 (Android 14)
- **Build System**: Gradle with Kotlin DSL

#### Android Architecture Components
- **Architecture Pattern**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: Hilt (Dagger-based)
- **Navigation**: Jetpack Navigation Component
- **UI**: Jetpack Compose (declarative UI)
- **Concurrency**: Kotlin Coroutines + Flow
- **Background Work**: WorkManager
- **Local Storage**: Room Database
- **Security**: Jetpack Security (EncryptedSharedPreferences, EncryptedFile)
- **Biometrics**: BiometricPrompt API

#### AI Inference Frameworks (Pluggable Architecture)
1. **llama.cpp** via llama-cpp-android
   - Pros: Wide model support, mature, well-optimized
   - Use case: Primary recommendation for most models

2. **MediaPipe LLM Inference**
   - Pros: Official Google support, easy integration
   - Use case: Gemma models specifically

3. **MLC-LLM**
   - Pros: Excellent performance, TVM-based optimization
   - Use case: Maximum performance on supported devices

#### Additional Libraries
- **HTTP Client**: OkHttp (for model downloads only)
- **JSON**: Kotlinx Serialization
- **Logging**: Timber
- **Analytics**: None (privacy-focused)

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Presentation Layer                   │
│                    (Jetpack Compose)                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │  Home    │ │ Threads  │ │  Search  │ │ Settings │  │
│  │  Screen  │ │  Screen  │ │  Screen  │ │  Screen  │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                   ViewModel Layer                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │HomeView  │ │ThreadsVM │ │SearchVM  │ │SettingsVM│  │
│  │  Model   │ │          │ │          │ │          │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                   Domain Layer                           │
│  ┌─────────────────────────────────────────────────┐   │
│  │              Use Cases / Interactors             │   │
│  │  - CaptureMessagesUseCase                        │   │
│  │  - GenerateSummaryUseCase                        │   │
│  │  - SearchSummariesUseCase                        │   │
│  │  - ManageModelsUseCase                           │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                    Data Layer                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │ Message  │ │ Summary  │ │   AI     │ │ Security │  │
│  │Repository│ │Repository│ │Repository│ │Repository│  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                 Infrastructure Layer                     │
│  ┌────────────┐ ┌────────────┐ ┌────────────────────┐  │
│  │ Room DB    │ │  AI Engine │ │ Notification       │  │
│  │ (Encrypted)│ │  Manager   │ │ Listener Service   │  │
│  └────────────┘ └────────────┘ └────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Module Structure

```
app/
├── ui/                          # Presentation Layer
│   ├── theme/                   # Compose theme
│   ├── navigation/              # Navigation graph
│   ├── screens/
│   │   ├── home/
│   │   ├── threads/
│   │   ├── search/
│   │   ├── settings/
│   │   ├── onboarding/
│   │   └── auth/
│   └── components/              # Reusable UI components
│
├── viewmodel/                   # ViewModel Layer
│
├── domain/                      # Domain Layer
│   ├── model/                   # Domain models
│   ├── usecase/                 # Business logic
│   └── repository/              # Repository interfaces
│
├── data/                        # Data Layer
│   ├── repository/              # Repository implementations
│   ├── local/
│   │   ├── db/                  # Room database
│   │   ├── preferences/         # Encrypted SharedPreferences
│   │   └── file/                # Local file storage
│   └── model/                   # Data models (entities)
│
├── infrastructure/              # Infrastructure Layer
│   ├── service/
│   │   ├── NotificationListenerService
│   │   └── SummaryWorker
│   ├── ai/
│   │   ├── engine/              # AI inference abstraction
│   │   ├── llamacpp/            # llama.cpp implementation
│   │   ├── mediapipe/           # MediaPipe implementation
│   │   └── mlc/                 # MLC-LLM implementation
│   └── security/
│       ├── encryption/
│       └── biometric/
│
└── di/                          # Dependency Injection (Hilt modules)
```

---

## 3. Core Components Design

### 3.1 Message Capture System

#### NotificationListenerService Implementation

```kotlin
class WhatsAppNotificationListener : NotificationListenerService() {

    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var notificationParser: NotificationParser

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Filter for WhatsApp notifications
        if (!isWhatsAppNotification(sbn)) return

        val parsedMessage = notificationParser.parse(sbn)

        // Check if it's a group message
        if (parsedMessage.isGroupMessage) {
            scope.launch {
                messageRepository.saveMessage(parsedMessage)
            }
        }
    }

    private fun isWhatsAppNotification(sbn: StatusBarNotification): Boolean {
        return sbn.packageName in listOf(
            "com.whatsapp",
            "com.whatsapp.w4b"  // WhatsApp Business
        )
    }
}
```

#### Message Data Model

```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val threadId: String,           // Unique group identifier
    val threadName: String,         // Group name
    val sender: String,             // Sender name
    val content: String,            // Message text
    val timestamp: Long,            // Unix timestamp
    val messageType: MessageType,   // TEXT, IMAGE_WITH_CAPTION, LINK, etc.
    val isRead: Boolean = false,
    val capturedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey
    val threadId: String,

    val threadName: String,
    val priority: Priority = Priority.NORMAL,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val lastMessageTimestamp: Long,
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class Priority {
    HIGH, NORMAL, LOW
}

enum class MessageType {
    TEXT, IMAGE_WITH_CAPTION, LINK, LOCATION, CONTACT, UNKNOWN
}
```

### 3.2 AI Summarization System

#### AI Engine Abstraction

```kotlin
interface AIEngine {
    suspend fun initialize(modelPath: String): Result<Unit>
    suspend fun generate(prompt: String, maxTokens: Int = 512): Result<String>
    suspend fun release()
    fun isLoaded(): Boolean
    fun getModelInfo(): ModelInfo
}

data class ModelInfo(
    val name: String,
    val size: Long,
    val framework: AIFramework,
    val capabilities: List<String>
)

enum class AIFramework {
    LLAMA_CPP, MEDIAPIPE, MLC_LLM
}
```

#### AI Engine Manager

```kotlin
class AIEngineManager @Inject constructor(
    private val llamaCppEngine: LlamaCppEngine,
    private val mediaPipeEngine: MediaPipeEngine,
    private val mlcEngine: MlcEngine,
    private val preferences: PreferencesRepository
) {
    private var currentEngine: AIEngine? = null

    suspend fun loadModel(model: AIModel): Result<Unit> {
        // Select appropriate engine based on model
        currentEngine = when (model.framework) {
            AIFramework.LLAMA_CPP -> llamaCppEngine
            AIFramework.MEDIAPIPE -> mediaPipeEngine
            AIFramework.MLC_LLM -> mlcEngine
        }

        return currentEngine?.initialize(model.path) ?: Result.failure(
            IllegalStateException("No engine available")
        )
    }

    suspend fun generateSummary(messages: List<Message>): Result<Summary> {
        val engine = currentEngine ?: return Result.failure(
            IllegalStateException("No model loaded")
        )

        val prompt = buildSummarizationPrompt(messages)

        return engine.generate(prompt, maxTokens = 512)
            .map { parseSummaryResponse(it) }
    }

    private fun buildSummarizationPrompt(messages: List<Message>): String {
        return """
            You are summarizing a WhatsApp group conversation for a busy parent.

            Instructions:
            1. Extract key topics discussed
            2. List action items with deadlines
            3. Highlight important announcements
            4. Note who said important things (participant highlights)

            Format your response as JSON:
            {
                "key_topics": ["topic1", "topic2"],
                "action_items": [
                    {"task": "bring snacks", "deadline": "Friday", "mentioned_by": "Teacher"}
                ],
                "announcements": ["School closed Monday"],
                "participant_highlights": [
                    {"person": "Teacher Name", "message": "Important point"}
                ]
            }

            Messages:
            ${messages.joinToString("\n") { "[${it.sender}]: ${it.content}" }}

            Summary:
        """.trimIndent()
    }

    private fun parseSummaryResponse(response: String): Summary {
        // Parse JSON response and create Summary object
        // Include error handling for malformed responses
    }
}
```

#### Summary Data Model

```kotlin
@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val threadId: String,
    val threadName: String,
    val startTimestamp: Long,       // First message in summary
    val endTimestamp: Long,         // Last message in summary
    val messageCount: Int,

    @ColumnInfo(name = "key_topics")
    val keyTopics: List<String>,

    @ColumnInfo(name = "action_items")
    val actionItems: List<ActionItem>,

    @ColumnInfo(name = "announcements")
    val announcements: List<String>,

    @ColumnInfo(name = "participant_highlights")
    val participantHighlights: List<ParticipantHighlight>,

    val generatedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Serializable
data class ActionItem(
    val task: String,
    val deadline: String?,
    val mentionedBy: String?
)

@Serializable
data class ParticipantHighlight(
    val person: String,
    val message: String
)
```

### 3.3 Security System

#### PIN and Biometric Authentication

```kotlin
class AuthenticationManager @Inject constructor(
    private val encryptedPreferences: EncryptedSharedPreferences,
    private val biometricPrompt: BiometricPrompt
) {
    fun setupPIN(pin: String): Result<Unit> {
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            return Result.failure(InvalidPINException("PIN must be 6 digits"))
        }

        val hashedPIN = hashPIN(pin)
        encryptedPreferences.edit()
            .putString(KEY_PIN_HASH, hashedPIN)
            .apply()

        return Result.success(Unit)
    }

    fun verifyPIN(pin: String): Boolean {
        val storedHash = encryptedPreferences.getString(KEY_PIN_HASH, null)
            ?: return false

        return hashPIN(pin) == storedHash
    }

    fun authenticateWithBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate")
            .setSubtitle("Use biometric to unlock")
            .setNegativeButtonText("Use PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun hashPIN(pin: String): String {
        // Use SHA-256 with salt
        val salt = encryptedPreferences.getString(KEY_SALT, null)
            ?: generateSalt().also {
                encryptedPreferences.edit().putString(KEY_SALT, it).apply()
            }

        return MessageDigest.getInstance("SHA-256")
            .digest((pin + salt).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): String {
        return UUID.randomUUID().toString()
    }
}
```

#### Data Encryption

```kotlin
class EncryptionManager @Inject constructor(
    private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    fun getEncryptedSharedPreferences(): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun encryptFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }
}
```

### 3.4 Background Processing

#### Daily Summary Worker

```kotlin
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters,
    private val summaryUseCase: GenerateSummaryUseCase,
    private val notificationManager: SummaryNotificationManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val summaries = summaryUseCase.generateDailySummaries()

            if (summaries.isNotEmpty()) {
                notificationManager.showSummaryNotification(summaries.size)
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate daily summaries")
            Result.retry()
        }
    }

    companion object {
        fun schedule(workManager: WorkManager, hour: Int, minute: Int) {
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)

                if (before(currentDate)) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

            val dailyWorkRequest = PeriodicWorkRequestBuilder<DailySummaryWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                "daily_summary",
                ExistingPeriodicWorkPolicy.REPLACE,
                dailyWorkRequest
            )
        }
    }
}
```

#### Data Cleanup Worker

```kotlin
class DataCleanupWorker(
    context: Context,
    params: WorkerParameters,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val cutoffDate = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
            messageRepository.deleteMessagesBefore(cutoffDate)

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old messages")
            Result.retry()
        }
    }

    companion fun {
        fun schedule(workManager: WorkManager) {
            val cleanupRequest = PeriodicWorkRequestBuilder<DataCleanupWorker>(
                7, TimeUnit.DAYS  // Run weekly
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresDeviceIdle(true)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                "data_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )
        }
    }
}
```

---

## 4. Database Schema

### 4.1 Room Database Design

```kotlin
@Database(
    entities = [
        MessageEntity::class,
        ThreadEntity::class,
        SummaryEntity::class,
        AIModelEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun threadDao(): ThreadDao
    abstract fun summaryDao(): SummaryDao
    abstract fun modelDao(): AIModelDao

    companion object {
        fun build(context: Context): AppDatabase {
            // Use SQLCipher for encryption
            val passphrase = getOrCreateDatabasePassphrase(context)

            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "app_database"
            )
                .openHelperFactory(SupportFactory(passphrase))
                .build()
        }

        private fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
            // Retrieve from Android Keystore
        }
    }
}
```

### 4.2 DAOs

```kotlin
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp DESC")
    fun getMessagesForThread(threadId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteMessagesBefore(cutoffTimestamp: Long)

    @Query("SELECT * FROM messages WHERE threadId = :threadId AND timestamp BETWEEN :start AND :end")
    suspend fun getMessagesInRange(threadId: String, start: Long, end: Long): List<MessageEntity>
}

@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads WHERE isArchived = 0 ORDER BY lastMessageTimestamp DESC")
    fun getAllActiveThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE priority = :priority")
    fun getThreadsByPriority(priority: Priority): Flow<List<ThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ThreadEntity)

    @Update
    suspend fun updateThread(thread: ThreadEntity)
}

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE threadId = :threadId ORDER BY endTimestamp DESC")
    fun getSummariesForThread(threadId: String): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries ORDER BY generatedAt DESC LIMIT :limit")
    fun getRecentSummaries(limit: Int = 20): Flow<List<SummaryEntity>>

    @Insert
    suspend fun insertSummary(summary: SummaryEntity)

    @Query("""
        SELECT * FROM summaries
        WHERE key_topics LIKE '%' || :query || '%'
        OR announcements LIKE '%' || :query || '%'
    """)
    suspend fun searchSummaries(query: String): List<SummaryEntity>
}
```

---

## 5. AI Model Management

### 5.1 Supported Models

| Model Name | Size | Framework | Quantization | Min RAM |
|------------|------|-----------|--------------|---------|
| Gemma-2B-Instruct | 1.2 GB | llama.cpp | Q4_K_M | 3 GB |
| Phi-2 | 1.6 GB | llama.cpp | Q4_K_M | 4 GB |
| Gemma-7B-Instruct | 2.8 GB | llama.cpp / MediaPipe | Q3_K_M | 6 GB |
| Phi-3-Mini | 2.3 GB | llama.cpp / MLC-LLM | Q4_K_M | 5 GB |

### 5.2 Model Download System

```kotlin
class ModelDownloadManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val modelRepository: AIModelRepository
) {
    fun downloadModel(
        model: AIModel,
        onProgress: (Float) -> Unit
    ): Flow<DownloadResult> = flow {
        val request = Request.Builder()
            .url(model.downloadUrl)
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            emit(DownloadResult.Error("Download failed: ${response.code}"))
            return@flow
        }

        val body = response.body ?: throw IllegalStateException("Empty response body")
        val contentLength = body.contentLength()

        val file = File(getModelsDirectory(), model.fileName)
        val outputStream = file.outputStream()
        val inputStream = body.byteStream()

        var bytesCopied = 0L
        val buffer = ByteArray(8 * 1024)
        var bytes = inputStream.read(buffer)

        while (bytes >= 0) {
            outputStream.write(buffer, 0, bytes)
            bytesCopied += bytes
            bytes = inputStream.read(buffer)

            val progress = bytesCopied.toFloat() / contentLength.toFloat()
            onProgress(progress)
            emit(DownloadResult.Progress(progress))
        }

        outputStream.close()
        inputStream.close()

        // Save model metadata
        modelRepository.saveModel(model.copy(
            path = file.absolutePath,
            isDownloaded = true
        ))

        emit(DownloadResult.Success(file.absolutePath))
    }.flowOn(Dispatchers.IO)

    private fun getModelsDirectory(): File {
        return File(context.filesDir, "models").apply {
            if (!exists()) mkdirs()
        }
    }
}

sealed class DownloadResult {
    data class Progress(val progress: Float) : DownloadResult()
    data class Success(val path: String) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}
```

---

## 6. Performance Optimization

### 6.1 Memory Management
- Use paging for large message lists
- Limit loaded messages to last 1000 per thread
- Clear AI model from memory when not in use
- Use LazyColumn/LazyRow for efficient lists

### 6.2 Database Optimization
- Index frequently queried columns (threadId, timestamp)
- Use database transactions for bulk operations
- Implement database pagination with Paging 3
- Regular VACUUM operations

### 6.3 AI Inference Optimization
- Model quantization (Q4, Q3 for mobile)
- Batch processing for multiple threads
- Priority queue for high-priority threads
- Early stopping for generation
- Context window management (chunk long conversations)

---

## 7. Testing Strategy

### 7.1 Unit Tests
- Repository layer logic
- ViewModel business logic
- Use case implementations
- Utility functions
- Mock AI engine responses

### 7.2 Integration Tests
- Database operations
- NotificationListenerService parsing
- End-to-end summarization flow
- Model loading and inference

### 7.3 UI Tests (Compose)
- Onboarding flow
- Authentication screens
- Summary display
- Search functionality

### 7.4 Performance Tests
- AI inference latency
- Database query performance
- Memory usage profiling
- Battery consumption testing

---

## 8. Security Considerations

### 8.1 Data Protection
- ✅ Encrypted database (SQLCipher)
- ✅ Encrypted SharedPreferences
- ✅ Secure PIN hashing (SHA-256 + salt)
- ✅ Android Keystore for key management
- ✅ No data in Android logs (release build)

### 8.2 Permission Security
- Request permissions with clear rationale
- Handle permission revocation gracefully
- Minimal permission set

### 8.3 Code Security
- ProGuard/R8 code obfuscation
- Certificate pinning for model downloads
- Input validation for all user data
- No hardcoded secrets

---

## 9. Deployment

### 9.1 Build Variants
```kotlin
android {
    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 9.2 Release Checklist
- [ ] Security audit completed
- [ ] Performance benchmarks passed
- [ ] All unit tests passing
- [ ] Integration tests passing
- [ ] ProGuard rules tested
- [ ] Model download URLs verified
- [ ] Privacy policy included
- [ ] App signing configured

---

**Document Version**: 1.0
**Last Updated**: 2026-01-31
**Status**: Technical Review
