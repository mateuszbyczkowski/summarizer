# Requirements Document
# WhatsApp Thread Summarizer - Detailed Requirements

## 1. Functional Requirements

### 1.1 Message Capture Requirements

**FR-1.1.1**: The system SHALL capture WhatsApp group message notifications in real-time.

**FR-1.1.2**: The system SHALL support both WhatsApp and WhatsApp Business applications.

**FR-1.1.3**: The system SHALL extract the following information from notifications:
- Sender name
- Message content (text)
- Group/thread name
- Timestamp
- Message type (text, image with caption, link, etc.)

**FR-1.1.4**: The system SHALL NOT capture individual/personal chat messages by default.

**FR-1.1.5**: The system SHALL allow users to select which groups to monitor during onboarding.

**FR-1.1.6**: The system SHALL handle message deduplication to avoid storing the same message multiple times.

**FR-1.1.7**: The system SHALL continue capturing messages when the app is in the background.

**FR-1.1.8**: The system SHALL gracefully handle cases where notification content is truncated.

### 1.2 AI Summarization Requirements

**FR-2.1**: The system SHALL generate summaries containing:
- Key topics discussed (bullet points)
- Action items with deadlines (structured list)
- Important announcements (highlighted)
- Participant highlights (who said what important)

**FR-2.2**: The system SHALL support multiple AI models:
- Small models (< 1GB): Gemma-2B, Phi-2
- Medium models (1-3GB): Gemma-7B quantized, Phi-3 Mini

**FR-2.3**: The system SHALL support multiple AI inference frameworks:
- llama.cpp
- MediaPipe LLM Inference
- MLC-LLM

**FR-2.4**: The system SHALL allow users to download AI models within the app.

**FR-2.5**: The system SHALL display download progress during model downloads.

**FR-2.6**: The system SHALL verify model integrity after download (checksum validation).

**FR-2.7**: The system SHALL allow users to switch between downloaded models.

**FR-2.8**: The system SHALL allow users to delete downloaded models to free up storage.

**FR-2.9**: The system SHALL generate summaries automatically at a user-configured time daily.

**FR-2.10**: The system SHALL allow users to generate summaries on-demand for any thread.

**FR-2.11**: The system SHALL complete summary generation within 30 seconds for a thread with up to 100 messages.

**FR-2.12**: The system SHALL handle very long threads by chunking messages if they exceed the model's context window.

**FR-2.13**: The system SHALL provide fallback behavior if AI generation fails (error message, retry option).

**FR-2.14**: The system SHALL NOT send any data to external servers during summarization.

### 1.3 Security Requirements

**FR-3.1**: The system SHALL require a 6-digit numeric PIN during first launch.

**FR-3.2**: The system SHALL hash and salt the PIN before storage using SHA-256.

**FR-3.3**: The system SHALL support biometric authentication (fingerprint/face) as an alternative to PIN.

**FR-3.4**: The system SHALL allow biometric authentication with PIN as fallback.

**FR-3.5**: The system SHALL lock after 5 failed PIN attempts for 60 seconds.

**FR-3.6**: The system SHALL automatically lock when the app goes to background.

**FR-3.7**: The system SHALL encrypt all data at rest using Android Jetpack Security.

**FR-3.8**: The system SHALL use Android Keystore for secure key management.

**FR-3.9**: The system SHALL provide a PIN reset option (with data loss warning).

**FR-3.10**: The system SHALL prevent screenshots in security-sensitive screens (optional setting).

### 1.4 Thread Management Requirements

**FR-4.1**: The system SHALL automatically create thread entries for new WhatsApp groups.

**FR-4.2**: The system SHALL allow users to assign priority levels to threads:
- High priority
- Normal priority
- Low priority

**FR-4.3**: The system SHALL allow users to archive threads.

**FR-4.4**: The system SHALL allow users to mute threads (disable notifications).

**FR-4.5**: The system SHALL display unread message count for each thread.

**FR-4.6**: The system SHALL allow users to sort threads by:
- Last message time
- Thread name
- Priority
- Unread count

**FR-4.7**: The system SHALL allow users to filter threads by priority level.

