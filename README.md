# WhatsApp Thread Summarizer
## AI-Powered Message Summarization for Parents

> **Helping busy parents stay informed about their children's school activities without drowning in WhatsApp group messages.**

---

## Project Overview

WhatsApp Thread Summarizer is an Android mobile application designed to automatically capture, organize, and summarize WhatsApp group conversations using local AI models. The app helps parents manage the overwhelming volume of messages from school and kindergarten WhatsApp groups by providing concise, structured summaries of conversations.

### Key Features

- **Automatic Message Capture**: Listens to WhatsApp group notifications and saves messages locally
- **AI-Powered Summarization**: Uses on-device AI models to generate intelligent summaries
- **Complete Privacy**: 100% offline operation, no data leaves the device
- **Smart Summaries**: Extracts key topics, action items, deadlines, and important announcements
- **Secure Access**: Protected by PIN and biometric authentication
- **Thread Prioritization**: Mark important groups for detailed summaries
- **Smart Notifications**: Get notified only about truly important messages
- **Search**: Find specific information across all summaries

### Why This App?

Parents with children in schools and kindergartens often belong to multiple WhatsApp groups that generate hundreds of messages daily. Important information about schedules, events, field trips, and deadlines gets buried in casual conversations. This app solves that problem by automatically summarizing conversations and highlighting what truly matters.

---

## Documentation

This repository contains comprehensive documentation for the project:

### 📋 [Product Requirements Document (PRD.md)](./PRD.md)
The complete product specification including:
- Product vision and target audience
- Detailed feature descriptions
- User flows and information architecture
- Success criteria and metrics
- Risk analysis and mitigation strategies
- Timeline and phasing

**Start here** to understand the product vision and business requirements.

### 🔧 [Technical Specification (TECHNICAL_SPECIFICATION.md)](./TECHNICAL_SPECIFICATION.md)
Detailed technical architecture and implementation details:
- Technology stack and frameworks
- System architecture diagrams
- Core component designs (message capture, AI, security)
- Database schema and data models
- AI inference implementation
- Performance optimization strategies
- Security implementation

**For developers** to understand the technical architecture and design decisions.

### 📅 [Implementation Plan (IMPLEMENTATION_PLAN.md)](./IMPLEMENTATION_PLAN.md)
Week-by-week implementation roadmap:
- 12-week MVP development plan
- Phase-by-phase breakdown
- Specific milestones and deliverables
- Resource requirements
- Risk mitigation strategies
- Success criteria and launch checklist

**For project planning** and tracking development progress.

### ✅ [Requirements Document (REQUIREMENTS.md)](./REQUIREMENTS.md)
Comprehensive functional and non-functional requirements:
- Detailed functional requirements (FR)
- Non-functional requirements (NFR)
- Performance, security, and usability requirements
- Edge cases and error handling
- User stories with acceptance criteria
- Quality attributes

**For validation** that all requirements are met during development and testing.

---

## Technology Stack

### Core Technologies
- **Language**: Kotlin 1.9+
- **Platform**: Android 10+ (API 29+)
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Clean Architecture

### Key Libraries & Frameworks
- **Dependency Injection**: Hilt (Dagger)
- **Database**: Room with SQLCipher encryption
- **Concurrency**: Kotlin Coroutines & Flow
- **Background Processing**: WorkManager
- **Security**: Jetpack Security, BiometricPrompt

### AI Inference (Framework Agnostic)
- **llama.cpp** (via llama-cpp-android)
- **MediaPipe LLM Inference**
- **MLC-LLM**

### AI Model (I1)
- **Model**: Phi-2 Q4_K_M (~1.6GB)
- **Source**: Hugging Face (TheBloke/Phi-2-GGUF)
- **Framework**: llama.cpp only

