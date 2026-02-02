# Product Requirements Document (PRD)
# WhatsApp Thread Summarizer for Parents

## 1. Product Overview

### 1.1 Product Name
**ThreadSummarizer** (working title)

### 1.2 Problem Statement
Parents with children in school and kindergarten are overwhelmed by numerous WhatsApp group messages. Important information about schedules, events, deadlines, and announcements gets buried in long conversation threads, making it difficult for busy parents to stay informed without spending significant time reading through messages.

### 1.3 Product Vision
An intelligent, privacy-focused mobile application that automatically captures, organizes, and summarizes WhatsApp group conversations, helping parents stay informed about their children's school activities without the burden of reading lengthy message threads.

### 1.4 Target Audience
- **Primary**: Parents with children in schools and kindergartens
- **Secondary**: Busy professionals managing multiple group conversations
- **Age Range**: 25-50 years old
- **Tech Savviness**: Moderate (able to grant app permissions and download AI models)

### 1.5 Success Metrics
- Daily Active Users (DAU) retention rate > 60%
- Time saved per user: Average 15-20 minutes daily
- Summary accuracy rating > 4.0/5.0 from users
- App completion rate for onboarding > 80%

---

## 2. Core Features

### 2.1 WhatsApp Message Capture
**Priority**: P0 (Must Have)

**Description**: Automatically capture WhatsApp messages from group conversations using Android's Notification Listener Service.

**Requirements**:
- Request and obtain NotificationListenerService permission
- Listen for WhatsApp notifications in real-time
- Extract message content, sender name, group name, and timestamp
- Handle various message types (text, images with captions, links)
- Support WhatsApp and WhatsApp Business
- Filter out personal/individual chats (focus on groups only, configurable)

**User Flow**:
1. User grants notification access permission during onboarding
2. App silently captures WhatsApp group messages in background
3. Messages are stored locally and processed for summarization

### 2.2 Local AI-Powered Summarization
**Priority**: P0 (Must Have)

**Description**: Summarize conversation threads using locally-running AI models, completely offline.

**Requirements**:
- Support multiple AI inference frameworks (llama.cpp, MediaPipe, MLC-LLM)
- Provide model marketplace with downloadable models:
  - Small models (< 1GB): Gemma-2B, Phi-2
  - Medium models (1-3GB): Gemma-7B quantized, Phi-3 Mini
- Model download with progress indicator
- Model management (view size, delete, switch active model)
- Summarization includes:
  - Key topics discussed
  - Action items and deadlines
  - Important announcements
  - Participant highlights (who said what important)
- Daily auto-summary at user-configurable time (default: 8 PM)
- On-demand summary generation
- Summary length optimization (concise but comprehensive)

**Technical Constraints**:
- All processing happens on-device
- No external API calls
- Efficient battery and memory usage
- Background processing with WorkManager

### 2.3 Secure Access Control
**Priority**: P0 (Must Have)

**Description**: Protect user privacy with PIN and biometric authentication.

**Requirements**:
- 6-digit PIN setup during onboarding
- PIN validation on app launch
- Optional biometric authentication (fingerprint/face)
- Encrypted local storage using Android Keystore
- Auto-lock after app goes to background
- PIN reset option with verification

**Security Considerations**:
- Use Android Jetpack Security for encryption
- Secure key storage in Android Keystore
- Biometric fallback to PIN
- Limit PIN attempts (lockout after 5 failed attempts)

### 2.4 Thread Management & Prioritization
**Priority**: P1 (Should Have)

**Description**: Organize conversations by thread and allow users to prioritize important groups.

**Requirements**:
- Automatic thread/group detection
- Thread list view showing:
  - Group name
  - Last message timestamp
  - Unread message count
  - Summary status (pending/generated)
- Priority levels: High, Normal, Low
- High-priority threads get:
  - More detailed summaries
  - Faster summarization
  - Prominent placement in UI
- Thread filtering and sorting options
- Archive/mute threads

### 2.5 Smart Notifications
**Priority**: P1 (Should Have)

**Description**: Intelligent notifications that alert users only for truly important messages.

**Requirements**:
- AI-powered importance detection
- Notification categories:
  - Critical (deadline, emergency)
  - Important (announcement, request)
  - Normal (general discussion)
- User-configurable notification preferences per thread priority
- Notification content preview with summary snippet
- Quick actions: View Summary, Mark as Read, Mute

### 2.6 Search & Discovery
**Priority**: P1 (Should Have)

**Description**: Search across all summaries to quickly find specific information.

**Requirements**:
- Full-text search across summaries
- Search filters:
  - Date range
  - Thread/group name
  - Keywords in action items
  - Participant names
- Search results highlighting
- Recent searches
- Search history

### 2.7 Data Management
**Priority**: P0 (Must Have)

**Description**: Manage local storage efficiently with automatic cleanup.

**Requirements**:
- Auto-delete original messages after 30 days
- Retain summaries indefinitely (or until manual deletion)
- Storage usage dashboard
- Manual cleanup options
- Data export (summaries only)
- Settings for retention period customization

---

## 3. Non-Functional Requirements

### 3.1 Performance
- App launch time: < 2 seconds
- Summary generation: < 30 seconds for 100 messages
- Model inference: < 5 seconds for typical thread
- Search results: < 1 second
- Background processing should not impact device performance

### 3.2 Privacy & Security
- **Zero cloud dependency**: All data stays on device
- **No network requests**: Fully offline operation
- **Data encryption**: At-rest encryption for all stored data
- **Permission minimalism**: Only essential permissions requested
- **Transparent data handling**: Clear privacy policy

### 3.3 Compatibility
- **Minimum Android Version**: Android 10.0 (API 29)
- **Target Android Version**: Android 14+ (API 34)
- **Device Requirements**:
  - Minimum 4GB RAM (6GB+ recommended for medium models)
  - Minimum 3GB free storage
  - ARMv8 or x86_64 processor

