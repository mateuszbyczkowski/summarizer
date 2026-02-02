# I1 Scope - Minimal MVP
# WhatsApp Thread Summarizer

## Overview

I1 is the minimal viable product focused on proving core value: **capture WhatsApp messages and summarize them on-demand**. This version is designed for beta testing with a small group of parents to validate the concept before building additional features.

**Timeline**: 6 weeks
**Target Platform**: Android 12+ (API 31+)
**AI Model**: Phi-2 Q4_K_M via Hugging Face
**AI Framework**: llama.cpp only

---

## I1 Core Features

### ✅ 1. Simple Onboarding (Week 1-2)
**Goal**: Get users up and running in < 3 minutes

**Flow**:
1. Welcome screen with app purpose
2. Request notification permission with clear explanation
3. Set 6-digit PIN (no biometric)
4. Download Phi-2 model from Hugging Face
   - Model: `TheBloke/Phi-2-GGUF` (Q4_K_M variant, ~1.6GB)
   - Show download progress
   - Validate download
5. Done - go to thread list

**What's Included**:
- PIN setup and validation
- Model download from Hugging Face
- Permission request flow
- Basic error handling

**What's Excluded**:
- Biometric authentication
- Multiple model selection
- Thread pre-selection
- Summary schedule configuration

---

### ✅ 2. Message Capture (Week 2-3)

**Goal**: Reliably capture WhatsApp group messages

**Features**:
- NotificationListenerService for WhatsApp
- Parse notification content:
  - Group name
  - Sender name
  - Message text
  - Timestamp
- Store in encrypted Room database
- Auto-create thread entries
- Handle text messages only (skip media for I1)

**What's Included**:
- Notification listener implementation
- Message parsing
- Database storage (encrypted with SQLCipher)
- Thread grouping

**What's Excluded**:
- WhatsApp Business support
- Image caption extraction
- Message deduplication (accept duplicates for I1)
- Edit/delete detection
- Message history import

---

### ✅ 3. Thread List (Week 3-4)

**Goal**: Simple view of all captured threads

**UI Components**:
- List of threads (groups)
- Each thread shows:
  - Group name
  - Message count
  - Last message timestamp
- Tap thread to view details
- Pull to refresh

**What's Included**:
- Basic thread list UI (Jetpack Compose)
- Sort by last message time (newest first)
- Empty state ("No messages captured yet")

**What's Excluded**:
- Thread prioritization
- Archive/mute functionality
- Filtering/sorting options
- Unread count
- Thread deletion
- Search

---

### ✅ 4. Thread Detail & Messages (Week 4)

**Goal**: View messages in a thread

**UI Components**:
- Thread name header
- List of messages:
  - Sender name
  - Message content
  - Timestamp
- "Summarize Now" button at top

**What's Included**:
- Message list view
- Chronological order (oldest first)
- Basic message display

**What's Excluded**:
- Message search
- Message filtering
- Export messages
- Delete individual messages

---

### ✅ 5. On-Demand Summarization (Week 4-5)

**Goal**: Generate AI summary when user requests

**Flow**:
1. User taps "Summarize Now" button
2. Loading indicator appears
3. AI processes messages (up to last 100 messages)
4. Summary displayed with:
   - Key topics (bullet list)
   - Action items with deadlines
   - Important announcements
   - Participant highlights (who said what)
5. Summary saved to database

**AI Implementation**:
- Framework: **llama.cpp** (via llama-cpp-android)
- Model: **Phi-2 Q4_K_M** (~1.6GB)
- Prompt template optimized for parent use case
- Timeout: 45 seconds max
- Parse JSON response from model

**What's Included**:
- llama.cpp integration
- Phi-2 model loading
- Summary generation pipeline
- Summary display UI (structured format)
- Loading state and error handling
- Save summary to database

**What's Excluded**:
- Daily auto-summarization
- Scheduled summaries
- Multiple AI frameworks
- Model switching
- Summary editing
- Summary export
- Summary quality rating

---

### ✅ 6. Basic Security (Week 1 & 6)

**Goal**: Protect user data with PIN

**Features**:
- 6-digit PIN setup
- PIN entry on app launch
- SHA-256 hashing with salt
- Store in EncryptedSharedPreferences
- Encrypted database (SQLCipher)
- Auto-lock when app goes to background

