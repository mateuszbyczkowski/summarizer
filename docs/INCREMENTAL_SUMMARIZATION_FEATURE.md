# Incremental Summarization Feature

## Overview
This document describes the incremental summarization feature that allows users to configure how summaries are generated for each thread, with support for summarizing only new messages or all messages.

## Implementation Date
February 10, 2026

## Database Changes

### New Tables
- **thread_settings** (v8): Stores per-thread summarization configuration

### Schema
```sql
CREATE TABLE thread_settings (
    threadId TEXT PRIMARY KEY NOT NULL,
    summarizationMode TEXT NOT NULL DEFAULT 'INCREMENTAL',
    autoSummarizationEnabled INTEGER DEFAULT NULL,
    summaryScheduleHour INTEGER DEFAULT NULL,
    lastSummarizedMessageTimestamp INTEGER DEFAULT NULL,
    lastSummarizedAt INTEGER DEFAULT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    FOREIGN KEY (threadId) REFERENCES threads(threadId) ON DELETE CASCADE
)
```

### Migration
- Added MIGRATION_7_8 in [DatabaseModule.kt:33](app/src/main/kotlin/com/summarizer/app/di/DatabaseModule.kt#L33)
- Database version updated from 7 to 8

## New Domain Models

### SummarizationMode Enum
Location: [domain/model/SummarizationMode.kt](app/src/main/kotlin/com/summarizer/app/domain/model/SummarizationMode.kt)

```kotlin
enum class SummarizationMode {
    INCREMENTAL,  // Summarize only new messages since last summary
    FULL          // Summarize all messages in the thread
}
```

### ThreadSettings Data Class
Location: [domain/model/ThreadSettings.kt](app/src/main/kotlin/com/summarizer/app/domain/model/ThreadSettings.kt)

**Fields:**
- `threadId`: Unique identifier
- `summarizationMode`: INCREMENTAL or FULL (default: INCREMENTAL)
- `autoSummarizationEnabled`: Per-thread override for auto-summarization
- `summaryScheduleHour`: Custom schedule time (null = use global)
- `lastSummarizedMessageTimestamp`: Timestamp of most recent summarized message
- `lastSummarizedAt`: When the last summary was generated
- `createdAt` / `updatedAt`: Audit timestamps

## Repository Layer

### ThreadSettingsRepository
Location: [domain/repository/ThreadSettingsRepository.kt](app/src/main/kotlin/com/summarizer/app/domain/repository/ThreadSettingsRepository.kt)

**Key Methods:**
- `getSettings(threadId)`: Get settings for a thread
- `getSettingsFlow(threadId)`: Reactive Flow of settings
- `saveSettings(settings)`: Save or update settings
- `updateLastSummarized(threadId, messageTimestamp, summarizedAt)`: Update after summarization
- `updateSummarizationMode(threadId, mode)`: Change mode
- `getOrCreateSettings(threadId)`: Get existing or create with defaults

### MessageRepository Updates
Added method: `getMessagesForThreadSince(threadId, sinceTimestamp)` to fetch only new messages.

Location: [data/local/database/dao/MessageDao.kt:23](app/src/main/kotlin/com/summarizer/app/data/local/database/dao/MessageDao.kt#L23)

## Use Case Updates

### GenerateSummaryUseCase
Location: [domain/usecase/GenerateSummaryUseCase.kt:80](app/src/main/kotlin/com/summarizer/app/domain/usecase/GenerateSummaryUseCase.kt#L80)

**New Logic:**
1. Fetches or creates ThreadSettings for the thread
2. Checks the `summarizationMode`:
   - **INCREMENTAL**: Fetches messages since `lastSummarizedMessageTimestamp + 1`
   - **FULL**: Fetches all messages
3. If incremental mode finds no new messages, falls back to all messages
4. After successful summarization, updates `lastSummarizedMessageTimestamp` in settings

## UI Updates

### ThreadDetailScreen
Location: [ui/screens/threads/ThreadDetailScreen.kt](app/src/main/kotlin/com/summarizer/app/ui/screens/threads/ThreadDetailScreen.kt)

**New Component: Three-Dot Menu with Summarization Options**
- Added menu icon (⋮) next to the star icon in the header
- Opens DropdownMenu with mode selection:
  - **Incremental**: "New messages only" (with radio button)
  - **Full**: "All messages" (with radio button)
- Shows current selection with radio button indicator
- Closes automatically after selection
- Clean, unobtrusive UI that doesn't clutter the message list

### ThreadDetailViewModel
Location: [ui/screens/threads/ThreadDetailViewModel.kt](app/src/main/kotlin/com/summarizer/app/ui/screens/threads/ThreadDetailViewModel.kt)

**New Features:**
- `threadSettings: StateFlow<ThreadSettings?>`: Reactive settings state
- `updateSummarizationMode(mode)`: Updates the mode for the thread

## How It Works

### User Flow
1. User opens a thread in ThreadDetailScreen
2. User taps three-dot menu (⋮) in the header (next to star icon)
3. Menu opens showing:
   - Current mode with radio button selected
   - Alternative mode with unselected radio button
4. User selects desired mode
5. Menu closes and setting is immediately saved to database
6. Next summarization respects the chosen mode

### Incremental Summarization Flow
1. User triggers summary (manual or auto)
2. `GenerateSummaryUseCase` checks ThreadSettings
3. If mode is INCREMENTAL:
   - Fetches `lastSummarizedMessageTimestamp` from settings
   - Queries messages with timestamp > lastSummarized
   - Only new messages are sent to AI
4. After successful summary:
   - Updates `lastSummarizedMessageTimestamp` to latest message timestamp
   - Next incremental summary will start from this point

### Full Summarization Flow
1. If mode is FULL:
   - Fetches all messages in thread
   - Sends all messages to AI
   - Updates timestamp (for consistency)

## Benefits

✅ **Saves summaries**: All summaries are automatically persisted to database (already existed)
✅ **Incremental by default**: New mode summarizes only new messages, saving AI costs and time
✅ **Full mode option**: Users can choose to summarize everything when needed
✅ **Per-thread configuration**: Each thread has independent settings
✅ **No redundant processing**: Tracks what's been summarized via timestamp
✅ **Automatic fallback**: If no new messages, incremental mode falls back to all messages

## Future Enhancements

Potential improvements:
- Per-thread auto-summarization schedules
- UI indicator showing when last summarized
- Summary diff view (what changed since last summary)
- Batch mode selection (apply mode to multiple threads)

## Files Modified

### New Files (8)
1. `app/src/main/kotlin/com/summarizer/app/domain/model/SummarizationMode.kt`
2. `app/src/main/kotlin/com/summarizer/app/domain/model/ThreadSettings.kt`
3. `app/src/main/kotlin/com/summarizer/app/data/local/entity/ThreadSettingsEntity.kt`
4. `app/src/main/kotlin/com/summarizer/app/data/local/database/dao/ThreadSettingsDao.kt`
5. `app/src/main/kotlin/com/summarizer/app/domain/repository/ThreadSettingsRepository.kt`
6. `app/src/main/kotlin/com/summarizer/app/data/repository/ThreadSettingsRepositoryImpl.kt`

### Modified Files (7)
1. `app/src/main/kotlin/com/summarizer/app/data/local/database/AppDatabase.kt` - Added ThreadSettingsEntity, version 8
2. `app/src/main/kotlin/com/summarizer/app/di/DatabaseModule.kt` - Added MIGRATION_7_8, provider for ThreadSettingsDao
3. `app/src/main/kotlin/com/summarizer/app/di/RepositoryModule.kt` - Bound ThreadSettingsRepository
4. `app/src/main/kotlin/com/summarizer/app/domain/repository/MessageRepository.kt` - Added getMessagesForThreadSince
5. `app/src/main/kotlin/com/summarizer/app/data/local/database/dao/MessageDao.kt` - Added query for messages since timestamp
6. `app/src/main/kotlin/com/summarizer/app/data/repository/MessageRepositoryImpl.kt` - Implemented new method
7. `app/src/main/kotlin/com/summarizer/app/domain/usecase/GenerateSummaryUseCase.kt` - Added mode-based message fetching
8. `app/src/main/kotlin/com/summarizer/app/ui/screens/threads/ThreadDetailViewModel.kt` - Added settings state and update method
9. `app/src/main/kotlin/com/summarizer/app/ui/screens/threads/ThreadDetailScreen.kt` - Added SummarizationSettingsCard UI

## Build Status
✅ **Build successful** (assembleDebug completed with no errors)

## Testing Recommendations

1. **Test incremental mode**:
   - Set thread to INCREMENTAL mode
   - Generate a summary
   - Add new messages
   - Generate another summary
   - Verify only new messages are summarized

2. **Test full mode**:
   - Set thread to FULL mode
   - Generate summary with all messages
   - Verify all messages are included

3. **Test mode switching**:
   - Switch between modes multiple times
   - Verify setting persists across app restarts

4. **Test fallback**:
   - Use incremental mode with no new messages
   - Verify it falls back to recent messages

5. **Test migration**:
   - Upgrade from v7 to v8
   - Verify all existing threads work
   - Verify new settings table is created
