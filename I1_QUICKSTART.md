# I1 Quick Start Guide
# Getting Started with Development

This guide will help you start building the I1 minimal MVP immediately.

---

## Prerequisites

### Required Tools
- **Android Studio**: Hedgehog (2023.1.1) or newer
- **JDK**: 17 or newer
- **Git**: For version control
- **Android Device/Emulator**: Android 12+ (API 31+)

### Knowledge Requirements
- Kotlin basics
- Jetpack Compose fundamentals
- Room database
- Kotlin Coroutines

---

## Step 1: Project Setup (Day 1)

### 1.1 Create New Android Project

```bash
# Open Android Studio
# File → New → New Project
# Select "Empty Activity" (Compose)
```

**Configuration**:
- Name: `Summarizer`
- Package: `com.summarizer.app`
- Save location: `/Users/mateusz.byczkowski/Dev/covantis/others/summarizer`
- Language: Kotlin
- Minimum SDK: API 31 (Android 12.0)
- Build configuration language: Kotlin DSL

### 1.2 Update `build.gradle.kts` (Project Level)

```kotlin
// build.gradle.kts (Project)
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
}
```

### 1.3 Update `build.gradle.kts` (App Module)

```kotlin
// build.gradle.kts (Module :app)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
}

android {
    namespace = "com.summarizer.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.summarizer.app"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-i1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // Hilt (Dependency Injection)
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room (Database)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // SQLCipher (Database Encryption)
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // OkHttp (Model Download)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // llama.cpp for Android
    // NOTE: You'll need to add this dependency once the library is available
    // For now, we'll integrate it manually in Week 5
    // implementation("com.github.karpathy:llama-cpp-android:0.1.0")

    // Timber (Logging)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

### 1.4 Sync Gradle

Click "Sync Now" in Android Studio to download all dependencies.

---

## Step 2: Project Structure (Day 1)

### 2.1 Create Package Structure

```
app/src/main/java/com/summarizer/app/
├── SummarizerApplication.kt
├── di/
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   └── RepositoryModule.kt
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── AppDatabase.kt
│   │   │   ├── Converters.kt
│   │   │   └── dao/
│   │   │       ├── MessageDao.kt
│   │   │       ├── ThreadDao.kt
│   │   │       └── SummaryDao.kt
│   │   ├── entity/
│   │   │   ├── MessageEntity.kt
│   │   │   ├── ThreadEntity.kt
│   │   │   └── SummaryEntity.kt
│   │   └── preferences/
│   │       └── SecurePreferences.kt
│   └── repository/
│       ├── MessageRepositoryImpl.kt
│       ├── ThreadRepositoryImpl.kt
│       ├── SummaryRepositoryImpl.kt
│       └── AuthRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── Message.kt
│   │   ├── Thread.kt
│   │   ├── Summary.kt
│   │   └── ActionItem.kt
│   └── repository/
│       ├── MessageRepository.kt
│       ├── ThreadRepository.kt
│       ├── SummaryRepository.kt
│       └── AuthRepository.kt
├── service/
│   └── WhatsAppNotificationListener.kt
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── navigation/
│   │   └── NavGraph.kt
│   └── screens/
│       ├── onboarding/
│       ├── auth/
│       ├── threads/
│       └── summary/
└── util/
    └── Constants.kt
```

### 2.2 Create Application Class

```kotlin
// SummarizerApplication.kt
package com.summarizer.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SummarizerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
```

### 2.3 Update AndroidManifest.xml

```xml
<!-- AndroidManifest.xml -->
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".SummarizerApplication"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Summarizer"
        tools:targetApi="31">

        <!-- Main Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Summarizer">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Notification Listener Service -->
        <service
            android:name=".service.WhatsAppNotificationListener"
            android:exported="false"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

---

## Step 3: Database Layer (Day 2-3)

### 3.1 Define Entities

