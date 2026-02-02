package com.summarizer.app.util

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber

/**
 * Monitors the health of the NotificationListenerService
 * Detects when the service is enabled but not receiving notifications (common on MIUI)
 */
object ServiceHealthMonitor {

    private const val PREFS_NAME = "service_health"
    private const val KEY_LAST_NOTIFICATION_TIME = "last_notification_time"
    private const val KEY_SERVICE_CONNECTED_TIME = "service_connected_time"
    private const val KEY_WARNING_DISMISSED = "warning_dismissed_v1"

    /**
     * Call this when the service receives a notification
     */
    fun recordNotificationReceived(context: Context) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_NOTIFICATION_TIME, System.currentTimeMillis())
            .apply()
        Timber.d("ServiceHealthMonitor: Notification received and recorded")
    }

    /**
     * Call this when the service connects
     */
    fun recordServiceConnected(context: Context) {
        getPrefs(context).edit()
            .putLong(KEY_SERVICE_CONNECTED_TIME, System.currentTimeMillis())
            .apply()
        Timber.d("ServiceHealthMonitor: Service connection recorded")
    }

    /**
     * Check if the service appears to be broken (enabled but not receiving notifications)
     * Returns true if:
     * - Service is enabled in settings
     * - Service connected at least 2 minutes ago
     * - No notifications received in the last 5 minutes
     * - User hasn't dismissed the warning
     */
    fun isServiceBroken(context: Context): Boolean {
        val prefs = getPrefs(context)

        // If user dismissed the warning, don't show it again
        if (prefs.getBoolean(KEY_WARNING_DISMISSED, false)) {
            return false
        }

        // Check if service is enabled in settings
        val isEnabled = PermissionHelper.isNotificationServiceEnabled(context)
        if (!isEnabled) {
            Timber.d("ServiceHealthMonitor: Service not enabled, not broken")
            return false
        }

        val serviceConnectedTime = prefs.getLong(KEY_SERVICE_CONNECTED_TIME, 0)
        val lastNotificationTime = prefs.getLong(KEY_LAST_NOTIFICATION_TIME, 0)
        val currentTime = System.currentTimeMillis()

        // If service never connected, it's not broken (just not started yet)
        if (serviceConnectedTime == 0L) {
            Timber.d("ServiceHealthMonitor: Service never connected")
            return false
        }

        // If service connected less than 2 minutes ago, give it time
        val timeSinceConnection = currentTime - serviceConnectedTime
        if (timeSinceConnection < 2 * 60 * 1000) {
            Timber.d("ServiceHealthMonitor: Service recently connected, giving it time")
            return false
        }

        // If we've never received a notification AND service has been connected for a while,
        // it's likely broken (AutoStart issue)
        if (lastNotificationTime == 0L && timeSinceConnection > 5 * 60 * 1000) {
            Timber.w("ServiceHealthMonitor: Service enabled for ${timeSinceConnection / 1000}s but never received notifications")
            return true
        }

        // If we haven't received a notification in 30 minutes AND service has been connected,
        // it might be broken
        val timeSinceLastNotification = currentTime - lastNotificationTime
        if (lastNotificationTime > 0 && timeSinceLastNotification > 30 * 60 * 1000) {
            Timber.w("ServiceHealthMonitor: Last notification was ${timeSinceLastNotification / 1000}s ago")
            // Don't mark as broken if we received notifications before - might just be no WhatsApp activity
            return false
        }

        Timber.d("ServiceHealthMonitor: Service appears healthy")
        return false
    }

    /**
     * Mark the warning as dismissed by the user
     */
    fun dismissWarning(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_WARNING_DISMISSED, true)
            .apply()
    }

    /**
     * Reset the warning (for testing or after user fixes the issue)
     */
    fun resetWarning(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_WARNING_DISMISSED, false)
            .apply()
    }

    /**
     * Get time since last notification in milliseconds
     */
    fun getTimeSinceLastNotification(context: Context): Long {
        val lastNotificationTime = getPrefs(context).getLong(KEY_LAST_NOTIFICATION_TIME, 0)
        if (lastNotificationTime == 0L) return -1
        return System.currentTimeMillis() - lastNotificationTime
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
