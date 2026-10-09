# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WhatsApp Thread Summarizer - Android app that captures WhatsApp messages via NotificationListenerService and generates AI summaries using either local LLM (Llamatik/TinyLlama) or OpenAI API. Kotlin + Jetpack Compose, MVVM + Clean Architecture, Android 12+ (API 31+).

## Build Commands

```bash
./gradlew assembleDebug    # Build debug APK
./gradlew installDebug     # Install on device
./gradlew clean && rm -rf app/build && ./gradlew assembleDebug  # Clean build (fixes Hilt/Room issues)
```

## Architecture: Dual AI Provider System

The critical architectural pattern: **runtime-switchable AI providers** abstracted behind a single interface.

```
Use Cases → AIEngine (interface) ← AIEngineProvider (singleton)
                                        ↓
                                   User Settings
                                        ↓
                            RealAIEngine OR OpenAIEngine
```

**How it works:**
- `domain/ai/AIEngine.kt`: Single interface for all AI operations
- `data/ai/AIEngineProvider.kt`: Factory that reads user settings and returns correct implementation
- `di/AIModule.kt`: Provides active engine as singleton - switches at runtime when settings change
- `data/ai/RealAIEngine.kt`: Local Llamatik (llama.cpp wrapper)
- `data/ai/OpenAIEngine.kt`: Cloud API (Retrofit)

**To add new provider:**
1. Implement `AIEngine` in `data/ai/YourEngine.kt`
2. Add enum to `domain/model/AIProvider.kt`
3. Update `AIEngineProvider.getEngine()` switch statement
4. Add UI in `ui/screens/settings/SettingsScreen.kt`

**Critical:** Never call specific engines from use cases. Always use `AIEngine` interface. Provider switching happens via DI, no restart needed.

## Database Encryption

SQLCipher passphrase is derived from Android ID (device-unique, non-transferable). See `di/DatabaseModule.kt`:
```kotlin
val passphrase = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
```

**Implications:** 
- Database cannot be decrypted on different device
- Factory reset = permanent data loss
- SQLCipher 16KB page size warning is known and benign - do not fix

## Message Capture Architecture

`service/WhatsAppNotificationListener.kt` parses WhatsApp notifications to extract:
- Sender name
- Group/chat name  
- Message content
- Timestamp

Stores in Room DB automatically. Handles both WhatsApp and WhatsApp Business by checking package names. Thread grouping by chat name happens in the listener, not database.

## Background Work

- `worker/AutoSummarizationWorker.kt`: Scheduled daily (8 PM default) via WorkManager
- `worker/DataRetentionWorker.kt`: Deletes messages >30 days old, keeps summaries forever
- `worker/WorkScheduler.kt`: Coordinates scheduling

All use `@HiltWorker` for dependency injection.

## Important Constraints

1. **Minimum API 31** - User requirement. Do not lower without approval.
2. **Hilt caching** - On compile errors: `./gradlew clean && rm -rf app/build`
3. **No cloud storage** - All data on-device only (privacy commitment)
4. **APK size ~89MB** - Llamatik includes llama.cpp ARM64 binaries (normal)

## Documentation

Extensive docs in `docs/` folder:
- `docs/development/START_DEVELOPING.md` - Setup guide
- `docs/planning/DECISIONS.md` - Architectural decisions with rationale
- `docs/planning/TECHNICAL_SPECIFICATION.md` - Full spec

Check existing docs before major architectural changes.
