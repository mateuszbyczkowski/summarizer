# iOS Migration Guide - Practical Implementation Examples

**Companion to:** [IOS_READINESS_ANALYSIS.md](IOS_READINESS_ANALYSIS.md)
**Purpose:** Hands-on code examples and migration patterns

---

## Table of Contents

1. [Quick Start: iOS Notification Prototype](#quick-start-ios-notification-prototype)
2. [Kotlin Multiplatform Setup](#kotlin-multiplatform-setup)
3. [Critical Component Migrations](#critical-component-migrations)
4. [Testing Strategy](#testing-strategy)
5. [Troubleshooting Common Issues](#troubleshooting-common-issues)

---

## Quick Start: iOS Notification Prototype

### Goal: Validate WhatsApp notification capture on iOS in 1 week

This prototype will help you determine if iOS development is viable BEFORE investing in full migration.

### Step 1: Create Minimal iOS App

```bash
# Using Xcode 15+
# File → New → Project → iOS → App
# Product Name: SummarizerPrototype
# Interface: SwiftUI
# Language: Swift
```

### Step 2: Add Notification Service Extension

```bash
# File → New → Target → Notification Service Extension
# Product Name: NotificationServiceExtension
```

### Step 3: Configure App Groups

**Enable in both targets:**
1. Select target → Signing & Capabilities
2. Click + Capability → App Groups
3. Add: `group.com.summarizer.prototype`

### Step 4: Implement Notification Capture

**NotificationServiceExtension/NotificationService.swift:**
```swift
import UserNotifications
import os.log

class NotificationService: UNNotificationServiceExtension {
    var contentHandler: ((UNNotificationContent) -> Void)?
    var bestAttemptContent: UNMutableNotificationContent?
    let logger = Logger(subsystem: "com.summarizer.prototype", category: "notifications")

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        self.contentHandler = contentHandler
        bestAttemptContent = (request.content.mutableCopy() as? UNMutableNotificationContent)

        guard let bestAttemptContent = bestAttemptContent else {
            contentHandler(request.content)
            return
        }

        // LOG EVERYTHING for analysis
        logger.info("🔔 Notification Received")
        logger.info("  Title: \(bestAttemptContent.title)")
        logger.info("  Subtitle: \(bestAttemptContent.subtitle)")
        logger.info("  Body: \(bestAttemptContent.body)")
        logger.info("  Badge: \(bestAttemptContent.badge?.intValue ?? 0)")
        logger.info("  Category: \(bestAttemptContent.categoryIdentifier)")
        logger.info("  Thread ID: \(bestAttemptContent.threadIdentifier)")
        logger.info("  User Info: \(bestAttemptContent.userInfo)")

        // Check if WhatsApp notification
        let isWhatsApp = isWhatsAppNotification(request)
        logger.info("  Is WhatsApp: \(isWhatsApp)")

        if isWhatsApp {
            captureMessage(bestAttemptContent)
        }

        contentHandler(bestAttemptContent)
    }

    private func isWhatsAppNotification(_ request: UNNotificationRequest) -> Bool {
        // WhatsApp bundle IDs
        let whatsappBundleIDs = [
            "net.whatsapp.WhatsApp",
            "net.whatsapp.WhatsAppSMB" // Business
        ]

        // iOS doesn't provide bundle ID in notification extension
        // We must infer from content patterns
        let title = bestAttemptContent?.title ?? ""
        let body = bestAttemptContent?.body ?? ""

        // WhatsApp specific patterns
        let hasColonFormat = body.contains(":") // "Sender: Message"
        let hasMentions = body.contains("@")
        let hasMediaIndicators = body.contains("image omitted") ||
                                 body.contains("video omitted") ||
                                 body.contains("audio omitted")

        return hasColonFormat || hasMentions || hasMediaIndicators
    }

    private func captureMessage(_ content: UNMutableNotificationContent) {
        let timestamp = Date().timeIntervalSince1970

        // Parse notification
        let threadName = content.title
        let messageBody = content.body

        // Extract sender and message
        var sender = "Unknown"
        var messageText = messageBody

        if let colonIndex = messageBody.firstIndex(of: ":") {
            sender = String(messageBody[..<colonIndex]).trimmingCharacters(in: .whitespaces)
            messageText = String(messageBody[messageBody.index(after: colonIndex)...])
                .trimmingCharacters(in: .whitespaces)
        }

        // Save to shared storage for analysis
        let data = CapturedMessage(
            threadName: threadName,
            sender: sender,
            message: messageText,
            timestamp: timestamp,
            rawTitle: content.title,
            rawBody: content.body,
            userInfo: content.userInfo.description
        )

        saveToSharedStorage(data)
        logger.info("✅ Message captured and saved")
    }

    private func saveToSharedStorage(_ message: CapturedMessage) {
        guard let groupURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.com.summarizer.prototype"
        ) else {
            logger.error("❌ Failed to access app group")
            return
        }

        let fileURL = groupURL.appendingPathComponent("captured_messages.json")

        // Append to JSON array
        var messages: [CapturedMessage] = []
        if let data = try? Data(contentsOf: fileURL),
           let existing = try? JSONDecoder().decode([CapturedMessage].self, from: data) {
            messages = existing
        }

        messages.append(message)

        if let encoded = try? JSONEncoder().encode(messages) {
            try? encoded.write(to: fileURL)
        }
    }

    override func serviceExtensionTimeWillExpire() {
        // Called when extension is about to be terminated
        logger.warning("⏰ Extension time expiring")
        if let contentHandler = contentHandler,
           let bestAttemptContent = bestAttemptContent {
            contentHandler(bestAttemptContent)
        }
    }
}

struct CapturedMessage: Codable {
    let threadName: String
    let sender: String
    let message: String
    let timestamp: TimeInterval
    let rawTitle: String
    let rawBody: String
    let userInfo: String
}
```

### Step 5: Main App to View Captured Messages

**ContentView.swift:**
```swift
import SwiftUI

struct ContentView: View {
    @State private var messages: [CapturedMessage] = []
    @State private var captureRate: Double = 0.0

    var body: some View {
        NavigationView {
            VStack {
                // Statistics
                VStack(alignment: .leading, spacing: 8) {
                    Text("Capture Statistics")
                        .font(.headline)

                    HStack {
                        Text("Messages Captured:")
                        Spacer()
                        Text("\(messages.count)")
                            .bold()
                    }

                    HStack {
                        Text("Estimated Success Rate:")
                        Spacer()
                        Text("\(Int(captureRate))%")
                            .foregroundColor(captureRateColor)
                            .bold()
                    }
                    .padding(.bottom)

                    Text("Compare this number to your actual WhatsApp message count to calculate success rate.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
                .padding()

                // Messages list
                List {
                    ForEach(messages.sorted(by: { $0.timestamp > $1.timestamp }), id: \.timestamp) { message in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(message.threadName)
                                .font(.headline)
                            Text("\(message.sender): \(message.message)")
                                .font(.body)
                                .foregroundColor(.secondary)
                            Text(formatDate(message.timestamp))
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                        .padding(.vertical, 4)
                    }
                }

                // Actions
                HStack {
                    Button("Refresh") {
                        loadMessages()
                    }
                    .buttonStyle(.bordered)

                    Button("Clear Data") {
                        clearMessages()
                    }
                    .buttonStyle(.bordered)
                    .tint(.red)
                }
                .padding()
            }
            .navigationTitle("WhatsApp Capture Test")
            .onAppear {
                loadMessages()
            }
        }
    }

    private var captureRateColor: Color {
        if captureRate >= 80 { return .green }
        if captureRate >= 60 { return .orange }
        return .red
    }

    private func loadMessages() {
        guard let groupURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.com.summarizer.prototype"
        ) else { return }

        let fileURL = groupURL.appendingPathComponent("captured_messages.json")

        if let data = try? Data(contentsOf: fileURL),
           let loaded = try? JSONDecoder().decode([CapturedMessage].self, from: data) {
            messages = loaded
        }
    }

    private func clearMessages() {
        guard let groupURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.com.summarizer.prototype"
        ) else { return }

        let fileURL = groupURL.appendingPathComponent("captured_messages.json")
        try? FileManager.default.removeItem(at: fileURL)
        messages = []
    }

    private func formatDate(_ timestamp: TimeInterval) -> String {
        let date = Date(timeIntervalSince1970: timestamp)
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .medium
        return formatter.string(from: date)
    }
}
```

### Step 6: Testing Protocol

**Week-long test plan:**

1. **Day 1: Setup**
   - Install prototype on your iPhone
   - Join 3-5 active WhatsApp groups
   - Enable notifications for prototype app

2. **Day 2-7: Passive Monitoring**
   - Use WhatsApp normally
   - DO NOT open prototype app (to avoid bias)
   - At end of each day: Record actual WhatsApp message count

3. **Day 7: Analysis**
   - Open prototype app
   - Compare captured count vs actual count
   - Calculate success rate: `(captured / actual) * 100`

**Success Criteria:**
- ✅ **≥80% capture rate** → Proceed with iOS development
- ⚠️ **60-79% capture rate** → Proceed with caution, warn users
- ❌ **<60% capture rate** → Do NOT proceed with iOS app

### Step 7: Export Results

```swift
// Add to ContentView
Button("Export Report") {
    exportReport()
}

private func exportReport() {
    let report = """
    WhatsApp Capture Test Report
    =============================

    Test Duration: [FILL IN]
    Actual WhatsApp Messages Received: [FILL IN]
    Messages Captured by Extension: \(messages.count)
    Success Rate: \(Int(captureRate))%

    Recommendation:
    \(captureRate >= 80 ? "✅ PROCEED with iOS development" :
      captureRate >= 60 ? "⚠️ PROCEED WITH CAUTION - expect missed messages" :
                          "❌ DO NOT PROCEED - too unreliable")

    Captured Messages:
    ------------------
    """

    // Create CSV for detailed analysis
    var csv = "Timestamp,Thread,Sender,Message\n"
    for msg in messages.sorted(by: { $0.timestamp < $1.timestamp }) {
        csv += "\(msg.timestamp),\"\(msg.threadName)\",\"\(msg.sender)\",\"\(msg.message)\"\n"
    }

    // Save to Files app
    let reportURL = FileManager.default.temporaryDirectory
        .appendingPathComponent("whatsapp_capture_report.txt")
    let csvURL = FileManager.default.temporaryDirectory
        .appendingPathComponent("whatsapp_captures.csv")

    try? report.write(to: reportURL, atomically: true, encoding: .utf8)
    try? csv.write(to: csvURL, atomically: true, encoding: .utf8)

    // Present share sheet
    let activityVC = UIActivityViewController(
        activityItems: [reportURL, csvURL],
        applicationActivities: nil
    )
    // Present activityVC...
}
```

---

## Kotlin Multiplatform Setup

### Prerequisites

- Android Studio Hedgehog (2024.1.1) or later
- Xcode 15+ (for iOS build)
- Kotlin 2.0+
- Gradle 8.5+

### Project Structure

```
summarizer/
├── shared/                          # Kotlin Multiplatform module
│   ├── src/
│   │   ├── commonMain/             # Shared code
│   │   │   ├── kotlin/
│   │   │   │   ├── domain/
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── Message.kt
│   │   │   │   │   │   ├── Thread.kt
│   │   │   │   │   │   └── Summary.kt
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── MessageRepository.kt
│   │   │   │   │   │   └── ThreadRepository.kt
│   │   │   │   │   └── usecase/
│   │   │   │   │       ├── GenerateSummaryUseCase.kt
│   │   │   │   │       └── AnalyzeImportanceUseCase.kt
│   │   │   │   └── data/
│   │   │   │       ├── repository/
│   │   │   │       │   ├── MessageRepositoryImpl.kt
│   │   │   │       │   └── ThreadRepositoryImpl.kt
│   │   │   │       └── api/
│   │   │   │           └── OpenAIClient.kt
│   │   │   └── sqldelight/
│   │   │       └── com/summarizer/db/
│   │   │           └── Database.sq
│   │   ├── androidMain/            # Android-specific
│   │   │   └── kotlin/
│   │   │       └── data/
│   │   │           └── database/
│   │   │               └── DatabaseDriver.kt
│   │   ├── iosMain/                # iOS-specific
│   │   │   └── kotlin/
│   │   │       └── data/
│   │   │           └── database/
│   │   │               └── DatabaseDriver.kt
│   │   └── commonTest/
│   └── build.gradle.kts
├── app/                            # Android app
├── iosApp/                         # iOS app (Xcode project)
└── settings.gradle.kts
```

### Step-by-Step Setup

#### 1. Create Shared Module

**settings.gradle.kts:**
```kotlin
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Summarizer"
include(":app")
include(":shared")  // Add shared module
```

#### 2. Configure Shared Module Build

**shared/build.gradle.kts:**
```kotlin
plugins {
    kotlin("multiplatform") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    id("com.android.library")
    id("app.cash.sqldelight") version "2.0.1"
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Kotlin Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

                // Kotlinx Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

                // SQLDelight (multiplatform database)
                implementation("app.cash.sqldelight:runtime:2.0.1")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")

                // Ktor (multiplatform HTTP client)
                implementation("io.ktor:ktor-client-core:2.3.8")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.8")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")
                implementation("io.ktor:ktor-client-logging:2.3.8")

                // DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:android-driver:2.0.1")
                implementation("io.ktor:ktor-client-okhttp:2.3.8")
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("app.cash.sqldelight:native-driver:2.0.1")
                implementation("io.ktor:ktor-client-darwin:2.3.8")
            }
        }

        val iosX64Main by getting {
            dependsOn(iosMain)
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
            }
        }
    }
}

android {
    namespace = "com.summarizer.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.summarizer.db")
        }
    }
}
```

#### 3. Define Shared Database Schema

**shared/src/commonMain/sqldelight/com/summarizer/db/Message.sq:**
```sql
import kotlin.Boolean;

CREATE TABLE MessageEntity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    threadId TEXT NOT NULL,
    threadName TEXT NOT NULL,
    sender TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    messageHash TEXT NOT NULL,
    messageType TEXT NOT NULL,
    isDeleted INTEGER AS Boolean NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL,
    UNIQUE(threadId, messageHash)
);

CREATE INDEX idx_message_thread ON MessageEntity(threadId);
CREATE INDEX idx_message_timestamp ON MessageEntity(threadId, timestamp);

selectAllByThread:
SELECT * FROM MessageEntity
WHERE threadId = ?
ORDER BY timestamp DESC;

selectUnreadByThread:
SELECT * FROM MessageEntity
WHERE threadId = ?
AND timestamp > ?
ORDER BY timestamp DESC;

insertMessage:
INSERT OR IGNORE INTO MessageEntity (
    threadId, threadName, sender, content, timestamp,
    messageHash, messageType, isDeleted, createdAt
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);

deleteOldMessages:
DELETE FROM MessageEntity
WHERE timestamp < ?
AND id NOT IN (
    SELECT id FROM MessageEntity
    WHERE threadId = ?
    ORDER BY timestamp DESC
    LIMIT 30
);
```

**shared/src/commonMain/sqldelight/com/summarizer/db/Thread.sq:**
```sql
CREATE TABLE ThreadEntity (
    threadId TEXT PRIMARY KEY,
    threadName TEXT NOT NULL,
    messageCount INTEGER NOT NULL DEFAULT 0,
    lastMessageTimestamp INTEGER NOT NULL,
    isFollowed INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL
);

selectAll:
SELECT * FROM ThreadEntity
ORDER BY lastMessageTimestamp DESC;

selectFollowed:
SELECT * FROM ThreadEntity
WHERE isFollowed = 1
ORDER BY lastMessageTimestamp DESC;

selectById:
SELECT * FROM ThreadEntity
WHERE threadId = ?;

upsertThread:
INSERT OR REPLACE INTO ThreadEntity (
    threadId, threadName, messageCount, lastMessageTimestamp, isFollowed, createdAt
) VALUES (?, ?, ?, ?, ?, ?);

updateStats:
UPDATE ThreadEntity
SET messageCount = ?, lastMessageTimestamp = ?
WHERE threadId = ?;
```

#### 4. Migrate Domain Models

**shared/src/commonMain/kotlin/domain/model/Message.kt:**
```kotlin
package com.summarizer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long = 0,
    val threadId: String,
    val threadName: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val messageHash: String,
    val messageType: MessageType = MessageType.TEXT,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    LOCATION,
    CONTACT,
    STICKER,
    DELETED,
    SYSTEM,
    UNKNOWN
}

fun Message.Companion.generateHash(
    threadId: String,
    sender: String,
    content: String,
    timestamp: Long
): String {
    return "$threadId:$sender:${content.take(50)}:${timestamp / 60000}"
        .hashCode()
        .toString()
}
```

**shared/src/commonMain/kotlin/domain/model/Thread.kt:**
```kotlin
package com.summarizer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Thread(
    val threadId: String,
    val threadName: String,
    val messageCount: Int = 0,
    val lastMessageTimestamp: Long = 0,
    val isFollowed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
```

**shared/src/commonMain/kotlin/domain/model/Summary.kt:**
```kotlin
package com.summarizer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Summary(
    val id: Long = 0,
    val threadId: String,
    val overview: String,
    val keyTopics: List<String> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val announcements: List<String> = emptyList(),
    val generatedAt: Long = System.currentTimeMillis()
)
```

#### 5. Create Repository Interfaces

**shared/src/commonMain/kotlin/domain/repository/MessageRepository.kt:**
```kotlin
package com.summarizer.domain.repository

import com.summarizer.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesFlow(threadId: String): Flow<List<Message>>
    suspend fun getMessages(threadId: String): List<Message>
    suspend fun getUnreadMessages(threadId: String, since: Long): List<Message>
    suspend fun saveMessage(message: Message)
    suspend fun deleteOldMessages(beforeTimestamp: Long, threadId: String)
}
```

#### 6. Implement Repository with SQLDelight

**shared/src/commonMain/kotlin/data/repository/MessageRepositoryImpl.kt:**
```kotlin
package com.summarizer.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.summarizer.db.AppDatabase
import com.summarizer.domain.model.Message
import com.summarizer.domain.model.MessageType
import com.summarizer.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepositoryImpl(
    private val database: AppDatabase
) : MessageRepository {
    private val queries = database.messageQueries

    override fun getMessagesFlow(threadId: String): Flow<List<Message>> {
        return queries.selectAllByThread(threadId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { entities ->
                entities.map { it.toDomainModel() }
            }
    }

    override suspend fun getMessages(threadId: String): List<Message> {
        return queries.selectAllByThread(threadId)
            .executeAsList()
            .map { it.toDomainModel() }
    }

    override suspend fun getUnreadMessages(threadId: String, since: Long): List<Message> {
        return queries.selectUnreadByThread(threadId, since)
            .executeAsList()
            .map { it.toDomainModel() }
    }

    override suspend fun saveMessage(message: Message) {
        queries.insertMessage(
            threadId = message.threadId,
            threadName = message.threadName,
            sender = message.sender,
            content = message.content,
            timestamp = message.timestamp,
            messageHash = message.messageHash,
            messageType = message.messageType.name,
            isDeleted = message.isDeleted,
            createdAt = message.createdAt
        )
    }

    override suspend fun deleteOldMessages(beforeTimestamp: Long, threadId: String) {
        queries.deleteOldMessages(beforeTimestamp, threadId)
    }
}

// Extension to convert DB entity to domain model
private fun com.summarizer.db.MessageEntity.toDomainModel() = Message(
    id = id,
    threadId = threadId,
    threadName = threadName,
    sender = sender,
    content = content,
    timestamp = timestamp,
    messageHash = messageHash,
    messageType = MessageType.valueOf(messageType),
    isDeleted = isDeleted,
    createdAt = createdAt
)
```

#### 7. Platform-Specific Database Drivers

**shared/src/androidMain/kotlin/data/database/DatabaseDriver.kt:**
```kotlin
package com.summarizer.data.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.summarizer.db.AppDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "summarizer.db"
        )
    }
}
```

**shared/src/iosMain/kotlin/data/database/DatabaseDriver.kt:**
```kotlin
package com.summarizer.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.summarizer.db.AppDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = AppDatabase.Schema,
            name = "summarizer.db"
        )
    }
}
```

**shared/src/commonMain/kotlin/data/database/DatabaseDriverFactory.kt:**
```kotlin
package com.summarizer.data.database

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

#### 8. Migrate Use Case

**shared/src/commonMain/kotlin/domain/usecase/GenerateSummaryUseCase.kt:**
```kotlin
package com.summarizer.domain.usecase

import com.summarizer.domain.model.Message
import com.summarizer.domain.model.Summary
import com.summarizer.domain.repository.MessageRepository
import com.summarizer.data.ai.AIEngine
import com.summarizer.data.ai.SummarizationMode

class GenerateSummaryUseCase(
    private val messageRepository: MessageRepository,
    private val aiEngine: AIEngine
) {
    suspend fun execute(
        threadId: String,
        mode: SummarizationMode = SummarizationMode.FULL
    ): Result<Summary> {
        return try {
            // Fetch messages based on mode
            val messages = when (mode) {
                SummarizationMode.FULL ->
                    messageRepository.getMessages(threadId)
                SummarizationMode.INCREMENTAL ->
                    messageRepository.getUnreadMessages(threadId, getLastSummarizedTimestamp(threadId))
            }

            if (messages.isEmpty()) {
                return Result.failure(Exception("No messages to summarize"))
            }

            // Build prompt
            val prompt = buildPrompt(messages)

            // Generate summary via AI
            val response = aiEngine.generate(
                prompt = prompt,
                systemPrompt = "You are a helpful assistant that summarizes WhatsApp conversations.",
                maxTokens = 512,
                temperature = 0.3f
            )

            // Parse response
            val summary = parseResponse(threadId, response)

            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPrompt(messages: List<Message>): String {
        val conversation = messages
            .filterNot { it.isDeleted }
            .joinToString("\n") { "${it.sender}: ${it.content}" }

        return """
            Summarize the following WhatsApp conversation.

            Conversation:
            $conversation

            Provide:
            1. OVERVIEW: Brief summary (2-3 sentences)
            2. TOPICS: Key topics discussed (bullet points)
            3. ACTIONS: Any action items or decisions (bullet points)
            4. ANNOUNCEMENTS: Important announcements or updates (bullet points)
        """.trimIndent()
    }

    private fun parseResponse(threadId: String, response: String): Summary {
        val overviewRegex = Regex("OVERVIEW:?\\s*([^\\n]+(?:\\n(?!TOPICS|ACTIONS|ANNOUNCEMENTS)[^\\n]+)*)", RegexOption.IGNORE_CASE)
        val topicsRegex = Regex("TOPICS:?\\s*([\\s\\S]*?)(?=ACTIONS:|ANNOUNCEMENTS:|$)", RegexOption.IGNORE_CASE)
        val actionsRegex = Regex("ACTIONS:?\\s*([\\s\\S]*?)(?=ANNOUNCEMENTS:|$)", RegexOption.IGNORE_CASE)
        val announcementsRegex = Regex("ANNOUNCEMENTS:?\\s*([\\s\\S]*?)$", RegexOption.IGNORE_CASE)

        val overview = overviewRegex.find(response)?.groupValues?.get(1)?.trim() ?: "No overview available"
        val topics = extractBulletPoints(topicsRegex.find(response)?.groupValues?.get(1))
        val actions = extractBulletPoints(actionsRegex.find(response)?.groupValues?.get(1))
        val announcements = extractBulletPoints(announcementsRegex.find(response)?.groupValues?.get(1))

        return Summary(
            threadId = threadId,
            overview = overview,
            keyTopics = topics,
            actionItems = actions,
            announcements = announcements
        )
    }

    private fun extractBulletPoints(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()

        return text.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.removePrefix("-").removePrefix("•").removePrefix("*").trim() }
            .filter { it.isNotBlank() }
    }

    private suspend fun getLastSummarizedTimestamp(threadId: String): Long {
        // TODO: Get from thread settings
        return 0
    }
}
```

#### 9. Use Shared Code in iOS

**iOS App - Swift:**
```swift
import SwiftUI
import Shared // KMP framework

class ThreadListViewModel: ObservableObject {
    @Published var threads: [Thread] = []

    private let repository: ThreadRepository
    private var cancellables: [Kotlinx_coroutines_coreJob] = []

    init() {
        let databaseDriver = DatabaseDriverFactory().createDriver()
        let database = AppDatabase(driver: databaseDriver)
        repository = ThreadRepositoryImpl(database: database)
    }

    func loadThreads() {
        // Collect Kotlin Flow in Swift
        let job = repository.getThreadsFlow().collect(
            collector: Collector { threads in
                DispatchQueue.main.async {
                    self.threads = threads
                }
            },
            onError: { error in
                print("Error loading threads: \(error)")
            }
        )
        cancellables.append(job)
    }

    deinit {
        cancellables.forEach { $0.cancel(cause: nil) }
    }
}

// Helper to collect Kotlin Flow
class Collector<T>: Kotlinx_coroutines_coreFlowCollector {
    let callback: (T) -> Void

    init(callback: @escaping (T) -> Void) {
        self.callback = callback
    }

    func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        callback(value as! T)
        completionHandler(nil)
    }
}
```

---

## Critical Component Migrations

### Authentication System

**Current Android (PIN + Biometric):**
- PIN hash stored in EncryptedSharedPreferences
- Biometric via BiometricPrompt
- SHA-256 hashing

**iOS Migration:**

```swift
import LocalAuthentication
import CryptoKit

class AuthenticationManager {
    private let keychain = KeychainService()

    // Store PIN hash securely
    func setPin(_ pin: String) {
        let hash = SHA256.hash(data: pin.data(using: .utf8)!)
        let hashString = hash.compactMap { String(format: "%02x", $0) }.joined()
        keychain.save(key: "pin_hash", value: hashString)
    }

    // Verify PIN
    func verifyPin(_ pin: String) -> Bool {
        let hash = SHA256.hash(data: pin.data(using: .utf8)!)
        let hashString = hash.compactMap { String(format: "%02x", $0) }.joined()
        return keychain.get(key: "pin_hash") == hashString
    }

    // Biometric authentication
    func authenticateWithBiometric(completion: @escaping (Bool, Error?) -> Void) {
        let context = LAContext()
        var error: NSError?

        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            completion(false, error)
            return
        }

        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "Authenticate to access app"
        ) { success, error in
            DispatchQueue.main.async {
                completion(success, error)
            }
        }
    }

    // Unified authentication flow
    func authenticate(completion: @escaping (Bool) -> Void) {
        // Try biometric first
        authenticateWithBiometric { [weak self] success, error in
            if success {
                completion(true)
            } else {
                // Fallback to PIN
                self?.promptForPin(completion: completion)
            }
        }
    }

    private func promptForPin(completion: @escaping (Bool) -> Void) {
        // Show PIN entry UI
        // This would be called from SwiftUI view
        completion(false)
    }
}

// Keychain Service
class KeychainService {
    func save(key: String, value: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecValueData as String: value.data(using: .utf8)!
        ]

        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }

    func get(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true
        ]

        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }
}
```

---

## Testing Strategy

### Unit Tests for Shared Code

**shared/src/commonTest/kotlin/domain/usecase/GenerateSummaryUseCaseTest.kt:**
```kotlin
package com.summarizer.domain.usecase

import com.summarizer.domain.model.Message
import com.summarizer.domain.repository.MessageRepository
import com.summarizer.data.ai.AIEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GenerateSummaryUseCaseTest {
    @Test
    fun testGenerateSummaryWithMessages() = runTest {
        val mockRepository = MockMessageRepository()
        val mockAI = MockAIEngine()
        val useCase = GenerateSummaryUseCase(mockRepository, mockAI)

        val result = useCase.execute("thread1")

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()!!
        assertTrue(summary.overview.isNotBlank())
    }
}

class MockMessageRepository : MessageRepository {
    override suspend fun getMessages(threadId: String) = listOf(
        Message(
            threadId = threadId,
            threadName = "Test Group",
            sender = "Alice",
            content = "Hello everyone",
            timestamp = 1000,
            messageHash = "hash1"
        )
    )
    // ... implement other methods
}

class MockAIEngine : AIEngine {
    override suspend fun generate(prompt: String, systemPrompt: String?, maxTokens: Int, temperature: Float) =
        """
        OVERVIEW: Test summary
        TOPICS:
        - Topic 1
        - Topic 2
        """.trimIndent()
    // ... implement other methods
}
```

### iOS UI Tests

**iosApp/iosAppUITests/ThreadListUITests.swift:**
```swift
import XCTest

class ThreadListUITests: XCTestCase {
    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    func testThreadListDisplaysThreads() throws {
        // Authenticate (if PIN screen appears)
        if app.staticTexts["Enter PIN"].exists {
            enterPin("1234")
        }

        // Verify thread list loads
        let threadList = app.tables.firstMatch
        XCTAssertTrue(threadList.waitForExistence(timeout: 5))

        // Verify at least one thread exists
        XCTAssertGreaterThan(threadList.cells.count, 0)
    }

    func testNavigateToThreadDetail() throws {
        let firstThread = app.tables.cells.firstMatch
        XCTAssertTrue(firstThread.waitForExistence(timeout: 5))

        firstThread.tap()

        // Verify navigation to detail screen
        XCTAssertTrue(app.navigationBars.buttons["Generate Summary"].waitForExistence(timeout: 3))
    }

    private func enterPin(_ pin: String) {
        for digit in pin {
            app.buttons[String(digit)].tap()
        }
    }
}
```

---

## Troubleshooting Common Issues

### Issue 1: KMP Framework Not Found in Xcode

**Error:** `framework 'Shared' not found`

**Solution:**
```bash
# In Android Studio terminal:
cd shared
./gradlew :shared:embedAndSignAppleFrameworkForXcode

# Or add Run Script Phase in Xcode:
# Build Phases → + → New Run Script Phase
cd "$SRCROOT/../shared"
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

### Issue 2: Kotlin Flow Not Collecting in Swift

**Problem:** Flow updates not triggering SwiftUI updates

**Solution:**
```swift
// Use Kotlin Flow collector helper
extension Kotlinx_coroutines_coreFlow {
    func collect(
        onEach: @escaping (T) -> Void,
        onCompletion: @escaping (Error?) -> Void
    ) -> Kotlinx_coroutines_coreJob {
        let collector = FlowCollector<T>(onEach: onEach)
        return self.collect(collector: collector) { error in
            onCompletion(error)
        }
    }
}

class FlowCollector<T>: Kotlinx_coroutines_coreFlowCollector {
    let onEach: (T) -> Void

    init(onEach: @escaping (T) -> Void) {
        self.onEach = onEach
    }

    func emit(value: Any?) async throws {
        if let value = value as? T {
            onEach(value)
        }
    }
}
```

### Issue 3: Background Task Not Running on iOS

**Problem:** BGTaskScheduler task never executes

**Debugging:**
```bash
# Use Xcode console to manually trigger
e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"com.summarizer.autosummarize"]

# Check if registered correctly
po [[BGTaskScheduler sharedScheduler] _registeredTasks]
```

**Common mistakes:**
- Forgot to add identifier to Info.plist `BGTaskSchedulerPermittedIdentifiers`
- Not calling `setTaskCompleted()` within 30 seconds
- Not rescheduling after completion

---

**Document Version:** 1.0
**Last Updated:** February 10, 2026
**Next Steps:** Build prototype → Evaluate results → Decide on full migration
