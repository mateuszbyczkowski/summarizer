# MIUI/Xiaomi Fix for Message Capture

## Problem Identified
MIUI (Xiaomi's Android skin) **aggressively kills background services**, including NotificationListenerService, even when all permissions are granted. This is why messages aren't being captured.

## Required MIUI-Specific Settings

### Step 1: Disable Battery Optimization (MIUI Style)
1. Open **Settings**
2. Tap **Apps** → **Manage apps**
3. Find and tap **Summarizer**
4. Tap **Battery saver**
5. Select **No restrictions** (CRITICAL!)

### Step 2: Enable Autostart
1. Still in Summarizer app settings
2. Tap **Autostart** (or **Permissions**)
3. **Enable Autostart** toggle (CRITICAL!)
4. This allows the app to run in background

### Step 3: Lock App in Recent Apps
1. Open **Recent Apps** (square button or swipe up and hold)
2. Find the **Summarizer** app card
3. **Swipe down** on the app card (or tap the lock icon)
4. This prevents MIUI from killing it

### Step 4: Disable MIUI Battery Saver
1. Open **Settings**
2. Tap **Battery & performance**
3. Tap **Battery saver**
4. Make sure it's **OFF** or add Summarizer to exceptions

### Step 5: Additional MIUI Permissions
1. **Settings** → **Apps** → **Summarizer** → **Permissions**
2. Enable:
   - ✅ **Display pop-up windows while running in background**
   - ✅ **Display pop-up window**
   - ✅ **Start in background**
   - ✅ **Permanent notification** (if available)

### Step 6: Notification Access (Re-enable)
1. **Settings** → **Apps** → **Special permissions** → **Notification access**
2. Find **Summarizer**
3. **Disable** it
4. Restart the app
5. **Enable** it again
6. Restart the app again

### Step 7: MIUI Optimization (Advanced)
If none of the above works, try disabling MIUI Optimization:
1. Open **Settings** → **Additional settings** → **Developer options**
2. Find **MIUI optimization**
3. **Disable** it (WARNING: This may affect other MIUI features)
4. Restart device
5. Re-enable all permissions for Summarizer

## Testing After Applying Settings

After completing ALL steps above:

1. **Force stop** the app:
   ```bash
   adb shell am force-stop com.summarizer.app
   ```

2. **Clear logcat**:
   ```bash
   adb logcat -c
   ```

3. **Start the app**:
   ```bash
   adb shell am start -n com.summarizer.app/.MainActivity
   ```

4. **Watch logs** and look for the critical connection message:
   ```bash
   adb logcat | grep -E "🟢|🔴|WhatsAppListener"
   ```

   You **MUST** see this line within a few seconds:
   ```
   🟢 NotificationListenerService CONNECTED - Ready to receive notifications!
   ```

   If you see:
   ```
   🔴 NotificationListenerService DISCONNECTED
   ```
   Or see nothing at all, MIUI is still killing the service.

5. **Send a test WhatsApp message** to "Testowa" group

6. **Check logs** for:
   ```
   🔔 Notification received from package: com.whatsapp
   ✅ WhatsApp notification confirmed!
   📱 WHATSAPP NOTIFICATION:
   ```

## Alternative Approach: ADB Command to Whitelist

You can force-whitelist the app using ADB (requires root or special permissions):

```bash
# Add to battery optimization whitelist
adb shell dumpsys deviceidle whitelist +com.summarizer.app

# Disable battery restrictions via ADB
adb shell cmd appops set com.summarizer.app RUN_IN_BACKGROUND allow
adb shell cmd appops set com.summarizer.app START_IN_BACKGROUND allow
```

## Known MIUI Issues

- **MIUI 12+**: Most aggressive battery management
- **MIUI 13+**: Improved, but still problematic for notification listeners
- **MIUI 14**: Slightly better with "Gaming mode" trick (see below)

### Gaming Mode Trick (MIUI 13+)
1. Enable **Game Turbo** (Settings → Special features → Game Turbo)
2. Add **Summarizer** to game list (it tricks MIUI into not killing it)
3. This is hacky but effective

## Verification Checklist

Before testing, verify ALL of these are set:

- [ ] Battery saver: **No restrictions**
- [ ] Autostart: **Enabled**
- [ ] App locked in Recent Apps (lock icon visible)
- [ ] MIUI Battery Saver: **OFF** or app in exceptions
- [ ] "Display pop-up windows while running in background": **Enabled**
- [ ] Notification access: **Enabled** (disabled/re-enabled after other settings)
- [ ] App NOT killed by recent apps swipe-away

## Success Indicators

If everything is configured correctly, you should see in logcat:

```
I WhatsAppListener: 🟢 NotificationListenerService CONNECTED - Ready to receive notifications!
```

Then when you send a WhatsApp message:

```
I WhatsAppListener: 🔔 Notification received from package: com.whatsapp
I WhatsAppListener: ✅ WhatsApp notification confirmed!
I WhatsAppListener: 📱 WHATSAPP NOTIFICATION:
I WhatsAppListener:    Title: 'Testowa'
I WhatsAppListener:    Text: 'YourName: Your message'
...
I WhatsAppListener: ✅ Message saved successfully!
```

## If Still Not Working

If after ALL of the above the service still doesn't work, the nuclear option:

1. Factory reset MIUI settings (Settings → Additional settings → Backup & reset → Reset all settings)
2. Or switch to a custom ROM without MIUI
3. Or use a different device for testing

## Additional Resources

- [Don't kill my app! - MIUI](https://dontkillmyapp.com/xiaomi)
- MIUI's aggressive battery optimization is documented across Android community

---

**Next Step**: Apply ALL settings above, then run the diagnostic script:
```bash
./debug_message_capture.sh
```

Watch for the `🟢 NotificationListenerService CONNECTED` message!
