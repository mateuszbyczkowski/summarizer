# I2 Implementation Prompt for Claude Code

> **⚠️ STATUS: COMPLETED** ✅
>
> This iteration has been fully implemented and tested.
> See [I2_COMPLETION_SUMMARY.md](../I2_COMPLETION_SUMMARY.md) for details.
>
> **Completion Date**: February 10, 2026
> **Commit**: `a340f0c`

---

## Context

This is a WhatsApp Thread Summarizer Android app built with Kotlin, Jetpack Compose, and on-device AI.

**I1 (MVP) Status:** ✅ Complete
- Message capture from WhatsApp notifications
- Thread list and detail screens
- PIN authentication
- On-demand AI summarization (local LLM + OpenAI API)
- Model download system
- Database encryption (SQLCipher)

**I2 Status:** ✅ COMPLETE
- ✅ Smart Notifications (AI-powered importance filtering)
- ✅ Daily Auto-Summarization (scheduled background task)
- ✅ Data Retention & Cleanup (automatic old data removal)
- ✅ Search Functionality (summaries + messages)
- ✅ Biometric Authentication (fingerprint/face unlock)
- ✅ Better Error Recovery (retry logic with backoff)
- ✅ Settings Screen Enhancements (reordered by usage)
- ✅ Thread Sorting (newest + alphabetical)
- ✅ Thread follow/unfollow feature
  - Database schema updated with `isFollowed` field
  - Filter chip in ThreadListScreen
  - Star icons for follow/unfollow
  - Database migration 6→7 added

**Database Version:** 7
**Migration Pattern:** Established in `DatabaseModule.kt` - use this for all future schema changes

---

## I2 Planned Features (from docs/planning/I1_SCOPE.md)

Based on the original planning documents, I2 should include:

1. **Daily auto-summarization** - Scheduled automatic summaries at user-configured times
2. **Smart notifications** - AI-powered alerts only for important messages
3. **Biometric authentication** - Fingerprint/face unlock in addition to PIN
4. **Search functionality** - Search across summaries for specific information
5. **Thread prioritization** - High/normal/low priority levels for threads
6. **Better error recovery** - Enhanced error handling and resilience
7. **Settings screen** - Configuration for schedule, retention, and preferences

---

## Your Task

Implement the remaining I2 features one by one. Start by asking me which features to prioritize, then implement them autonomously following the established patterns.

---

## Phase 1: Initial Questions (ASK ME FIRST)

Before starting implementation, ask me:

1. **Feature Priority:** Which I2 features should I implement first?
   - Suggest: Daily auto-summarization, Settings screen, Search functionality
   - Ask: "Which 2-3 features would you like me to implement first?"

2. **Daily Auto-Summarization Details:**
   - When should summaries run? (default: 8 PM daily)
   - Should it only summarize followed threads?
   - Notification when summary is ready?

3. **Settings Screen Scope:**
   - What settings should be included initially?
   - Suggest: AI provider, Summary schedule, Data retention, PIN reset, About

4. **Search Functionality:**
   - Search summaries only, or both summaries and messages?
   - Real-time search or button-triggered?

---

## Phase 2: Autonomous Implementation

After getting answers, implement the selected features following these patterns:

### Pattern 1: Database Changes
```kotlin
// 1. Update entity with new fields
// 2. Increment database version in AppDatabase.kt
// 3. Add migration in DatabaseModule.kt:
private val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE ...")
    }
}
// 4. Add to builder: .addMigrations(MIGRATION_X_Y)
```

### Pattern 2: Repository Layer
```kotlin
// 1. Add methods to domain repository interface (domain/repository/)
// 2. Implement in data repository (data/repository/)
// 3. Add DAOs if needed (data/local/database/dao/)
```

### Pattern 3: UI Implementation
```kotlin
// 1. Create ViewModel with StateFlow
// 2. Create Composable screen with Material 3
// 3. Add navigation route to NavGraph.kt
// 4. Wire up with Hilt DI
```

### Pattern 4: Background Work (for auto-summarization)
```kotlin
// Use WorkManager with PeriodicWorkRequest
// 1. Create Worker class
// 2. Schedule work in repository or ViewModel
// 3. Handle notifications
```

### Implementation Steps:

