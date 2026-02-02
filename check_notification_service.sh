#!/bin/bash

echo "=========================================="
echo "Notification Listener Service Diagnostics"
echo "=========================================="
echo ""

# Check if notification listener permission is granted
echo "1. Checking notification listener permission..."
LISTENERS=$(adb shell cmd notification allow_listener 2>/dev/null | grep "com.summarizer.app")
if [ -z "$LISTENERS" ]; then
    echo "❌ Permission NOT granted!"
    echo ""
    echo "Attempting to grant permission via ADB..."
    adb shell cmd notification allow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener
    sleep 2
    LISTENERS=$(adb shell cmd notification allow_listener 2>/dev/null | grep "com.summarizer.app")
    if [ -z "$LISTENERS" ]; then
        echo "❌ Still not granted. You MUST grant it manually:"
        echo "   Settings → Apps → Special app access → Notification access → Summarizer"
        exit 1
    else
        echo "✅ Permission granted via ADB"
    fi
else
    echo "✅ Permission is granted:"
    echo "   $LISTENERS"
fi
echo ""

# Check if service is running
echo "2. Checking if NotificationListenerService is running..."
SERVICE=$(adb shell dumpsys activity services | grep -A 3 "WhatsAppNotificationListener")
if [ -z "$SERVICE" ]; then
    echo "⚠️  Service NOT found in running services"
else
    echo "✅ Service found:"
    echo "$SERVICE"
fi
echo ""

# Force rebind the service
echo "3. Force rebinding notification listener..."
adb shell cmd notification disallow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener 2>/dev/null
sleep 1
adb shell cmd notification allow_listener com.summarizer.app/com.summarizer.app.service.WhatsAppNotificationListener
sleep 2
echo "✅ Rebind command sent"
echo ""

# Restart the app
echo "4. Restarting app..."
adb shell am force-stop com.summarizer.app
sleep 2
adb shell am start -n com.summarizer.app/.MainActivity
sleep 3
echo "✅ App restarted"
echo ""

# Check logcat for connection messages
echo "5. Checking logcat for service connection (5 seconds)..."
timeout 5 adb logcat -d | grep -E "WhatsAppListener|🟢|🔴|NotificationListenerService" | tail -20
echo ""

echo "=========================================="
echo "If you see '🟢 NotificationListenerService CONNECTED' above, it's working!"
echo "If not, check the output for clues."
echo ""
echo "Now monitoring live logs. Send a WhatsApp message..."
echo "Press Ctrl+C to stop"
echo "=========================================="
echo ""

adb logcat -c
adb logcat | grep -E "WhatsAppListener|🟢|🔴|NotificationListenerService|Summarizer"
