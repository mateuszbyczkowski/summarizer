# Troubleshooting WhatsApp Message Capture

## Current Issue
Messages from WhatsApp group "Testowa" are not being captured despite notification listener permission being granted.

## Debug Build Status
✅ APK built with extensive logging: `app/build/outputs/apk/debug/app-debug.apk` (87MB)
✅ All group filtering DISABLED (accepts ANY WhatsApp notification)
✅ Comprehensive logging added to trace notification flow

---

## Step-by-Step Debugging Instructions

### Step 1: Verify NotificationListenerService is Running

Run this command to check if the service is enabled:
```bash
adb shell cmd notification allow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener
```

To verify it's in the enabled list:
```bash
adb shell cmd notification allow_listener
```

You should see `com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener` in the output.

### Step 2: Install Debug APK

```bash
cd /Users/mateusz.byczkowski/Dev/covantis/others/summarizer
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Restart the App Completely

```bash
adb shell am force-stop com.summarizer.app
adb shell am start -n com.summarizer.app/.MainActivity
```

### Step 4: Start Log Monitoring

Open a terminal and run:
```bash
adb logcat -c  # Clear existing logs
adb logcat | grep -E "WhatsAppListener|🔔|📱|✅|❌|💾|⚠️"
```

This will show ONLY the WhatsApp notification-related logs with our emoji markers.

### Step 5: Send Test Messages

1. Open WhatsApp on your device
2. Navigate to the "Testowa" group
3. Send several test messages with different content:
   - A simple text message: "Test 1"
   - A message with emoji: "Test 2 😊"
   - A slightly longer message: "This is test message number 3"

### Step 6: Analyze Logs

You should see output like this for EACH WhatsApp notification:

```
🔔 Notification received from package: com.whatsapp
✅ WhatsApp notification confirmed!
📱 WHATSAPP NOTIFICATION:
   Title: 'Testowa'
   Text: 'Sender: Test 1'
   BigText: 'Sender: Test 1'
   SubText: null
   Time: 1234567890
💾 Attempting to save to database...
✅ Message saved successfully!
```

#### Scenario A: You see "🔔 Notification received" but package is NOT com.whatsapp
**Problem**: Notifications are being received, but not from WhatsApp
**Solution**: Check if WhatsApp is installed and you're using the standard version (not Business)

#### Scenario B: You see NO logs at all (not even 🔔)
**Problem**: NotificationListenerService is not receiving ANY notifications
**Possible causes**:
1. Permission not actually granted (check Settings → Apps → Summarizer → Notifications → Notification access)
2. Service not bound properly
3. Battery optimization killing the service

**Solutions**:
```bash
# Disable battery optimization
adb shell dumpsys deviceidle whitelist +com.summarizer.app

# Force rebind the notification listener
adb shell cmd notification disallow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener
adb shell cmd notification allow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener
```

#### Scenario C: You see "🔔" and "✅ WhatsApp notification confirmed!" but NO "📱 WHATSAPP NOTIFICATION:"
**Problem**: Exception occurring when extracting notification content
**Solution**: Check the full logcat for stack traces:
```bash
adb logcat | grep -A 20 "❌ ERROR processing notification"
```

#### Scenario D: You see "📱 WHATSAPP NOTIFICATION:" but title/text are empty
**Problem**: WhatsApp notification format has changed
**Solution**: We need to inspect ALL extras - modify the code to log all extras

#### Scenario E: Everything logs correctly but "❌ ERROR" appears
**Problem**: Database save failed
**Solution**: Check the exception details in logcat

---

## Additional Diagnostic Commands

### Check if WhatsApp is installed
```bash
adb shell pm list packages | grep whatsapp
```
Expected output: `com.whatsapp` or `com.whatsapp.w4b`

### Check app permissions
```bash
adb shell dumpsys package com.summarizer.app | grep permission
```

### Check if service is running
```bash
adb shell dumpsys activity services | grep WhatsAppNotificationListener
```

### View all recent notifications (system-level)
```bash
adb shell dumpsys notification --noredact | grep -A 30 "com.whatsapp"
```

This shows if WhatsApp notifications are even being posted to the system.

---

## Manual Permission Check (via UI)

1. Open Settings on your device
2. Apps → Special app access → Notification access
3. Verify "Summarizer" is ENABLED
4. If it's already enabled, try:
   - Disable it
   - Restart the app
   - Re-enable it
   - Restart the app again

---

## Known Issues & Workarounds

### Issue: Samsung/Xiaomi/Huawei Aggressive Battery Management
Some manufacturers aggressively kill background services.

**Solution**:
1. Settings → Apps → Summarizer
2. Battery → Unrestricted
3. Auto-start → Enable (if available)
4. Background restrictions → Don't optimize

### Issue: Android 12+ Notification Trampolining
Android 12+ restricts how notifications can launch activities.

**Current Status**: ✅ Not applicable - we're only reading notifications, not launching activities

### Issue: WhatsApp Notification Grouping
WhatsApp groups multiple messages into summary notifications.

**Current Status**: ✅ Handled - we log `bigText` which contains full content

---

## Next Steps Based on Findings

After running the debug steps above, report back with:

1. **What you see in logcat** (copy-paste the actual output)
2. **Which scenario matches** (A, B, C, D, or E from above)
3. **WhatsApp package name** from `pm list packages` command
4. **Notification access status** from manual UI check

This will help us pinpoint the exact issue.