**What's Included**:
- PIN authentication
- Basic encryption (database + preferences)
- Lock screen

**What's Excluded**:
- Biometric authentication (fingerprint/face)
- PIN reset flow (if forgotten, must reinstall)
- Failed attempt limiting
- Auto-lock timer settings
- Screenshot prevention

---

## I1 User Flow

```
1. Install App
   ↓
2. Welcome Screen
   ↓
3. Grant Notification Permission
   ↓
4. Set 6-digit PIN
   ↓
5. Download Phi-2 Model (1.6GB)
   ↓
6. Thread List (empty initially)
   ↓
7. [WhatsApp messages start appearing]
   ↓
8. Tap a Thread
   ↓
9. View Messages
   ↓
10. Tap "Summarize Now"
    ↓
11. Wait ~10-30 seconds
    ↓
12. View Summary:
    • Key Topics
    • Action Items & Deadlines
    • Announcements
    • Participant Highlights
```

---

## Technical Stack for I1

### Core
- **Language**: Kotlin 1.9+
- **Min SDK**: Android 12 (API 31)
- **Target SDK**: Android 14 (API 34)
- **UI**: Jetpack Compose
- **Architecture**: MVVM (simplified)

### Libraries
- **DI**: Hilt
- **Database**: Room + SQLCipher
- **Coroutines**: Kotlin Coroutines + Flow
- **Security**: Jetpack Security (EncryptedSharedPreferences)
- **AI**: llama-cpp-android
- **HTTP**: OkHttp (for model download only)
- **JSON**: Kotlinx Serialization

### AI Model (I1 - Testing)
- **Model**: TinyLlama-1.1B Q4_K_M
- **Size**: ~700MB (easier for testing and iteration)
- **Source**: Hugging Face (`TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF`)
- **Format**: GGUF (for llama.cpp)
- **URL**: `https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf`
- **Note**: This is a smaller model for I1 to enable fast iteration. We'll upgrade to Phi-2 or better in I2.

---

## Simplified Architecture for I1

```
┌─────────────────────────────────────┐
│       UI Layer (Compose)            │
│  • Onboarding                       │
│  • Thread List                      │
│  • Thread Detail                    │
│  • Summary View                     │
│  • PIN Lock                         │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│       ViewModel Layer               │
│  • ThreadsViewModel                 │
│  • SummaryViewModel                 │
│  • OnboardingViewModel              │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│       Repository Layer              │
│  • MessageRepository                │
│  • SummaryRepository                │
│  • ModelRepository                  │
│  • AuthRepository                   │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│       Data Sources                  │
│  • Room Database (encrypted)        │
│  • Encrypted Preferences            │
│  • AI Engine (llama.cpp)            │
│  • NotificationListener Service     │
└─────────────────────────────────────┘
```

---

## Database Schema (Simplified for I1)

### Tables

**messages**
```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val threadId: String,
    val threadName: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)
```

**threads**
```kotlin
@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey
    val threadId: String,
    val threadName: String,
    val messageCount: Int = 0,
    val lastMessageTimestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)
```

**summaries**
```kotlin
@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val threadId: String,
    val keyTopics: List<String>,
    val actionItems: List<ActionItem>,
    val announcements: List<String>,
    val participantHighlights: List<ParticipantHighlight>,
    val messageCount: Int,
    val generatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ActionItem(
    val task: String,
    val deadline: String? = null,
    val mentionedBy: String? = null
)

@Serializable
data class ParticipantHighlight(
    val person: String,
    val message: String
)
```

---

## I1 Development Plan (6 Weeks)

### Week 1: Setup & Foundation ✅ COMPLETE
**Goal**: Project structure and database ready

**COMPLETED (2026-01-31):**
- [x] Create Android Studio project (Kotlin, Compose)
- [x] Set up Hilt DI
- [x] Configure Room database with SQLCipher
- [x] Define entities (Message, Thread, Summary)
- [x] Create DAOs and repositories (4 total: Message, Thread, Summary, Auth)
- [x] Complete PIN authentication system
  - [x] AuthRepository interface & implementation
  - [x] SecurePreferences with EncryptedSharedPreferences
  - [x] SHA-256 PIN hashing with UUID salt
  - [x] PinSetupScreen with confirmation
  - [x] PinLockScreen with auto-verify
  - [x] Navigation flow with auth
