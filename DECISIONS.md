# Project Decisions Log
# WhatsApp Summarizer - I1 MVP

**Last Updated**: 2026-01-31
**Project Status**: Week 4 Complete (67% of I1)

---

## 📋 Decision Categories

1. [Architecture & Technology](#architecture--technology)
2. [Week 2 - Message Handling](#week-2---message-handling)
3. [Week 4 - Model Download](#week-4---model-download)
4. [Security & Privacy](#security--privacy)
5. [Performance & Optimization](#performance--optimization)

---

## Architecture & Technology

### AI Model Selection
**Decision**: TinyLlama 1.1B Q4_K_M (~700MB)
**Date**: 2026-01-31 (Week 0)
**Rationale**:
- Smaller than Phi-2 (700MB vs 1.8GB)
- Faster iteration during development
- Lower RAM requirements (4GB vs 6GB)
- Good enough for basic summarization
- Can upgrade to Phi-2 later if needed

**Alternatives Considered**:
- Phi-2 2.7B (rejected: too large for initial iteration)
- Gemma 2B (kept as option in model list)

---

### Minimum SDK Version
**Decision**: API 31 (Android 12)
**Date**: 2026-01-31 (Week 0)
**Rationale**:
- User requirement (explicit request)
- Simplifies notification listener permissions
- Reduces compatibility code
- Still covers ~70% of Android devices

**Alternatives Considered**:
- API 29 (Android 10) - rejected per user requirement
- API 33 (Android 13) - too restrictive

---

### Dependency Injection Framework
**Decision**: Hilt (over Koin)
**Date**: 2026-01-31 (Week 1)
**Rationale**:
- Official Google DI framework
- Better integration with Android components
- Compile-time validation
- Strong typing
- Better performance than reflection-based DI

**Alternatives Considered**:
- Koin (rejected: reflection-based, runtime errors)
- Manual DI (rejected: too much boilerplate)

---

### UI Framework
**Decision**: Jetpack Compose with Material 3
**Date**: 2026-01-31 (Week 1)
**Rationale**:
- Modern declarative UI
- Less boilerplate than XML
- Better state management
- Material 3 for latest design patterns
- Easier animations

**Alternatives Considered**:
- XML Views (rejected: outdated, more code)
- Flutter (rejected: not native Android)

---

### Database Encryption
**Decision**: SQLCipher with passphrase from Android ID
**Date**: 2026-01-31 (Week 1)
**Rationale**:
- Messages are sensitive personal data
- Encryption at rest is essential
- Android ID provides device-unique passphrase
- Transparent encryption (no code changes in DAOs)

**Known Issue**: SQLCipher 16KB page size warning (monitoring for updates)

---

### Preferences Storage
**Decision**: DataStore Preferences (over SharedPreferences)
**Date**: 2026-01-31 (Week 3)
**Rationale**:
- Type-safe API
- Coroutine-based (async by default)
- Safer than SharedPreferences
- Follows modern Android best practices
- Better error handling

**Alternatives Considered**:
- SharedPreferences (rejected: synchronous, not type-safe)
- EncryptedSharedPreferences only (rejected: for auth only)

---

## Week 2 - Message Handling

### Deleted Messages Display
**Decision**: Option C - Show deleted messages but exclude from summaries
**Date**: 2026-01-31 (Week 2)
**User Preference**: C (confirmed)
**Rationale**:
- Keep visible for context
- Users can see conversation flow
- Exclude from AI summaries (irrelevant content)
- Visual styling (red background, strikethrough)

**Alternatives Considered**:
- A: Keep visible and included in summaries (rejected: pollutes summaries)
- B: Hide completely (rejected: loses context)
- D: User preference toggle (rejected: adds complexity for I1)

---

### System Messages Display
**Decision**: Option C - Show system messages but exclude from summaries
**Date**: 2026-01-31 (Week 2)
**User Preference**: C (confirmed)
**Rationale**:
- Useful for context (who joined/left group)
- Not relevant for content summarization
- Visual styling (tertiary color container)

**Alternatives Considered**:
- A: Show and include in summaries (rejected: noise in summaries)
- B: Hide completely (rejected: loses group management context)
- D: Filter toggle (rejected: complexity)

---

### Media Message Display
**Decision**: Option A - Simple emoji indicators
**Date**: 2026-01-31 (Week 2)
**User Preference**: A (confirmed)
**Rationale**:
- Clean and simple for I1
- Low implementation complexity
- No additional storage for thumbnails
- Clear indication of message type

**Alternatives Considered**:
- B: Thumbnail placeholders (rejected: storage overhead)
- C: "View in WhatsApp" button (rejected: complexity)

---

### Database Migration Strategy
**Decision**: Option A for I1, Option C for production
**Date**: 2026-01-31 (Week 2)
**User Preference**: A→C (confirmed)
**Rationale**:
- Destructive migration acceptable for beta testing
- Simplifies development velocity
- Proper migrations before production release
- Beta testers will reinstall anyway

**Implementation**: Using `fallbackToDestructiveMigration()` for I1

---

### WhatsApp Business Testing Priority
**Decision**: Option B - Rely on user feedback during beta
**Date**: 2026-01-31 (Week 2)
**User Preference**: B (confirmed)
**Rationale**:
- Code already supports both packages
- Limited testing resources
- Beta testers will report if issues occur
- Can fix in Week 6 if needed

**Alternatives Considered**:
- A: Test in Week 3 (rejected: time constraint)
- C: Not a priority (kept as option)

---

### Message Parsing Error Recovery
**Decision**: Option A - Skip and log errors
**Date**: 2026-01-31 (Week 2)
**User Preference**: A (confirmed)
**Rationale**:
- Graceful degradation
- Doesn't block other messages
- Timber logs for debugging
- Can enhance later if users report issues

**Alternatives Considered**:
- B: Save raw data (rejected: storage overhead)
- C: Notify user (rejected: UX disruption)
- D: Fallback save unparsed (considered for later)

---

## Week 4 - Model Download

### Storage Location Selection
**Decision**: User picks storage location with visual space display
**Date**: 2026-01-31 (Week 4)
**User Preference**: User choice with visual feedback ✅
**Rationale**:
- Users may have storage constraints
- Internal storage often limited
- External storage may be larger
- User sees available space before choosing
- Intelligent default (location with more space)

**Implementation**:
- StorageLocationScreen with real-time space display
- Visual usage bars (color-coded by usage %)
- Warning if <2GB available
- Prevents download if insufficient space (with 10% buffer)

---

### Download Network Policy
**Decision**: WiFi-only by default with user override toggle
**Date**: 2026-01-31 (Week 4)
**User Preference**: WiFi-only with override ✅
**Rationale**:
- Models are large (700MB - 1.8GB)
- Prevents unexpected mobile data usage
- Users can override if needed
- Clear toggle in UI (WiFi chip)

**Implementation**:
- Default: `isWiFiOnlyDownload() = true`
- Toggle chip in ModelDownloadScreen header
- NetworkHelper for WiFi/mobile detection
- Clear error message if WiFi required

---

### Model Deletion Policy
**Decision**: Manual deletion only (user controls)
**Date**: 2026-01-31 (Week 4)
**User Preference**: Manual deletion ✅
**Rationale**:
- Users may want to keep multiple models
- No storage pressure in I1
- User has full control
- Can add auto-delete later if needed

**Alternatives Considered**:
- B: Auto-delete when switching (rejected: user may want both)
- C: Keep multiple models (future enhancement)

---

### Week 4 Implementation Scope
**Decision**: Option A - Full implementation (OkHttp, progress, pause/resume)
**Date**: 2026-01-31 (Week 4)
**User Preference**: A, defer C to Week 5 ✅
**Rationale**:
- Completeness over quick implementation
- Better UX with pause/resume
- Production-ready download system
- Can add background downloads (Option C) in Week 5 if time permits

**Implementation Features**:
- OkHttp with 30s timeouts
- HTTP Range headers for resume support
- Pause/resume/cancel operations
- MD5 checksum validation
- Progress tracking (throttled to 1% updates)
- StateFlow for reactive updates

---

### Pull-to-Refresh Implementation
**Decision**: Accompanist SwipeRefresh library
**Date**: 2026-01-31 (Week 3)
**Rationale**:
- Material3 PullToRefresh not yet available
- Accompanist is stable and well-maintained
- Easy to migrate later
- Works perfectly for I1

**Known Issue**: Deprecation warning (will migrate to Material3 when stable)

---

## Security & Privacy

### PIN Storage
**Decision**: SHA-256 hash with UUID salt in EncryptedSharedPreferences
**Date**: 2026-01-31 (Week 1)
**Rationale**:
- Never store plaintext PIN
- Unique salt per device
- EncryptedSharedPreferences for additional layer
- Industry-standard hashing

---

### Message Storage Encryption
**Decision**: SQLCipher with Android ID passphrase
**Date**: 2026-01-31 (Week 1)
**Rationale**:
- Messages contain sensitive personal data
- Encryption at rest essential for privacy
- Android ID provides device-unique key
- Transparent to application code

---

### Notification Permission Explanation
**Decision**: Dedicated PermissionExplanationScreen before permission request
**Date**: 2026-01-31 (Week 3)
**Rationale**:
- Better permission grant rates
- Clear privacy assurances
- Explains exactly what we do with notifications
- Emphasizes local-only processing

---

## Performance & Optimization

### Download Progress Throttling
**Decision**: Update UI every 1% progress (not every byte)
**Date**: 2026-01-31 (Week 4)
**Rationale**:
- Reduces UI overhead significantly
- Smooth progress bar updates
- Minimal latency impact
- Efficient StateFlow updates

---

### Storage Space Buffer
**Decision**: Require 10% more space than model size
**Date**: 2026-01-31 (Week 4)
**Rationale**:
- Prevents "out of space" during download
- Accounts for temporary files
- Ensures safe download completion
- Industry best practice

---

### Database Version Management
**Decision**: Destructive migration for I1
**Date**: 2026-01-31 (Week 2, 3, 4)
**Rationale**:
- Faster development iteration
- Acceptable for beta testing
- Will implement proper migrations before production
- Current versions: 1 → 2 → 3

---

### Model Download Resume Strategy
**Decision**: HTTP Range headers + temp file
**Date**: 2026-01-31 (Week 4)
**Rationale**:
- Large files (700MB-1.8GB) prone to interruption
- Resume from where it left off
- Temp file becomes final file on completion
- Atomic operation (rename)

---

## Future Considerations

### Items Deferred to Later Weeks

**Week 5** (if time permits):
- Background downloads with WorkManager
- Additional model download sources
- Model signature verification

**Week 6**:
- Proper database migrations
- Performance profiling and optimization
- Accessibility improvements
- Dark mode verification

**Post-I1**:
- Multiple model storage
- Model delta updates
- Bandwidth throttling options
- Download queue management

---

## Decision Making Process

### Criteria Used
1. **User Requirements**: Explicit user preferences take priority
2. **I1 Scope**: Simple, working features over complex, perfect ones
3. **Privacy First**: Always choose the more privacy-preserving option
4. **Development Velocity**: Fast iteration to meet 6-week timeline
5. **Future Flexibility**: Don't lock into irreversible decisions

### Stakeholders
- **Primary**: User (Mateusz)
- **Secondary**: Beta testers (5 parents)
- **Development**: Claude Code implementation

---

**Document Purpose**: Track all major decisions for future reference
**Revision Policy**: Update when significant decisions are made
**Review Schedule**: End of each week milestone