**FR-4.8**: The system SHALL allow users to delete threads with confirmation.

### 1.5 Smart Notifications Requirements

**FR-5.1**: The system SHALL analyze message importance using AI.

**FR-5.2**: The system SHALL classify messages into importance levels:
- Critical (deadline, emergency)
- Important (announcement, action item)
- Normal (general discussion)

**FR-5.3**: The system SHALL send notifications only for important/critical messages from high-priority threads (configurable).

**FR-5.4**: The system SHALL include a summary snippet in notifications.

**FR-5.5**: The system SHALL allow users to configure notification preferences per thread priority.

**FR-5.6**: The system SHALL provide quick actions in notifications:
- View summary
- Mark as read
- Mute thread

**FR-5.7**: The system SHALL group multiple notifications from the same thread.

### 1.6 Search Requirements

**FR-6.1**: The system SHALL provide full-text search across all summaries.

**FR-6.2**: The system SHALL highlight search terms in results.

**FR-6.3**: The system SHALL allow filtering search results by:
- Date range
- Thread name
- Priority level

**FR-6.4**: The system SHALL return search results within 1 second for typical queries.

**FR-6.5**: The system SHALL maintain search history (last 10 searches).

**FR-6.6**: The system SHALL allow users to clear search history.

### 1.7 Data Management Requirements

**FR-7.1**: The system SHALL automatically delete original messages older than 30 days.

**FR-7.2**: The system SHALL retain summaries indefinitely (until manual deletion).

**FR-7.3**: The system SHALL allow users to customize the retention period (7, 14, 30, 60, 90 days, or never).

**FR-7.4**: The system SHALL display storage usage statistics.

**FR-7.5**: The system SHALL warn users when storage usage exceeds 80% of allocated space.

**FR-7.6**: The system SHALL allow users to manually delete individual summaries.

**FR-7.7**: The system SHALL allow users to manually delete all messages for a specific thread.

**FR-7.8**: The system SHALL provide a "clear all data" option with confirmation.

### 1.8 Onboarding Requirements

**FR-8.1**: The system SHALL guide users through initial setup within 5 minutes.

**FR-8.2**: The onboarding flow SHALL include:
1. Welcome screen with app value proposition
2. Notification permission request with explanation
3. PIN setup (6 digits, confirmed)
4. Biometric setup (optional)
5. AI model selection and download
6. Thread selection (which groups to monitor)
7. Summary schedule configuration
8. Completion screen

**FR-8.3**: The system SHALL explain why each permission is needed before requesting it.

**FR-8.4**: The system SHALL allow users to skip optional steps (biometric, thread selection).

**FR-8.5**: The system SHALL save progress during onboarding (resume if interrupted).

---

## 2. Non-Functional Requirements

### 2.1 Performance Requirements

**NFR-1.1**: The app SHALL launch within 2 seconds on devices with 4GB+ RAM.

**NFR-1.2**: The app SHALL generate summaries within 30 seconds for threads with up to 100 messages.

**NFR-1.3**: AI inference SHALL complete within 5 seconds for a typical summary on mid-range devices.

**NFR-1.4**: Search results SHALL appear within 1 second for typical queries.

**NFR-1.5**: The UI SHALL remain responsive (60 FPS) during normal operation.

**NFR-1.6**: Database queries SHALL complete within 100ms for typical operations.

**NFR-1.7**: Background processing SHALL NOT noticeably impact device performance.

**NFR-1.8**: The app SHALL consume less than 5% battery during 24 hours of background operation.

### 2.2 Reliability Requirements

**NFR-2.1**: The app crash rate SHALL be less than 1% of sessions.

**NFR-2.2**: The ANR (Application Not Responding) rate SHALL be less than 0.5%.

**NFR-2.3**: The app SHALL recover gracefully from errors without data loss.

**NFR-2.4**: The app SHALL handle network unavailability during model downloads (resume capability).

**NFR-2.5**: The app SHALL handle insufficient storage gracefully with clear error messages.

**NFR-2.6**: The app SHALL handle device reboots without losing scheduled tasks.

### 2.3 Scalability Requirements

