# Simple Debug Steps - Copy-Paste Commands

## Step 1: Install New APK

```bash
cd /Users/mateusz.byczkowski/Dev/covantis/others/summarizer
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Wait for "Success" message.

---

## Step 2: Grant Notification Listener Permission

```bash
adb shell cmd notification allow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener
```

---

## Step 3: Disable Battery Optimization

```bash
adb shell dumpsys deviceidle whitelist +com.summarizer.app
```

---

## Step 4: Force Rebind Service

```bash
adb shell cmd notification disallow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener
```

Wait 2 seconds, then:

```bash
adb shell cmd notification allow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener
```

---

## Step 5: Restart App

```bash
adb shell am force-stop com.summarizer.app
```

Wait 2 seconds, then:

```bash
adb shell am start -n com.summarizer.app/.MainActivity
```

---

## Step 6: Clear Logs and Start Monitoring

```bash
adb logcat -c
```

Then immediately:

```bash
adb logcat | grep -E "WhatsAppListener|onCreate|CONNECTED|DISCONNECTED"
```

---

## What You Should See

Within 5 seconds of starting the app, you **MUST** see:

```
🔵 NotificationListenerService onCreate() called - Service is being created!
🟢 NotificationListenerService CONNECTED - Ready to receive notifications!
```

If you see these two lines ✅ - the service is working!

If you DON'T see these lines ❌ - the service is blocked by MIUI or permission not granted.

---

## After Seeing CONNECTED, Send Test WhatsApp Message

1. Keep logcat running (from Step 6)
2. Send a message to ANY WhatsApp group
3. You should immediately see in logs:

```
🔔 Notification received from package: com.whatsapp
✅ WhatsApp notification confirmed!
📱 WHATSAPP NOTIFICATION:
   Title: 'Group Name'
   Text: 'Sender: Message'
```

---

## If You See NOTHING in Logs

The notification listener permission is NOT actually granted. You MUST grant it manually:

### On Your Phone:
1. Open **Settings**
2. Go to **Apps** → **Special app access** → **Notification access**
3. Find **Summarizer** in the list
4. Toggle it **ON** (should be enabled)
5. Go back and repeat Step 4 (Force Rebind Service)

---

## Copy and Paste This

After running all steps, copy and paste:
1. Everything you see in the logcat output
2. Tell me: Did you see "🔵 onCreate" and "🟢 CONNECTED"? Yes/No
3. Tell me: Did you see "🔔 Notification received" when you sent WhatsApp message? Yes/No