- [x] First successful build
- [x] App running on emulator
- [x] Permission UI (PermissionHelper + PermissionCard)
- [x] Message capture VERIFIED working
- [x] Thread list screen complete
- [x] Thread detail screen complete
- [x] Navigation fully functional

**Deliverable**: ✅ Fully functional app with working message capture and PIN auth

**Completion Date**: 2026-01-31 (Same day as start!)
**Files Created**: 37 Kotlin files
**Commits**: 16

---

### Week 2: Message Capture Refinement (CURRENT)
**Goal**: Refine and polish message capture

**COMPLETED (Week 1):**
- [x] Implement NotificationListenerService
- [x] Parse WhatsApp notifications (basic parsing)
- [x] Save messages to database
- [x] Auto-create threads
- [x] Test with real WhatsApp groups (VERIFIED WORKING)
- [x] Permission request UI implemented

**TODO (This Week):**
- [ ] Test on Android 12 physical device
- [ ] Refine message parsing (handle edge cases like deleted messages, edits)
- [ ] Add message deduplication logic
- [ ] Add comprehensive error handling and logging
- [ ] Test on multiple group formats and notification styles
- [ ] Handle WhatsApp notification format changes gracefully

**Deliverable**: Robust message capture with edge case handling

---

### Week 3: Basic UI & Thread List
**Goal**: Users can see captured threads

**COMPLETED (Week 1):**
- [x] Thread list screen (Compose) ✅
- [x] Empty state ✅
- [x] Navigation setup ✅
- [x] PIN lock screen ✅
- [x] PIN setup screen ✅
- [x] Permission card UI ✅

**TODO (This Week):**
- [ ] Onboarding flow (welcome screen, model download intro)
- [ ] Pull to refresh on thread list
- [ ] Loading states
- [ ] Polish animations and transitions

**Deliverable**: Polished UI with onboarding flow

---

### Week 4: Model Download
**Goal**: Download TinyLlama model from Hugging Face

**COMPLETED (Week 1):**
- [x] Thread detail screen ✅
- [x] Message list display ✅
- [x] "Summarize Now" button (placeholder) ✅

**TODO (This Week):**
- [ ] Model download functionality
  - Download TinyLlama 1.1B Q4_K_M from Hugging Face (~700MB)
  - Progress indicator with percentage
  - Checksum validation
  - Save to app storage
  - Handle resume on interruption
- [ ] Model download UI screen
- [ ] Handle download errors gracefully
- [ ] Verify model integrity

**Deliverable**: Can download and verify AI model

---

### Week 5: AI Integration
**Goal**: Generate summaries on-demand

- [ ] Integrate llama-cpp-android
- [ ] Load Phi-2 model
- [ ] Create prompt template
- [ ] Implement summary generation
  - Fetch messages (last 100)
  - Build prompt
  - Run inference
  - Parse JSON response
- [ ] Handle errors and timeouts
- [ ] Save summary to database
- [ ] Summary display UI

**Deliverable**: On-demand summarization working

---

### Week 6: Testing & Polish
**Goal**: Beta-ready app

- [ ] End-to-end testing
- [ ] Performance optimization
  - AI inference speed
  - UI responsiveness
  - Memory usage
- [ ] Error handling improvements
- [ ] UI polish (loading states, animations)
- [ ] Test on multiple devices
- [ ] Fix critical bugs
- [ ] Build signed APK for beta testing

**Deliverable**: Beta release for parent testers

---

## Testing Strategy for I1

### During Development
- Unit tests for repositories
- ViewModel tests with mock data
- Manual testing on emulator
- Manual testing on physical device (Android 12+)

### Beta Testing with Parents
**Week 7-8**: Private beta with 5-10 parents

**Feedback to Collect**:
1. Was onboarding clear and easy?
2. Did message capture work reliably?
3. Were summaries accurate and useful?
4. How long did summarization take?
5. Any crashes or errors?
6. Did you save time vs reading all messages?
7. What features are missing that you need?