### 3.4 Accessibility
- Support for TalkBack screen reader
- Minimum touch target size: 48dp
- High contrast mode support
- Text scaling support
- Color blindness considerations

### 3.5 Usability
- Intuitive onboarding flow (< 3 minutes)
- Clear permission explanations
- Contextual help and tooltips
- Error messages with actionable guidance
- Offline-first design (no network error states)

---

## 4. User Experience

### 4.1 Onboarding Flow
1. Welcome screen with value proposition
2. Permission request: Notification Access (with explanation)
3. PIN setup (6 digits)
4. Biometric setup (optional)
5. AI model selection and download
6. Initial thread selection (which groups to monitor)
7. Summary schedule configuration
8. Onboarding complete

### 4.2 Main User Flows

#### Flow 1: Daily Summary Review
1. User opens app (PIN/biometric)
2. Home screen shows new summaries badge
3. User taps on thread with new summary
4. Summary displayed with structured sections
5. User can expand to see original messages
6. Mark as read or take action

#### Flow 2: On-Demand Summarization
1. User opens app
2. Navigate to thread list
3. Select thread
4. Tap "Summarize Now" button
5. Progress indicator while processing
6. Summary displayed

#### Flow 3: Search for Information
1. User opens app
2. Tap search icon
3. Enter keywords (e.g., "field trip")
4. Results show matching summaries
5. Tap result to view full summary

### 4.3 Information Architecture
```
App Root
├── Home (Summaries Feed)
│   ├── Today's Summaries
│   ├── Earlier Summaries
│   └── High Priority Threads
├── Threads
│   ├── All Threads
│   ├── High Priority
│   ├── Normal
│   └── Archived
├── Search
│   ├── Search Bar
│   └── Results List
└── Settings
    ├── Security (PIN, Biometric)
    ├── AI Model Management
    ├── Notification Preferences
    ├── Thread Priorities
    ├── Summary Schedule
    ├── Data & Storage
    └── About
```

---

## 5. Out of Scope (Phase 1)

The following features are explicitly out of scope for the initial release:

- iOS version (planned for Phase 2)
- Message sending/replying capabilities
- Integration with other messaging platforms (Telegram, Signal, etc.)
- Cloud backup/sync
- Multi-device support
- Group analytics and statistics
- Custom AI model training
- Voice summaries
- Widget support
- Wear OS companion app

---

## 6. Technical Constraints & Considerations

### 6.1 WhatsApp Limitations
- No official API access
- Reliance on notification content (may not capture all message details)
- Cannot access message history before app installation
- Deleted messages may still appear in notifications
- Media files (images, videos) content cannot be fully analyzed

### 6.2 AI Model Limitations
- On-device models less accurate than cloud-based LLMs
- Model size vs. accuracy trade-off
- Inference speed varies by device hardware
- Limited context window (may need chunking for very long threads)
- Language support depends on model (initially English-focused)

### 6.3 Android Platform Constraints
- NotificationListenerService can be disabled by user or system
- Background processing restrictions (Doze mode, battery optimization)
- Storage limitations on devices
- Variable hardware capabilities

---

## 7. Success Criteria

### 7.1 Launch Criteria (MVP)
- [ ] Successfully capture WhatsApp group messages
- [ ] Generate readable summaries with 80%+ accuracy
- [ ] PIN + biometric authentication working
- [ ] At least 2 AI models available for download
- [ ] Auto-summarization running daily
- [ ] Data retention (30-day auto-delete) functional
- [ ] Search across summaries working
- [ ] App passes security audit
- [ ] Performance benchmarks met on mid-range devices

### 7.2 User Acceptance Criteria
- Users can complete onboarding in < 5 minutes
- Summary accuracy rated 4.0+ stars
- Users report time savings (via survey)
- < 5% crash rate
- Positive app store rating (4.0+)

---

## 8. Risks & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|---------|-------------|------------|
| WhatsApp changes notification format | High | Medium | Build flexible parsing, monitor WhatsApp updates |
| AI models too large/slow for devices | High | Medium | Offer multiple model sizes, provide clear requirements |
| Users don't grant notification permission | High | Low | Clear onboarding explanation, value proposition |
| Battery drain concerns | Medium | Medium | Optimize background processing, battery usage monitoring |
| Storage fills up quickly | Medium | Low | Aggressive auto-cleanup, storage warnings |
| Summary quality insufficient | High | Medium | Model selection, prompt engineering, user feedback loop |
| Security/privacy concerns | High | Low | Transparent privacy policy, security audit, encryption |

---

## 9. Timeline & Phasing

### Phase 1: MVP (Weeks 1-12)
- Core message capture
- Basic summarization
- Security (PIN + biometric)
- Essential UI/UX
- 2-3 AI model options

### Phase 2: Enhancement (Weeks 13-20)
- Smart notifications
- Thread prioritization
- Search improvements
- Performance optimization
- Additional AI models

### Phase 3: Polish & Scale (Weeks 21-26)
- User feedback implementation
- Advanced features
- iOS planning
- Marketing preparation

---

## 10. Appendix

### 10.1 Glossary
- **Thread**: A conversation in a WhatsApp group
- **Summary**: AI-generated condensed version of a thread
- **Priority**: User-assigned importance level for a thread
- **Model**: AI language model for local inference
- **Inference**: Running AI model to generate summaries

### 10.2 References
- Android NotificationListenerService documentation
- llama.cpp mobile integration guides
- Android Jetpack Security documentation
- WhatsApp notification structure analysis

---

**Document Version**: 1.0
**Last Updated**: 2026-01-31
**Owner**: Product Team
**Status**: Draft for Review