See [I1_SCOPE.md - Database Schema](./I1_SCOPE.md#database-schema-simplified-for-i1) for complete entity definitions.

### 3.2 Create DAOs

```kotlin
// MessageDao.kt
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThread(threadId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId")
    suspend fun getMessageCount(threadId: String): Int
}
```

### 3.3 Create Database

```kotlin
// AppDatabase.kt
@Database(
    entities = [MessageEntity::class, ThreadEntity::class, SummaryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun threadDao(): ThreadDao
    abstract fun summaryDao(): SummaryDao
}
```

### 3.4 Database Module (Hilt)

```kotlin
// DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        val passphrase = SQLiteDatabase.getBytes("your-secure-passphrase".toCharArray())
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "summarizer_database"
        )
            .openHelperFactory(factory)
            .build()
    }

    @Provides
    fun provideMessageDao(database: AppDatabase) = database.messageDao()

    @Provides
    fun provideThreadDao(database: AppDatabase) = database.threadDao()

    @Provides
    fun provideSummaryDao(database: AppDatabase) = database.summaryDao()
}
```

---

## Step 4: First Milestone - Display Empty Thread List (Day 3-4)

### 4.1 Create Thread List Screen

```kotlin
// ThreadListScreen.kt
@Composable
fun ThreadListScreen(
    viewModel: ThreadListViewModel = hiltViewModel()
) {
    val threads by viewModel.threads.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Messages") })
        }
    ) { paddingValues ->
        if (threads.isEmpty()) {
            EmptyState(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                items(threads) { thread ->
                    ThreadItem(thread = thread)
                }
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No messages yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Messages will appear here once WhatsApp groups start posting")
        }
    }
}
```

### 4.2 Test on Emulator

Run the app and verify:
- App launches successfully
- Empty state displays correctly
- No crashes

---

## Step 5: Next Steps

Once you have the basic structure working:

1. **Week 2**: Implement NotificationListenerService (see [I1_SCOPE.md Week 2](./I1_SCOPE.md#week-2-message-capture))
2. **Week 3**: Build out UI screens
3. **Week 4**: Add model download
4. **Week 5**: Integrate llama.cpp and Phi-2
5. **Week 6**: Testing and polish

---

## Development Tips

### Testing with WhatsApp
1. Create a test WhatsApp group with friends/family
2. Ask them to send test messages
3. Verify app captures notifications correctly

### Debugging NotificationListenerService
```kotlin
override fun onNotificationPosted(sbn: StatusBarNotification) {
    Timber.d("Notification received: ${sbn.packageName}")
    // Log all notification details for debugging
}
```

### Quick Emulator Setup
```bash
# Create Android 12 emulator
# Tools → Device Manager → Create Device
# Select: Pixel 7
# System Image: Android 12 (API 31)
```

### Useful ADB Commands
```bash
# Check if notification listener is enabled
adb shell settings get secure enabled_notification_listeners

# Force notification listener restart
adb shell pm clear com.summarizer.app

# View logs
adb logcat -s SummarizerApp
```

---

## Common Issues & Solutions

### Issue: Room database error
**Solution**: Make sure KSP plugin is applied and synced

### Issue: Hilt compilation errors
**Solution**: Clean and rebuild project (`Build → Clean Project → Rebuild Project`)

### Issue: NotificationListenerService not receiving notifications
**Solution**:
1. Check permission in Settings → Apps → Summarizer → Notifications
2. Verify service is declared in manifest
3. Restart the app

### Issue: SQLCipher native library not found
**Solution**: Add `android:extractNativeLibs="true"` in manifest `<application>` tag

---

## Resources

### Official Documentation
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)

### llama.cpp Resources
- [llama.cpp GitHub](https://github.com/ggerganov/llama.cpp)
- [Phi-2 Model on Hugging Face](https://huggingface.co/TheBloke/Phi-2-GGUF)

### Learning Resources
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)

---

## Checkpoint: End of Week 1

You should have:
- ✅ Android Studio project created
- ✅ All dependencies configured
- ✅ Project structure in place
- ✅ Database layer implemented
- ✅ Empty thread list screen displaying
- ✅ App running on emulator

**Next**: Implement NotificationListenerService to capture WhatsApp messages

---

**Document Version**: 1.0
**Last Updated**: 2026-01-31
**For**: I1 Development