**For Daily Auto-Summarization:**
1. Create `AutoSummarizationWorker` extending `CoroutineWorker`
2. Add WorkManager dependency if not present
3. Create repository method `scheduleDailySummary(hour: Int)`
4. Store schedule preference in `PreferencesRepository`
5. Create notification channel for summary completion
6. Add UI in Settings to configure time
7. Test with immediate work for verification

**For Settings Screen:**
1. Create `SettingsScreen.kt` and `SettingsViewModel.kt` in `ui/screens/settings/`
2. Add Material 3 cards for each setting category
3. Wire up to existing repositories (PreferencesRepository, AuthRepository)
4. Add navigation from ThreadListScreen (Settings icon already exists)
5. Include: AI Provider, Summary Schedule toggle, Data retention period, About section

**For Search Functionality:**
1. Add search methods to `SummaryRepository` and/or `MessageRepository`
2. Create `SearchScreen.kt` and `SearchViewModel.kt`
3. Use Material 3 SearchBar component
4. Add navigation route
5. Real-time filtering with debounce (500ms)
6. Show results grouped by thread

**For Thread Prioritization:**
1. Add `priority: ThreadPriority` enum (HIGH, NORMAL, LOW) to `Thread` entity
2. Database migration to add priority column (default NORMAL)
3. Add priority selector in ThreadDetailScreen (3 chips or dropdown)
4. Filter/sort options in ThreadListScreen
5. Visual distinction (colors, badges) for priority levels

---

## Phase 3: Guidelines

**Follow these principles:**

1. **Use existing patterns:** Study `ThreadListScreen`, `ThreadDetailScreen`, `GenerateSummaryUseCase` as references
2. **Material 3 UI:** Use Cards, Chips, TopAppBar, etc. from `androidx.compose.material3`
3. **Clean Architecture:** Domain → Data → UI separation
4. **Hilt DI:** All ViewModels use `@HiltViewModel`, repositories via constructor injection
5. **StateFlow:** Use `StateFlow` for UI state, not `LiveData`
6. **Error Handling:** Show user-friendly error messages, log with Timber
7. **Database migrations:** Always add migration, never use destructive migration
8. **Testing:** Build after each major change to verify compilation

**DO NOT:**
- Don't use deprecated APIs (check existing code for modern alternatives)
- Don't add features not in the approved I2 list without asking
- Don't skip database migrations
- Don't use LiveData (use StateFlow instead)
- Don't create new architecture patterns (follow existing)

---

## Phase 4: Progress Tracking

Use the TodoWrite tool to track your progress. Create todos like:
1. Implement daily auto-summarization
2. Create Settings screen
3. Add search functionality
4. Test end-to-end

Mark them as `in_progress` and `completed` as you work.

---

## Phase 5: Final Questions (ASK ME AT THE END)

After implementing the selected features, ask me:

1. **Testing Results:**
   - "I've completed [X, Y, Z features]. Build status: [SUCCESS/FAILED]"
   - "Would you like me to test these on a device now, or proceed with more features?"

2. **Next Steps:**
   - "Which remaining I2 features should I implement next?"
   - Options: [list remaining features]

3. **Documentation:**
   - "Should I create/update the following docs?"
     - I2_SCOPE.md (formal specification)
     - I2_COMPLETION.md (summary of changes)
     - Update PROGRESS.md

4. **Priority Changes:**
   - "Any bugs or issues you want me to address before continuing?"

---

## Quick Reference: File Locations

```
app/src/main/kotlin/com/summarizer/app/
├── data/
│   ├── local/
│   │   ├── dao/              # Room DAOs
│   │   ├── database/         # AppDatabase, Converters
│   │   └── entity/           # Room entities
│   └── repository/           # Repository implementations
├── domain/
│   ├── model/               # Domain models
│   └── repository/          # Repository interfaces
├── di/                      # Hilt modules
│   └── DatabaseModule.kt    # Database + migrations
└── ui/
    └── screens/
        ├── threads/         # Thread list & detail
        ├── summary/         # Summary display
        ├── settings/        # Settings (create this)
        └── auth/            # PIN screens
```

---

## Example Usage

**Copy this entire prompt into a new Claude Code session and say:**

"Please implement I2 features for the WhatsApp Summarizer app following this prompt."

**The AI will:**
1. Ask you the initial questions
2. Wait for your answers
3. Implement autonomously
4. Ask final questions when done

---

**Ready to start? Begin with Phase 1 questions!**