**Success Criteria**:
- ✅ All parents complete onboarding
- ✅ Message capture works 90%+ of the time
- ✅ Summaries rated "useful" by 80%+ of testers
- ✅ No critical bugs
- ✅ Average summary time < 30 seconds

---

## What's Next After I1

Based on beta feedback, prioritize for **I2**:

### Likely I2 Features (4-6 weeks after I1)
1. **Daily auto-summarization**
2. **Smart notifications** for important messages
3. **Biometric authentication**
4. **Search** across summaries
5. **Thread prioritization**
6. **Better error recovery**
7. **Settings screen** (schedule, retention)

### Future Iterations
- Auto-cleanup (30-day retention)
- Multiple AI models
- Export summaries
- iOS version

---

## I1 Success Criteria

### Technical
- [ ] App launches < 2 seconds
- [ ] Message capture success rate > 90%
- [ ] Summary generation < 30 seconds (100 messages)
- [ ] Crash rate < 2%
- [ ] Works on Android 12, 13, 14

### User Experience
- [ ] Onboarding completion rate > 80%
- [ ] Beta testers rate summaries 4+/5 for usefulness
- [ ] All beta testers report time savings
- [ ] Clear feedback on what to build next

### Validation
- [ ] Prove core value proposition
- [ ] Understand parent pain points better
- [ ] Validate AI summary quality
- [ ] Get real usage data for planning I2

---

## Out of Scope for I1

**Explicitly NOT building in I1**:
- ❌ Biometric authentication
- ❌ Daily/scheduled auto-summarization
- ❌ Smart notifications
- ❌ Thread prioritization, archive, mute
- ❌ Search functionality
- ❌ Auto-cleanup (30-day retention)
- ❌ Multiple AI models/frameworks
- ❌ Settings screen (beyond PIN)
- ❌ Export/import
- ❌ WhatsApp Business
- ❌ Message deletion
- ❌ Summary editing
- ❌ Image caption extraction
- ❌ PIN reset
- ❌ Usage analytics

---

## Device Requirements for I1

### Minimum
- **OS**: Android 12 (API 31)
- **RAM**: 4GB
- **Storage**: 3GB free (for app + model)
- **Processor**: ARMv8 64-bit

### Recommended
- **OS**: Android 13+
- **RAM**: 6GB+
- **Storage**: 5GB free
- **Processor**: Snapdragon 7 series or better

### Testing Devices
- **Emulator**: Pixel 7 API 34 (Android Studio)
- **Physical**: Your own device(s)
- **Beta**: Parent testers' devices (real-world variety)

---

## Risk Mitigation for I1

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Model too large for some devices | Medium | High | Clear storage requirements, download validation |
| AI inference too slow | Medium | High | Use Phi-2 (optimized), set expectations, show progress |
| WhatsApp changes notifications | Low | High | Flexible parser, can update quickly for beta |
| Parents don't grant permission | Medium | High | Clear explanation of why needed, show value first |
| Summary quality poor | Medium | Critical | Prompt engineering, beta feedback, manual review |
| Storage fills up | Low | Medium | Start with no auto-capture limit, monitor in beta |

---

## I1 Launch Checklist

### Before Beta Testing
- [ ] All core features working
- [ ] No critical bugs
- [ ] Tested on Android 12, 13, 14
- [ ] Model downloads successfully
- [ ] Summarization works reliably
- [ ] PIN authentication secure
- [ ] Data encrypted
- [ ] Signed APK ready
- [ ] Privacy policy prepared (simple version)
- [ ] Beta tester instructions written

### Beta Testing Phase
- [ ] Recruit 5-10 parent testers
- [ ] Distribute APK
- [ ] Collect feedback (survey + interviews)
- [ ] Monitor crashes (Firebase Crashlytics optional)
- [ ] Weekly check-ins with testers
- [ ] Document all issues and feature requests

### Post-Beta Decision
- [ ] Review feedback
- [ ] Decide if I1 validates concept
- [ ] Plan I2 features based on feedback
- [ ] Determine if architecture needs changes

---

**Document Version**: 1.0
**Last Updated**: 2026-01-31
**Status**: Approved for Development
**Target Start**: Immediately
**Target Beta**: Week 7 (after 6 weeks dev)
