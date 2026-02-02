#!/bin/bash

# Debug Message Capture - Automated Diagnostics
# Usage: ./debug_message_capture.sh

set -e

echo "=================================================="
echo "WhatsApp Message Capture Diagnostic Tool"
echo "=================================================="
echo ""

# Check if device is connected
echo "Step 1: Checking for connected Android device..."
if ! adb devices | grep -q "device$"; then
    echo "❌ No Android device found. Please connect your device and enable USB debugging."
    exit 1
fi
echo "✅ Device connected"
echo ""

# Check if WhatsApp is installed
echo "Step 2: Checking WhatsApp installation..."
WHATSAPP_PACKAGE=$(adb shell pm list packages | grep whatsapp | head -1 | cut -d: -f2 | tr -d '\r\n')
if [ -z "$WHATSAPP_PACKAGE" ]; then
    echo "❌ WhatsApp not found on device"
    exit 1
fi
echo "✅ WhatsApp found: $WHATSAPP_PACKAGE"
echo ""

# Check if app is installed
echo "Step 3: Checking if Summarizer is installed..."
if ! adb shell pm list packages | grep -q "com.summarizer.app"; then
    echo "⚠️  Summarizer not installed. Installing now..."
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    echo "✅ Summarizer installed"
else
    echo "✅ Summarizer already installed"
fi
echo ""

# Enable notification listener
echo "Step 4: Enabling NotificationListenerService..."
adb shell cmd notification allow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener 2>/dev/null || true
echo "✅ Notification listener enabled"
echo ""

# Disable battery optimization
echo "Step 5: Disabling battery optimization..."
adb shell dumpsys deviceidle whitelist +com.summarizer.app 2>/dev/null || true
echo "✅ Battery optimization disabled"
echo ""

# Restart the app
echo "Step 6: Restarting app to bind notification listener..."
adb shell am force-stop com.summarizer.app
sleep 2
adb shell am start -n com.summarizer.app/.MainActivity
echo "✅ App restarted"
echo ""

# Clear logs and start monitoring
echo "Step 7: Starting log monitor..."
echo "=================================================="
echo "SEND TEST MESSAGES TO WHATSAPP NOW!"
echo "=================================================="
echo "Watching for WhatsApp notifications..."
echo "Press Ctrl+C to stop"
echo ""

adb logcat -c
adb logcat | grep --line-buffered -E "WhatsAppListener|🔔|📱|✅|❌|💾|⚠️|Timber"