*Future versions will support multiple models and frameworks*

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│              Presentation Layer (Compose)                │
│         Home | Threads | Search | Settings              │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                 ViewModel Layer (MVVM)                   │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│              Domain Layer (Use Cases)                    │
│  • Capture Messages  • Generate Summaries               │
│  • Search Summaries  • Manage Models                    │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                Data Layer (Repositories)                 │
│  Messages | Summaries | AI Models | Security            │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│             Infrastructure Layer                         │
│  • Room DB (Encrypted)                                   │
│  • AI Engine Manager                                     │
│  • NotificationListenerService                           │
└─────────────────────────────────────────────────────────┘
```

---

## Development Timeline

### 🎯 Current Focus: I1 - Minimal MVP (6 Weeks)

**I1 Goal**: Validate core concept with beta testers

**I1 Features**:
- Simple onboarding (PIN only, no biometric)
- WhatsApp message capture
- Thread list view
- On-demand summarization (manual only)
- Single AI model: TinyLlama 1.1B from Hugging Face

**Progress**:
- ✅ **Week 1**: Project setup + foundation + PIN auth + message capture (COMPLETE 2026-01-31)
- 🎯 **Week 2**: Message capture refinement + edge cases (CURRENT)
- 📅 **Week 3**: Onboarding flow + UI polish
- 📅 **Week 4**: Model download from Hugging Face
- 📅 **Week 5**: AI integration (llama.cpp + TinyLlama)
- 📅 **Week 6**: Testing + polish for beta

**See [I1_SCOPE.md](./I1_SCOPE.md) for detailed I1 specifications.**

---

### Future: Full Product (Post-I1)

After I1 beta validation, build additional features:
- Daily auto-summarization
- Smart notifications
- Biometric authentication
- Search functionality
- Thread prioritization
- Multiple AI models
- Auto-cleanup

**See full documentation ([PRD.md](./PRD.md), [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)) for complete product vision.**

---

## Key Design Decisions

### 1. **Offline-First Architecture**
All processing happens on-device. No cloud dependencies ensure complete privacy and work without internet connectivity.

### 2. **Framework-Agnostic AI**
Support multiple AI inference frameworks to maximize model compatibility and future-proof the app.

### 3. **NotificationListenerService**
Most reliable method for capturing WhatsApp messages on Android without violating ToS.

### 4. **Automatic Cleanup**
Auto-delete messages after 30 days to manage storage while retaining summaries indefinitely.

### 5. **Security-First**
Encrypted storage, PIN + biometric auth, and Android Keystore ensure user data is protected.

### 6. **MVVM + Clean Architecture**
Separation of concerns, testability, and maintainability through layered architecture.

---

## Privacy & Security

### Privacy Commitments
- ✅ **No Cloud Storage**: All data stays on device
- ✅ **No Analytics**: Zero user tracking
- ✅ **No Network Requests**: Fully offline (except one-time model downloads)
- ✅ **Open Source Models**: Transparent AI processing
- ✅ **User Control**: Delete all data anytime

### Security Features
- ✅ **Encrypted Database**: AES-256 encryption via SQLCipher
- ✅ **Secure Authentication**: SHA-256 hashed PIN + biometric
- ✅ **Android Keystore**: Hardware-backed key storage
- ✅ **Auto-Lock**: Locks when app goes to background
- ✅ **Code Obfuscation**: R8/ProGuard in release builds

---

## Device Requirements

### Minimum Requirements (I1)
- **OS**: Android 12.0 (API 31) or higher
- **RAM**: 4GB minimum
- **Storage**: 3GB free space (for app + Phi-2 model)
- **Processor**: ARMv8 64-bit

### Recommended Specifications
- **OS**: Android 13+ for best experience
- **RAM**: 6GB or more
- **Storage**: 5GB free space
- **Processor**: Mid-range or better (Snapdragon 7 series+)

---

## User Flow Example

1. **Install & Onboard** (3-5 minutes)
   - Grant notification permission
   - Set up 6-digit PIN
   - Enable biometric unlock
   - Download AI model
   - Select groups to monitor

2. **Automatic Capture**
   - App captures WhatsApp group messages in background
   - Messages stored locally and encrypted

3. **Daily Summary** (8 PM by default)
   - AI automatically generates summaries
   - Notification alerts user when ready

4. **Review Summary**
   - Open app (unlock with PIN/biometric)
   - View structured summary:
     - Key topics discussed
     - Action items with deadlines
     - Important announcements
     - Who said what important
   - Tap to see original messages if needed

5. **Search When Needed**
   - Search across all summaries
   - Find specific information (e.g., "field trip", "permission slip")

---

## Risk Mitigation

| Risk | Mitigation Strategy |
|------|---------------------|
| WhatsApp notification format changes | Flexible parser, monitor updates, quick patches |
| AI models too large/slow | Multiple model sizes, clear requirements |
| Battery drain | Optimized background processing, battery monitoring |
| Storage limitations | Aggressive cleanup, user warnings |
| Summary quality issues | Multiple model options, prompt engineering |
| Security concerns | Encryption, open security audit, transparent privacy policy |

---

## Out of Scope (Phase 1)

The following features are **not included** in the MVP:

- ❌ iOS version (planned for Phase 2)
- ❌ Message sending/replying
- ❌ Cloud backup/sync
- ❌ Other messaging platforms (Telegram, Signal, etc.)
- ❌ Analytics and statistics
- ❌ Widget support
- ❌ Wear OS app
- ❌ Voice summaries

---

## Success Metrics

### MVP Launch Criteria
- ✅ Successfully capture WhatsApp messages
- ✅ Generate readable summaries (80%+ accuracy)
- ✅ PIN + biometric authentication working
- ✅ At least 2 AI models available
- ✅ Daily auto-summarization functional
- ✅ Search working across summaries
- ✅ < 1% crash rate
- ✅ Performance benchmarks met

### User Success Metrics
- **Time Saved**: 15-20 minutes per day
- **User Satisfaction**: 4.0+ star rating
- **Retention**: 60%+ DAU retention
- **Onboarding Completion**: 80%+ complete onboarding

---

## Contributing

This project is currently in the planning phase. Once development begins, contributions will be welcome. Please refer to the implementation plan for development phases and milestones.

---

## License

To be determined (recommend MIT or Apache 2.0 for open source)

---

## Contact & Support

For questions about this project, please refer to the documentation files or contact the project maintainer.

---

## Appendix: Related Documentation

### I1 Documentation (Current Focus)
1. **[I1_SCOPE.md](./I1_SCOPE.md)** - I1 Minimal MVP Specification ⭐ **START HERE**

### Full Product Documentation (Future Reference)
2. **[PRD.md](./PRD.md)** - Product Requirements Document
3. **[TECHNICAL_SPECIFICATION.md](./TECHNICAL_SPECIFICATION.md)** - Technical Architecture
4. **[IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)** - Development Roadmap (12-week full version)
5. **[REQUIREMENTS.md](./REQUIREMENTS.md)** - Detailed Requirements

---

**Document Version**: 1.2
**Last Updated**: 2026-01-31
**Project Status**: Development - Week 1 COMPLETE 🎉 (25% overall)
**Next Milestone**: Week 2 - Message capture refinement
**Progress Tracking**: See [PROGRESS.md](./PROGRESS.md)
