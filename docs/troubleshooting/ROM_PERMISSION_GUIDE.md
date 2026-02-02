# ROM Permission Requirements Guide

## Problem Statement

Many Android devices (especially those from Chinese manufacturers like Xiaomi, OPPO, Vivo, OnePlus) use custom ROMs with aggressive battery optimization and permission restrictions. These ROMs prevent the `NotificationListenerService` from working properly even when the user has explicitly granted notification access.

### Symptoms

- User grants Notification Listener permission ✅
- Service shows as "enabled" in settings ✅
- But service **never receives** any notifications ❌
- Logs show: `AutoStartManagerService: MIUILOG- Reject service` ❌

## Affected ROMs

| ROM | Manufacturer | Issue |
|-----|--------------|-------|
| **MIUI** | Xiaomi, Redmi, POCO | AutoStart Manager blocks service binding |
| **ColorOS** | OPPO, Realme | Similar AutoStart restrictions |
| **FunTouch OS** | Vivo | Background activity restrictions |
| **OxygenOS** | OnePlus | Battery optimization issues |
| **EMUI** | Huawei, Honor | App launch restrictions |

## Our Solution (Automated)

The app now **automatically detects** restrictive ROMs and guides users through the required steps:

### 1. **Onboarding Detection**

When the user reaches the Permission Explanation screen, the app:
- Detects the ROM type using `Build.MANUFACTURER` and `Build.BRAND`
- Shows an additional warning card if on MIUI/ColorOS/etc.
- Provides a direct button to open AutoStart settings

```kotlin
if (PermissionHelper.isRestrictiveROM()) {
    // Show warning card with "Open AutoStart Settings" button
}
```

### 2. **Direct Intent to AutoStart Settings**

The app opens the manufacturer-specific AutoStart settings page:

```kotlin
fun openAutoStartSettings(context: Context): Boolean {
    val intent = when (manufacturer) {
        "xiaomi", "redmi" -> ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
        "oppo" -> ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        )
        // ... other manufacturers
    }
    context.startActivity(intent)
}
```

### 3. **Service Health Monitoring**

The app tracks whether the NotificationListenerService is actually working:

```kotlin
// In WhatsAppNotificationListener
override fun onListenerConnected() {
    ServiceHealthMonitor.recordServiceConnected(context)
}

override fun onNotificationPosted(sbn: StatusBarNotification) {
    if (isWhatsAppNotification(sbn.packageName)) {
        ServiceHealthMonitor.recordNotificationReceived(context)
    }
}
```

The health monitor can detect if:
- Service is enabled but never received notifications
- Service was working but stopped receiving notifications
- AutoStart permission is likely missing

### 4. **User-Friendly Instructions**

The app provides ROM-specific instructions:

```kotlin
fun getAutoStartInstructions(): String {
    return when (manufacturer) {
        "xiaomi" -> "Settings → Apps → Manage apps → ThreadSummarizer → Autostart → Enable"
        "oppo" -> "Settings → Apps → App Management → ThreadSummarizer → Allow autostart"
        // ... other ROMs
    }
}
```

## Manual Steps for Users (Fallback)

If automatic detection fails, users can manually enable AutoStart:

### MIUI (Xiaomi, Redmi, POCO)

1. Open **Settings**
2. Go to **Apps** → **Manage apps**
3. Find **ThreadSummarizer**
4. Enable **Autostart**
5. Also enable:
   - **Display pop-up windows while running in the background**
   - **Battery saver** → No restrictions

### ColorOS (OPPO, Realme)

1. Open **Settings**
2. Go to **Apps** → **App Management**
3. Find **ThreadSummarizer**
4. Enable **Allow autostart**
5. Enable **Allow background activity**

### FunTouch OS (Vivo)

1. Open **Settings**
2. Go to **Battery** → **Background activity management**
3. Find **ThreadSummarizer**
4. Enable **High background activity**

### OxygenOS (OnePlus)

1. Open **Settings**
2. Go to **Apps** → **ThreadSummarizer**
3. Go to **Battery** → **Battery optimization**
4. Select **Don't optimize**

### EMUI (Huawei, Honor)

1. Open **Settings**
2. Go to **Apps** → **ThreadSummarizer**
3. Go to **Battery** → **App launch**
4. Select **Manage manually**
5. Enable:
   - Auto-launch
   - Secondary launch
   - Run in background

## Testing the Fix

After enabling AutoStart:

1. **Force rebind the service:**
   ```bash
   adb shell cmd notification disallow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener
   adb shell cmd notification allow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener
   ```

2. **Check logs for successful connection:**
   ```bash
   adb logcat | grep "WhatsAppListener"
   ```

   You should see:
   ```
   🔵 NotificationListenerService onCreate() called
   🟢 NotificationListenerService CONNECTED
   ```

3. **Send a test WhatsApp message**

   You should see:
   ```
   🔔 Notification received from package: com.whatsapp
   ✅ WhatsApp notification confirmed!
   💾 Attempting to save to database...
   ✅ Message saved successfully!
   ```

## For Beta Testers

If you're testing on MIUI/ColorOS/etc., please:

1. ✅ Complete normal onboarding
2. ✅ Grant Notification Listener permission
3. ✅ **CRITICAL:** Tap "Open AutoStart Settings" button in the warning card
4. ✅ Enable AutoStart for ThreadSummarizer
5. ✅ Restart the app
6. ✅ Send a test WhatsApp message
7. ✅ Verify the message appears in the app

## Known Issues

### Issue: Service still not working after enabling AutoStart

**Solution:**
1. Reboot the device
2. Check if MIUI Security Center has additional restrictions
3. Try manually adding the app to the "App lock" whitelist

### Issue: Service works but stops after device reboot

**Solution:**
1. Ensure AutoStart is enabled
2. Disable **Memory optimization** in MIUI Security
3. Lock the app in Recent Apps (swipe down on the app card)

## Implementation Checklist

- [x] Add `PermissionHelper.isRestrictiveROM()` detection
- [x] Add `PermissionHelper.openAutoStartSettings()` with manufacturer-specific intents
- [x] Update `PermissionExplanationScreen` with conditional warning card
- [x] Add `ServiceHealthMonitor` to track service health
- [x] Integrate monitoring into `WhatsAppNotificationListener`
- [ ] Add banner in `ThreadListScreen` if service is broken (TODO)
- [ ] Add in-app troubleshooting guide (TODO)
- [ ] Add crash analytics for AutoStart rejection detection (TODO)

## Analytics to Track

For production, track these events to understand user impact:

1. **ROM Distribution:**
   - `rom_type: miui|coloros|funtouch|oxygenos|emui|stock`

2. **Permission Flow:**
   - `autostart_warning_shown: true|false`
   - `autostart_settings_opened: true|false`

3. **Service Health:**
   - `service_enabled_but_broken: true|false`
   - `time_to_first_notification: milliseconds`

4. **Failure Modes:**
   - `autostart_rejection_detected: true|false`
   - `service_never_connected: true|false`

## Future Improvements

1. **Persistent notification** when service is broken
2. **In-app troubleshooting wizard** with step-by-step screenshots
3. **Automatic detection** of AutoStart setting state (if possible)
4. **Background worker** that periodically checks service health
5. **Push notification** to user if service stops working

## References

- [MIUI AutoStart Permission Issue](https://github.com/topics/miui-autostart)
- [Dontkillmyapp.com - MIUI](https://dontkillmyapp.com/xiaomi)
- [Android Notification Listener Best Practices](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
