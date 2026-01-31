# Android Project Setup Guide
## Getting Started with Development

## 🎉 Project Status: READY TO BUILD!

The complete Android project structure has been generated. You can now open it in Android Studio and start coding.

---

## Quick Start (5 minutes)

### 1. Open Project in Android Studio

```bash
# Navigate to project directory
cd /Users/mateusz.byczkowski/Dev/covantis/others/summarizer

# Open Android Studio (you can also File → Open in Android Studio GUI)
open -a "Android Studio" .
```

**OR**

1. Launch Android Studio
2. Click "Open"
3. Navigate to `/Users/mateusz.byczkowski/Dev/covantis/others/summarizer`
4. Click "OK"

### 2. Wait for Gradle Sync

Android Studio will automatically:
- Download all dependencies (~5 minutes first time)
- Index the project
- Build the project structure

Look for "Gradle sync successful" in the status bar.

### 3. Connect Device or Start Emulator

**Physical Device**:
```bash
# Check device connection
adb devices
```

**Emulator**:
- Tools → Device Manager → Create Device
- Select Pixel 7, Android 12 (API 31)

### 4. Run the App

Click the green ▶️ Run button or press `Ctrl + R`

**Expected Result**: App launches showing empty thread list screen!

---

## What's Already Built

### ✅ Infrastructure
- [x] Gradle configuration with all dependencies
- [x] Hilt dependency injection setup
- [x] Room database with SQLCipher encryption
- [x] Repository pattern implementation

### ✅ Features
- [x] **WhatsAppNotificationListener** - Captures messages (needs permission)
- [x] **Thread List Screen** - Displays captured threads
- [x] **Database Layer** - Messages, Threads, Summaries
- [x] **MVVM Architecture** - ViewModels and repositories ready

### ✅ UI
- [x] Jetpack Compose theme
- [x] Material 3 design
- [x] Navigation setup
- [x] Empty state screen

---

## Project Structure

```
summarizer/
├── app/
│   ├── build.gradle.kts          ← Dependencies
│   └── src/main/
│       ├── AndroidManifest.xml   ← Permissions & services
│       ├── kotlin/com/summarizer/app/
│       │   ├── MainActivity.kt
│       │   ├── SummarizerApplication.kt
│       │   ├── data/              ← Database & repos
│       │   ├── domain/            ← Business logic
│       │   ├── di/                ← Hilt modules
│       │   ├── service/           ← Notification listener
│       │   ├── ui/                ← Compose screens
│       │   └── util/              ← Constants
│       └── res/                   ← Resources (strings, themes)
├── build.gradle.kts              ← Project config
├── settings.gradle.kts
└── gradle.properties
```

---

## First Build Checklist

After opening in Android Studio:

### 1. Verify Gradle Sync
- ✅ Check bottom right: "Gradle sync successful"
- ⚠️ If errors: Try `File → Invalidate Caches → Restart`

### 2. Check Build Configuration
- ✅ Build variant: "debug"
- ✅ Target device: Your Android 12+ device or emulator

### 3. Run the App
- ✅ Press Run button
- ✅ App installs on device
- ✅ Empty screen displays (expected - no messages yet!)

### 4. Test Notification Listener (Optional for now)
- Open Settings → Apps → Summarizer → Notifications
- Enable notification access
- Send a WhatsApp group message
- Check if message appears in thread list

---

## Next Development Steps

### Week 1 (Current - Day 2-7)
Now that the foundation is ready, continue with:

1. **Test the notification listener** (Day 2-3)
   - Send test WhatsApp messages
   - Verify they appear in thread list
   - Debug parsing logic if needed

2. **Add Thread Detail screen** (Day 4-5)
   - Show individual messages
   - Add "Summarize Now" button (placeholder)

3. **Basic PIN authentication** (Day 6-7)
   - PIN setup screen
   - PIN entry screen
   - Lock/unlock logic

### Week 2: Model Download & AI Integration
- Implement model downloader
- Integrate llama.cpp
- Create summarization pipeline

---

## Troubleshooting

### Gradle Sync Fails
```bash
# In terminal, from project root:
./gradlew clean build --refresh-dependencies
```

### "Cannot find symbol" errors
- File → Invalidate Caches → Restart
- Build → Clean Project
- Build → Rebuild Project

### SQLCipher library issues
Add to `app/build.gradle.kts` in `android` block:
```kotlin
packagingOptions {
    jniLibs {
        useLegacyPackaging = true
    }
}
```

### Hilt errors
Make sure `SummarizerApplication` is set in `AndroidManifest.xml`:
```xml
<application
    android:name=".SummarizerApplication"
    ...>
```

### Room schema export warning
Create directory:
```bash
mkdir -p app/schemas
```

---

## Testing the Notification Listener

### Enable Notification Access

1. Launch the app
2. Go to Android Settings → Apps → Summarizer → Notifications
3. Enable "Notification access"
4. Grant permission

### Test Message Capture

1. Open WhatsApp
2. Join a test group (or create one with friends)
3. Send a few messages
4. Return to Summarizer app
5. Pull to refresh thread list
6. Verify group appears with message count

### Debug Logging

Check logcat for message capture:
```bash
adb logcat | grep "Summarizer"
```

You should see:
```
D/WhatsAppNotificationListener: Saved message from John in School Group
D/WhatsAppNotificationListener: Created new thread: School Group
```

---

## Development Tools

### Useful ADB Commands

```bash
# Check installed app
adb shell pm list packages | grep summarizer

# Clear app data (reset)
adb shell pm clear com.summarizer.app

# View database (requires root or debuggable app)
adb shell
run-as com.summarizer.app
cd databases
ls -l

# View logs
adb logcat -s SummarizerApp:D AndroidRuntime:E
```

### Android Studio Tips

- **Cmd + O**: Quick file search
- **Cmd + Shift + O**: Search everywhere
- **Cmd + B**: Go to declaration
- **Cmd + Alt + L**: Reformat code
- **Cmd + /**: Toggle comment

---

## Code Quality

### Before Committing
```bash
# Format code
# Android Studio: Cmd + Alt + L (on selection or whole file)

# Run lint checks
./gradlew lint

# Run tests (when you write them)
./gradlew test
```

---

## What's NOT Implemented Yet (Week 2+)

- ❌ Onboarding flow (permission request screens)
- ❌ PIN authentication
- ❌ Thread detail screen
- ❌ Model download functionality
- ❌ AI summarization engine
- ❌ Summary display screen

These will be built in upcoming weeks following [I1_SCOPE.md](./I1_SCOPE.md).

---

## Getting Help

### Documentation
- [I1_SCOPE.md](./I1_SCOPE.md) - What to build
- [I1_QUICKSTART.md](./I1_QUICKSTART.md) - Development guide
- [TECHNICAL_SPECIFICATION.md](./TECHNICAL_SPECIFICATION.md) - Architecture details

### Common Questions

**Q: Where do I add new screens?**
A: `app/src/main/kotlin/com/summarizer/app/ui/screens/`

**Q: How do I add a new database entity?**
A:
1. Create entity in `data/local/entity/`
2. Add DAO in `data/local/database/dao/`
3. Update `AppDatabase.kt`
4. Bump version number
5. Sync Gradle

**Q: Where's the AI code?**
A: Not implemented yet - that's Week 5. Focus on getting message capture working first.

---

## Success Criteria - Week 1

By end of week 1, you should have:
- ✅ App running on your device
- ✅ Notification listener working
- ✅ Messages appearing in thread list
- ✅ Basic navigation between screens
- ✅ Understanding of the codebase structure

---

**Ready to code!** 🚀

Open Android Studio, run the app, and start building!
