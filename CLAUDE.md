# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WhatsApp Thread Summarizer - Android app that captures WhatsApp messages via NotificationListenerService and generates AI summaries using either an on-device LLM (Gemma 4 on Google LiteRT-LM using the GPU, or LFM2.5 on Llamatik/llama.cpp using the CPU) or the OpenAI API. Kotlin + Jetpack Compose, MVVM + Clean Architecture, Android 12+ (API 31+).

## UI

Summary-first Material 3 design (dynamic color, edge-to-edge), English with a full Polish translation. Plan and rules: `docs/redesign/PLAN.md`; mockups: https://claude.ai/artifact/EDpVyy8fYJfXzX3btYUnvg.

- Bottom navigation: Chats (`ui/screens/home`, with a "This week" agenda card), Tasks (`ui/screens/tasks`), Settings (`ui/screens/settings`)
- `ui/screens/thread`: one screen per chat with Summary and Messages tabs, summary history, streaming generation card; route `thread/{threadId}?tab=&summaryId=` built only by `openThread` / `openSummary` in `ui/navigation/NavGraph.kt`
- `ui/screens/search`, `ui/screens/models` (AI engine: on-device model manager + OpenAI tab), `ui/screens/onboarding`
- `ui/widget`: Glance home-screen widget "Upcoming" (this week's dates and tasks; counts only while the app lock is on)
- **Strings:** every user-visible string in `res/values/strings_<feature>.xml` plus its `values-pl` translation (plurals need one/few/many/other). The app is in the system per-app language setting (`res/xml/locales_config.xml`, `localeFilters`)
- **Dates and times:** only through `util/DateTimeFormats` (`dateTimeFormats(context)`, `rememberDateTimeFormats()` in Compose); they follow the locale and the 12/24-hour setting. Never hard-code date patterns in the UI

## Onboarding

Three steps (welcome, notification access, engine choice with on-device preselected), then the OpenAI key if chosen, then an app PIN only when the phone has no screen lock. `data/download/ModelSetupManager.kt` downloads the model picked by RAM (`domain/model/ModelRecommendation.kt`) in the background with a progress card on Home, resumes when Wi-Fi connects, and steps aside when the user picks a model in the model manager.

## Build Commands

```bash
./gradlew assembleDebug    # Build debug APK
./gradlew installDebug     # Install on device
./gradlew clean && rm -rf app/build && ./gradlew assembleDebug  # Clean build (fixes Hilt/Room issues)
./gradlew testDebugUnitTest            # JVM unit tests (parsers, dates, prompts, agenda, onboarding, lock policy)
./gradlew connectedDebugAndroidTest    # Instrumented tests (migrations, DB key, PIN, parsers on ICU regex); EMULATOR ONLY
```

`connectedDebugAndroidTest` uninstalls the app afterwards; never run it on a phone with real data. On a phone, install the test APK and use `adb shell am instrument` (see `benchmark/LocalModelBenchmark.kt`, `LiteRtBenchmark.kt`). Benchmarks and `LiteRtEngineTest` use the debug sample chat (`data/debug/DebugSampleData.kt`), never user data.

## Architecture: Dual AI Provider System

The critical architectural pattern: **runtime-switchable AI providers** abstracted behind a single interface.

```
Use Cases → AIEngine (interface) ← AIEngineProvider (singleton)
                                        ↓
                                   User Settings
                                        ↓
              LiteRtEngine (.litertlm, GPU) | RealAIEngine (.gguf, CPU) | OpenAIEngine
```

**How it works:**
- `domain/ai/AIEngine.kt`: Single interface for all AI operations
- `data/ai/AIEngineProvider.kt`: Factory that reads user settings and returns correct implementation
- `di/AIModule.kt`: Provides active engine as singleton - switches at runtime when settings change
- `data/ai/LiteRtEngine.kt`: Google LiteRT-LM on the GPU for `.litertlm` models (default: Gemma 4 E2B). ~10-40x faster prompt reading than llama.cpp on a mid-range phone. Native calls on the single `litert-native` thread; a fresh conversation per summary
- `data/ai/RealAIEngine.kt`: Local Llamatik (llama.cpp wrapper) for `.gguf` models (CPU fallback: LFM2.5). LlamaBridge is one global, non-thread-safe context: every native call runs on the engine's single `llama-native` thread
- The local engine is chosen by the model file extension; if the GPU cannot load a model, `AIEngineProvider` falls back to a downloaded GGUF model, or after repeated failures has `ModelSetupManager.startCpuFallback()` download one (a user cancel is remembered)
- `domain/ai/ModelLoading.kt`: `ensureModelLoaded()` loads the active local model on demand (use cases never assume it is loaded)
- `domain/ai/ModelGate.kt`: one job at a time uses the model. Summaries queue behind each other (stage `WAITING`), loading, switching or deleting a model waits for the running summary, and smart-notification scoring skips a busy model. Any new code that loads, unloads or generates goes through it
- `data/ai/OpenAIEngine.kt`: Cloud API (Retrofit)

**To add new provider:**
1. Implement `AIEngine` in `data/ai/YourEngine.kt`
2. Add enum to `domain/model/AIProvider.kt`
3. Update `AIEngineProvider.getActiveEngine()` switch statement
4. Add UI in `ui/screens/models/` (AI engine screen) and the onboarding `EngineChoiceScreen`

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

`ui/lock/AppLockManager.kt` shows the lock screen above the navigation graph; every entry point (notifications, widget, search deep links) goes through it. On by default (setting in Privacy & security). It uses the phone's own lock (fingerprint, face or screen lock via `BiometricHelper`); the app's 6-digit PIN is used only when the phone has no screen lock (`ui/lock/AppLockPolicy.kt` decides, and asks for a PIN if the screen lock is removed later). An unlock stays valid until the app has been unused for 24 hours (persisted, survives process death); wrong PINs only cause short pauses. The owner wants the lock lenient - do not tighten it without asking.

While the lock is on, notifications show no summary or message content, the widget shows only counts, and the notification Copy action is blocked. Notifications always have a public version without content for the lock screen. `MainActivity` sets `FLAG_SECURE` (skipped only on emulator debug builds, for screenshots).

## Chat Description

Per chat, the user can write a short note on what the chat is about ("About this chat" in the thread menu, `ThreadSettings.description`, max 150 chars, one line). `PromptTemplate` puts it before the transcript so the model knows what matters.

## Message Capture Architecture

`service/WhatsAppNotificationListener.kt` turns each WhatsApp notification into a `NotificationSnapshot`; `service/WhatsAppNotificationParser.kt` (pure Kotlin, unit-tested) extracts messages from MessagingStyle with each message's own sender and timestamp. WhatsApp re-posts notifications with earlier messages, so deduplication relies on a SHA-256 of (thread, sender, content, timestamp); `MessageRepository.saveIncomingMessage` stores the message and updates thread stats in one transaction.

Thread IDs come from `util/ThreadNaming.threadIdFor(name)` - keep it stable, existing data depends on it. Handles both WhatsApp and WhatsApp Business by checking package names.

## Background Work

- `worker/AutoSummarizationWorker.kt`: Hourly self-rescheduling check; summarizes followed threads whose schedule (per-thread setting, else global, 8 PM default) passed since their last summary
- `worker/DataRetentionWorker.kt`: Deletes messages older than the retention period (30 days default), keeps summaries forever
- `worker/SummaryGenerationWorker.kt` + `domain/usecase/SummaryGenerationCoordinator.kt`: a summary the user asked for runs as a foreground job (unique work per thread) with progress stages, streamed overview, Cancel, and a "summary ready" notification (Show / Copy via `CopySummaryReceiver`)
- `ui/widget/UpcomingWidgetMidnightWorker.kt`: refreshes the widget after midnight while one is placed
- `worker/WorkScheduler.kt`: Coordinates scheduling (`refreshAutoSummarization()` after any related setting change)

All use `@HiltWorker` for dependency injection.

## Important Constraints

1. **Minimum API 31** - User requirement. Do not lower without approval.
2. **Hilt caching** - On compile errors: `./gradlew clean && rm -rf app/build`
3. **No cloud storage** - All data on-device only (privacy commitment)
4. **APK size ~135MB debug / ~66MB release** - llama.cpp (Llamatik) and LiteRT-LM native libraries; only `arm64-v8a` is packaged (`abiFilters`)
5. **Logs** - never log message or summary text (thread IDs are hashes). WorkManager logs and stores progress `Data`, so the streamed overview goes through `SummaryGenerationCoordinator.publishPartialText` (in memory), never `setProgress`
6. **Model catalog** - `data/download/ModelCatalog.DEFAULT_MODELS` (the app picks one by RAM in onboarding, `ModelRecommendation`); URLs pinned to a Hugging Face commit with a SHA-256 `checksum` (look them up from the phone with `LiteRtModelProbe#fileHashes`; Hugging Face is blocked on the corporate network)
7. **Regex on Android** - Android uses ICU regex, which rejects some patterns the JVM accepts (a class starting with `[:` is read as a POSIX class: write `[.:]`). JVM unit tests cannot catch this; parsers also run on the device in `ParsersOnDeviceTest`
8. **No animations during generation** - on-device GPU generation stalls the UI's own frames for seconds; anything that animates on its own while a summary runs (spinners, ticking timers, indeterminate bars) freezes the app into an ANR

## Debug Tooling (debug builds only)

- **Demo model** (`data/ai/DemoEngine.kt`, model id `debug-demo`): needs no download and streams a canned summary of the sample chat (`data/debug/DebugSampleData.kt`, "🧪 Test: Klasa 3B – rodzice"). Use it to validate generation, progress, cancel and notifications on the emulator.
- **Add a message from adb** (`debug/DebugMessageReceiver.kt`, guarded by the DUMP permission so only the shell can send it): `adb shell am broadcast -a com.summarizer.app.debug.ADD_MESSAGE -p com.summarizer.app.debug --es sender Anna --es text "..."` (optional `--es thread <name>`)

## Documentation

Extensive docs in `docs/` folder:
- `docs/development/START_DEVELOPING.md` - Setup guide
- `docs/planning/DECISIONS.md` - Architectural decisions with rationale
- `docs/planning/TECHNICAL_SPECIFICATION.md` - Full spec

Check existing docs before major architectural changes.
