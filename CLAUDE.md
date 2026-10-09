# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WhatsApp Thread Summarizer - Android app that captures WhatsApp messages via NotificationListenerService and generates AI summaries using either a local LLM (Llamatik/llama.cpp with Qwen3.5, LFM2.5 or Gemma 3n GGUFs) or the OpenAI API. Kotlin + Jetpack Compose, MVVM + Clean Architecture, Android 12+ (API 31+).

## Build Commands

```bash
./gradlew assembleDebug    # Build debug APK
./gradlew installDebug     # Install on device
./gradlew clean && rm -rf app/build && ./gradlew assembleDebug  # Clean build (fixes Hilt/Room issues)
./gradlew testDebugUnitTest            # JVM unit tests (parser, prompts, retry)
./gradlew connectedDebugAndroidTest    # Instrumented tests (migrations, DB key, PIN); needs emulator
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
- `data/ai/RealAIEngine.kt`: Local Llamatik (llama.cpp wrapper). LlamaBridge is one global, non-thread-safe context: every native call runs on the engine's single `llama-native` thread
- `domain/ai/ModelLoading.kt`: `ensureModelLoaded()` loads the active local model on demand (use cases never assume it is loaded)
- `data/ai/OpenAIEngine.kt`: Cloud API (Retrofit)

**To add new provider:**
1. Implement `AIEngine` in `data/ai/YourEngine.kt`
2. Add enum to `domain/model/AIProvider.kt`
3. Update `AIEngineProvider.getActiveEngine()` switch statement
4. Add UI in `ui/screens/settings/`

**Critical:** Never call specific engines from use cases. Always use `AIEngine` interface. Provider switching happens via DI, no restart needed.

## Database Encryption

SQLCipher passphrase is 32 random bytes wrapped by a non-exportable Android Keystore key (`data/local/database/DatabaseKeyProvider.kt`). Databases from older versions (keyed with ANDROID_ID) are re-keyed on first launch; an unreadable database is moved aside (`*.unreadable-<ts>`) instead of crashing.

**Implications:**
- Database cannot be decrypted on a different device or from a copied file
- Factory reset or Keystore loss = permanent data loss
- Never add `fallbackToDestructiveMigration()`; every schema change needs a migration in `data/local/database/Migrations.kt` and a `MigrationTest` case
- SQLCipher 16KB page size warning is known and benign - do not fix

Secrets (PIN hash, OpenAI API key, lock state) live in `SecurePreferences`, also Keystore-encrypted.

## App Lock

`ui/lock/AppLockManager.kt` shows the PIN screen above the navigation graph. An unlock stays valid until the app has been unused for 24 hours (persisted, survives process death); wrong PINs only cause short pauses. The owner wants the lock lenient - do not tighten it without asking. `MainActivity` sets `FLAG_SECURE`.

## Message Capture Architecture

`service/WhatsAppNotificationListener.kt` turns each WhatsApp notification into a `NotificationSnapshot`; `service/WhatsAppNotificationParser.kt` (pure Kotlin, unit-tested) extracts messages from MessagingStyle with each message's own sender and timestamp. WhatsApp re-posts notifications with earlier messages, so deduplication relies on a SHA-256 of (thread, sender, content, timestamp); `MessageRepository.saveIncomingMessage` stores the message and updates thread stats in one transaction.

Thread IDs come from `util/ThreadNaming.threadIdFor(name)` - keep it stable, existing data depends on it. Handles both WhatsApp and WhatsApp Business by checking package names.

## Background Work

- `worker/AutoSummarizationWorker.kt`: Hourly self-rescheduling check; summarizes followed threads whose schedule (per-thread setting, else global, 8 PM default) passed since their last summary
- `worker/DataRetentionWorker.kt`: Deletes messages older than the retention period (30 days default), keeps summaries forever
- `worker/WorkScheduler.kt`: Coordinates scheduling (`refreshAutoSummarization()` after any related setting change)

All use `@HiltWorker` for dependency injection.

## Important Constraints

1. **Minimum API 31** - User requirement. Do not lower without approval.
2. **Hilt caching** - On compile errors: `./gradlew clean && rm -rf app/build`
3. **No cloud storage** - All data on-device only (privacy commitment)
4. **APK size ~110MB debug / ~45MB release** - Llamatik includes llama.cpp; only `arm64-v8a` is packaged (`abiFilters`)
5. **Model catalog** - `ModelDownloadViewModel.DEFAULT_MODELS`; set `checksum` (SHA-256 from Hugging Face) to have downloads verified

## Documentation

Extensive docs in `docs/` folder:
- `docs/development/START_DEVELOPING.md` - Setup guide
- `docs/planning/DECISIONS.md` - Architectural decisions with rationale
- `docs/planning/TECHNICAL_SPECIFICATION.md` - Full spec

Check existing docs before major architectural changes.