**NFR-3.1**: The app SHALL support monitoring up to 50 WhatsApp groups simultaneously.

**NFR-3.2**: The app SHALL handle threads with up to 1000 messages efficiently.

**NFR-3.3**: The database SHALL efficiently store up to 10,000 summaries.

**NFR-3.4**: The app SHALL function properly with up to 5GB of stored data.

### 2.4 Usability Requirements

**NFR-4.1**: Users SHALL be able to complete onboarding without external help.

**NFR-4.2**: The app SHALL follow Material Design 3 guidelines.

**NFR-4.3**: All touch targets SHALL be at least 48dp in size.

**NFR-4.4**: The app SHALL support dynamic font sizing (accessibility).

**NFR-4.5**: The app SHALL support TalkBack screen reader.

**NFR-4.6**: The app SHALL provide meaningful error messages with actionable guidance.

**NFR-4.7**: The app SHALL support dark mode based on system settings.

**NFR-4.8**: The app SHALL use clear, non-technical language in all user-facing text.

### 2.5 Security Requirements

**NFR-5.1**: All sensitive data SHALL be encrypted at rest using AES-256.

**NFR-5.2**: The app SHALL use Android Keystore for cryptographic key storage.

**NFR-5.3**: The app SHALL NOT log sensitive information in production builds.

**NFR-5.4**: The app SHALL be obfuscated using R8 in release builds.

**NFR-5.5**: The app SHALL validate all user inputs to prevent injection attacks.

**NFR-5.6**: The app SHALL use certificate pinning for model downloads (HTTPS).

**NFR-5.7**: The app SHALL NOT contain hardcoded secrets or API keys.

**NFR-5.8**: The app SHALL pass OWASP Mobile Security Testing Guide (MSTG) requirements.

### 2.6 Privacy Requirements

**NFR-6.1**: The app SHALL NOT transmit any user data to external servers.

**NFR-6.2**: The app SHALL NOT include any analytics that track user behavior.

**NFR-6.3**: The app SHALL operate fully offline (except for model downloads).

**NFR-6.4**: The app SHALL include a clear, readable privacy policy.

**NFR-6.5**: The app SHALL request only essential permissions (notification access, storage).

**NFR-6.6**: The app SHALL explain data handling practices during onboarding.

**NFR-6.7**: The app SHALL allow users to delete all their data at any time.

### 2.7 Compatibility Requirements

**NFR-7.1**: The app SHALL support Android 10 (API 29) and above.

**NFR-7.2**: The app SHALL target Android 14 (API 34).

**NFR-7.3**: The app SHALL function on devices with at least 4GB RAM.

**NFR-7.4**: The app SHALL require at least 3GB free storage (including AI model).

**NFR-7.5**: The app SHALL support both ARMv8 and x86_64 architectures.

**NFR-7.6**: The app SHALL handle different screen sizes (phones, tablets).

**NFR-7.7**: The app SHALL support screen orientations (portrait, landscape).

**NFR-7.8**: The app SHALL work on devices with and without biometric hardware.

### 2.8 Maintainability Requirements

**NFR-8.1**: The code SHALL achieve at least 80% test coverage.

**NFR-8.2**: The code SHALL follow Kotlin coding conventions.

**NFR-8.3**: The code SHALL be documented with KDoc for public APIs.

**NFR-8.4**: The architecture SHALL follow Clean Architecture principles.

**NFR-8.5**: Dependencies SHALL be kept up-to-date with latest stable versions.

**NFR-8.6**: The app SHALL support automated UI testing.

---

## 3. Constraints

### 3.1 Technical Constraints

**C-1.1**: WhatsApp does not provide an official API for message access.

**C-1.2**: NotificationListenerService may not capture all message details (e.g., media content).

**C-1.3**: Android may kill background processes to save battery, affecting message capture.

**C-1.4**: On-device AI models have limited accuracy compared to cloud-based LLMs.

**C-1.5**: Model file sizes (1-3GB) may be challenging for users with limited storage.

**C-1.6**: AI inference speed varies significantly across device hardware.

**C-1.7**: The app cannot access WhatsApp message history from before installation.

### 3.2 Business Constraints

