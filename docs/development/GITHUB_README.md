# WhatsApp Summarizer 📱

> AI-powered message summarization for busy parents managing school and kindergarten WhatsApp groups.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android%2012%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)

---

## The Problem

Parents with children in schools and kindergartens belong to multiple WhatsApp groups that generate **hundreds of messages daily**. Important information about:
- 📅 Schedule changes
- 🎒 Field trips and permission slips
- 📢 School announcements
- ⏰ Deadlines and events

...gets buried in endless casual conversations.

## The Solution

**WhatsApp Summarizer** automatically captures and summarizes your WhatsApp group messages using **on-device AI**, helping you stay informed without reading every message.

### ✨ Key Features

- 🤖 **AI-Powered Summaries**: Local AI generates intelligent summaries with key topics, action items, and deadlines
- 🔒 **100% Private**: All data stays on your device - no cloud, no tracking
- ⚡ **On-Demand**: Tap any thread to get an instant summary
- 🎯 **Smart Extraction**: Highlights important announcements and who said what
- 🔐 **Secure**: Protected by PIN authentication and encrypted storage

---

## How It Works

```
1. Grant notification permission
   ↓
2. App captures WhatsApp group messages
   ↓
3. Messages stored locally (encrypted)
   ↓
4. Tap "Summarize Now" on any thread
   ↓
5. View AI-generated summary:
   • Key topics discussed
   • Action items & deadlines
   • Important announcements
   • Participant highlights
```

---

## Current Status: I1 (Beta)

This is the initial beta version focused on proving the core concept. We're testing with a small group of parents to validate the approach.

### I1 Features
- ✅ WhatsApp message capture (NotificationListenerService)
- ✅ Thread organization
- ✅ On-demand AI summarization
- ✅ PIN authentication
- ✅ Encrypted local storage

### Coming Soon (Post-I1)
- ⏰ Daily auto-summarization
- 🔔 Smart notifications (only for important messages)
- 📱 Biometric authentication
- 🔍 Search across summaries
- ⭐ Thread prioritization

---

## Technology

- **Platform**: Android 12+ (API 31+)
- **Language**: Kotlin with Jetpack Compose
- **AI**: On-device inference using llama.cpp
- **Model**: TinyLlama 1.1B (I1) → Better models in future versions
- **Architecture**: MVVM with Clean Architecture
- **Security**: SQLCipher encryption + Android Keystore

**Privacy First**: No internet connection required after initial model download. Zero telemetry, zero tracking.

---

## Installation (Beta Testers)

**Requirements**:
- Android 12 or newer
- 4GB RAM minimum
- 2GB free storage

**Steps**:
1. Download the APK from [Releases](https://github.com/YOUR_USERNAME/whatsup-summarizer/releases)
2. Enable "Install from Unknown Sources" in Settings
3. Install the APK
4. Follow the onboarding to:
   - Grant notification permission
   - Set up PIN
   - Download AI model (~700MB)
5. Start capturing messages!

---

## Screenshots

_Coming soon - screenshots of thread list, summary view, and onboarding flow_

---

## Development

Want to contribute or build it yourself?

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17+
- Android device/emulator with Android 12+

### Quick Start

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/whatsup-summarizer.git
cd whatsup-summarizer

# Open in Android Studio
# Let Gradle sync dependencies

# Run on device/emulator
# Build → Run 'app'
```

See [I1_QUICKSTART.md](./I1_QUICKSTART.md) for detailed setup instructions.

### Documentation

- **[I1_SCOPE.md](./I1_SCOPE.md)** - Current MVP specification
- **[PRD.md](./PRD.md)** - Full product vision
- **[TECHNICAL_SPECIFICATION.md](./TECHNICAL_SPECIFICATION.md)** - Architecture details
- **[IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)** - Development roadmap

---

## Contributing

We welcome contributions! This is an early-stage project, and we'd love your help.

**How to contribute**:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

**Areas we need help**:
- 🧪 Testing on different devices
- 🎨 UI/UX improvements
- 🤖 AI prompt optimization
- 📱 iOS version (Kotlin Multiplatform)
- 🌍 Localization

---

## Beta Testing

We're currently testing with a small group of parents. Interested in joining?

**We need testers who**:
- Are parents with active school/kindergarten WhatsApp groups
- Can test for 1-2 weeks
- Provide honest feedback
- Have Android 12+ devices

**Contact**: [Open an issue](https://github.com/YOUR_USERNAME/whatsup-summarizer/issues) with the label `beta-tester`

---

## Roadmap

### I1 (Current) - Beta Testing
- Core message capture
- On-demand summarization
- Basic security

### I2 (Next) - Enhanced Features
- Daily auto-summarization
- Smart notifications
- Biometric unlock
- Search functionality

### I3 (Future) - Polish & Scale
- Multiple AI models
- Better summarization quality
- Export summaries
- iOS version

---

## Privacy & Security

We take privacy seriously:

- ✅ **No cloud storage** - everything stays on your device
- ✅ **No analytics** - we don't track anything
- ✅ **No internet** - works completely offline (after model download)
- ✅ **Encrypted storage** - AES-256 database encryption
- ✅ **Open source** - audit the code yourself

Read our [Privacy Policy](./PRIVACY.md) for details.

---

## FAQ

**Q: Does this violate WhatsApp's Terms of Service?**
A: No. The app uses Android's standard NotificationListenerService API and doesn't interact with WhatsApp directly.

**Q: Can the app send messages or access my contacts?**
A: No. It only reads notification content. It cannot send messages or access any other data.

**Q: How accurate are the summaries?**
A: I1 uses a small model for testing. Summary quality will improve significantly in I2 with better models.

**Q: Does it work with WhatsApp Business?**
A: Not yet in I1, but planned for I2.

**Q: What about iOS?**
A: iOS has much stricter limitations on accessing other app's notifications. We're exploring options for I3.

**Q: How much storage does it use?**
A: ~700MB for the AI model + message storage (depends on group activity). I2 will add auto-cleanup.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) - Efficient LLM inference
- [TinyLlama](https://github.com/jzhang38/TinyLlama) - Compact language model
- All the parent beta testers providing feedback

---

## Support

- 🐛 [Report a bug](https://github.com/YOUR_USERNAME/whatsup-summarizer/issues/new?labels=bug)
- 💡 [Request a feature](https://github.com/YOUR_USERNAME/whatsup-summarizer/issues/new?labels=enhancement)
- 💬 [Ask a question](https://github.com/YOUR_USERNAME/whatsup-summarizer/discussions)

---

**Made with ❤️ for busy parents**

⭐ Star this repo if you find it useful!