**C-2.1**: The app must be completely free to use (no subscriptions, ads, or in-app purchases).

**C-2.2**: All AI models must be freely available and commercially usable.

**C-2.3**: The app must not violate WhatsApp Terms of Service.

**C-2.4**: The app must comply with Google Play Store policies.

### 3.3 Regulatory Constraints

**C-3.1**: The app must comply with GDPR (if targeting EU users).

**C-3.2**: The app must comply with CCPA (if targeting California users).

**C-3.3**: The app must include a privacy policy accessible from the app.

**C-3.4**: The app must respect user data deletion requests.

---

## 4. Assumptions

**A-1**: Users have WhatsApp installed and actively use group chats.

**A-2**: Users are willing to grant notification access permission.

**A-3**: Users have sufficient storage for at least one AI model.

**A-4**: WhatsApp will not significantly change notification format without notice.

**A-5**: Users have a basic understanding of how to grant permissions on Android.

**A-6**: Devices have sufficient RAM (4GB+) and processing power for AI inference.

**A-7**: Users have a stable internet connection for downloading AI models (one-time).

---

## 5. Dependencies

**D-1**: Android Jetpack libraries (Compose, Room, WorkManager, etc.)

**D-2**: AI inference libraries (llama-cpp-android, MediaPipe, MLC-LLM)

**D-3**: SQLCipher for database encryption

**D-4**: Hilt for dependency injection

**D-5**: OkHttp for model downloads

**D-6**: Kotlinx Serialization for JSON parsing

**D-7**: Availability of free, quantized AI models compatible with mobile devices

**D-8**: Ongoing WhatsApp notification format stability

---

## 6. Edge Cases & Error Handling

### 6.1 Message Capture Edge Cases

**EC-1.1**: **Deleted messages**: May still appear in notifications
- **Handling**: Mark as potentially deleted, don't include in summaries by default

**EC-1.2**: **Edited messages**: Original message captured, edit may not be detected
- **Handling**: Accept limitation, document in user guide

**EC-1.3**: **Very long messages**: Notification content may be truncated
- **Handling**: Capture what's available, indicate truncation in UI

**EC-1.4**: **Messages with only media**: No text content in notification
- **Handling**: Save with placeholder text like "[Image]", "[Video]"

**EC-1.5**: **Rapid message burst**: Many messages in short time
- **Handling**: Queue processing, batch database inserts

**EC-1.6**: **Notification permission revoked**: App can no longer capture messages
- **Handling**: Detect permission loss, show alert to user, guide to re-enable

### 6.2 AI Inference Edge Cases

**EC-2.1**: **Model fails to load**: File corrupted or incompatible
- **Handling**: Show error, offer re-download, fallback to different model

**EC-2.2**: **Inference timeout**: Model takes too long
- **Handling**: Cancel after 30s, show error, suggest smaller model

**EC-2.3**: **Out of memory during inference**: Device can't handle model
- **Handling**: Catch OOM, suggest smaller model or free up memory

**EC-2.4**: **Malformed AI response**: AI returns invalid JSON
- **Handling**: Parse with error handling, show partial summary or generic error

**EC-2.5**: **Empty thread**: User requests summary of thread with no messages
- **Handling**: Show message "No messages to summarize"

**EC-2.6**: **Very long thread**: Exceeds model context window
- **Handling**: Chunk messages, summarize in parts, combine summaries

### 6.3 Storage Edge Cases

**EC-3.1**: **Storage full**: Cannot save messages or models
- **Handling**: Warn user, offer cleanup options, disable capture temporarily

**EC-3.2**: **Model download interrupted**: Partial download
- **Handling**: Resume download from last checkpoint

**EC-3.3**: **Database corruption**: Database file damaged
- **Handling**: Attempt recovery, worst case: backup and recreate

### 6.4 Authentication Edge Cases

**EC-4.1**: **User forgets PIN**: Cannot access app
- **Handling**: Offer reset option with clear warning about data loss

**EC-4.2**: **Biometric changed**: User enrolls new fingerprint
- **Handling**: Biometric should still work, Android handles this

**EC-4.3**: **Biometric removed**: User removes all biometrics
- **Handling**: Fall back to PIN automatically

### 6.5 Background Processing Edge Cases

**EC-5.1**: **Doze mode**: Device in deep sleep
- **Handling**: WorkManager handles this, may delay execution

**EC-5.2**: **Battery saver enabled**: Background restrictions
- **Handling**: Show info to user about potential delays

**EC-5.3**: **App killed by system**: Low memory
- **Handling**: NotificationListenerService restarts, resume normal operation

---

## 7. Acceptance Criteria

### 7.1 User Story 1: First-Time User Onboarding

**As a** new user
**I want to** set up the app quickly
**So that** I can start getting summaries without hassle

**Acceptance Criteria**:
- [ ] Onboarding completes in under 5 minutes
- [ ] All steps have clear instructions
- [ ] User can skip optional steps
- [ ] Progress is saved if interrupted
- [ ] User receives confirmation when setup is complete

### 7.2 User Story 2: Viewing Daily Summary

**As a** parent
**I want to** see a summary of today's group messages
**So that** I don't miss important school information

**Acceptance Criteria**:
- [ ] Summary is automatically generated at the configured time
- [ ] Summary includes key topics, action items, and announcements
- [ ] Summary is easy to read and well-organized
- [ ] User can see which person said important things
- [ ] User can tap to see original messages if needed

### 7.3 User Story 3: On-Demand Summarization

**As a** user
**I want to** generate a summary for a specific group
**So that** I can catch up on conversations I haven't read

**Acceptance Criteria**:
- [ ] User can select any thread and request a summary
- [ ] Summary generates within 30 seconds
- [ ] Progress is shown during generation
- [ ] User is notified when summary is ready
- [ ] Error handling for failed generations

### 7.4 User Story 4: Searching for Information

**As a** user
**I want to** search for specific topics across all summaries
**So that** I can quickly find information I need

**Acceptance Criteria**:
- [ ] Search bar is easily accessible
- [ ] Search returns results within 1 second
- [ ] Results highlight matching terms
- [ ] User can filter by date, thread, or priority
- [ ] User can tap result to see full summary

### 7.5 User Story 5: Managing AI Models

**As a** user
**I want to** download and switch between AI models
**So that** I can choose the best model for my device

**Acceptance Criteria**:
- [ ] User can see available models with size and requirements
- [ ] Download progress is shown clearly
- [ ] User can pause/resume downloads
- [ ] User can switch active model
- [ ] User can delete models to free space

### 7.6 User Story 6: Protecting Privacy

**As a** privacy-conscious user
**I want to** secure my message data
**So that** no one else can access my information

**Acceptance Criteria**:
- [ ] PIN is required on every app launch
- [ ] Biometric unlock works as expected
- [ ] Data is encrypted at rest
- [ ] No data is sent to external servers
- [ ] User can delete all data

---

## 8. Quality Attributes

### 8.1 Testability
- Unit tests for business logic
- Integration tests for repositories
- UI tests for critical flows
- Mock AI engines for testing
- Test database for isolated testing

### 8.2 Extensibility
- Plugin architecture for AI engines
- Easy to add new model types
- Modular architecture allows feature additions
- Repository pattern allows data source changes

### 8.3 Monitorability
- Crash reporting (Crashlytics)
- Performance monitoring
- Error logging (Timber)
- User feedback mechanism

### 8.4 Accessibility
- TalkBack support
- Dynamic font sizes
- High contrast mode
- Minimum 48dp touch targets
- Clear focus indicators

---

## 9. Future Enhancements (Out of Scope for MVP)

**FE-1**: iOS version using Kotlin Multiplatform

**FE-2**: Export summaries to PDF or text files

**FE-3**: Conversation analytics and insights

**FE-4**: Custom summary templates

**FE-5**: Multi-language support for non-English groups

**FE-6**: Widget showing latest summaries

**FE-7**: Wear OS companion app

**FE-8**: Voice summary narration

**FE-9**: Integration with other messaging platforms (Telegram, Signal)

**FE-10**: Cloud backup (optional, user-controlled)

---

**Document Version**: 1.0
**Last Updated**: 2026-01-31
**Status**: Final Draft
