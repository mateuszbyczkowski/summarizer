package com.summarizer.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import timber.log.Timber

object PermissionHelper {

    /**
     * Check if the app has notification listener permission
     */
    fun hasNotificationListenerPermission(context: Context): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }

    /**
     * Open the notification listener settings screen
     */
    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Check if notification listener service is enabled for this app
     */
    fun isNotificationServiceEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        if (flat.isNullOrEmpty()) {
            return false
        }
        val names = flat.split(":")
        return names.any {
            val componentName = ComponentName.unflattenFromString(it)
            componentName?.packageName == packageName
        }
    }

    /**
     * Detect if the device is running MIUI, ColorOS, FunTouch, or other restrictive Chinese ROMs
     */
    fun isRestrictiveROM(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        return when {
            // Xiaomi devices (MIUI)
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            manufacturer.contains("redmi") || brand.contains("redmi") -> true

            // OPPO devices (ColorOS)
            manufacturer.contains("oppo") || brand.contains("oppo") -> true

            // Vivo devices (FunTouch OS)
            manufacturer.contains("vivo") || brand.contains("vivo") -> true

            // OnePlus devices (OxygenOS - sometimes restrictive)
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> true

            // Realme devices
            manufacturer.contains("realme") || brand.contains("realme") -> true

            // Huawei/Honor devices (EMUI)
            manufacturer.contains("huawei") || brand.contains("huawei") ||
            manufacturer.contains("honor") || brand.contains("honor") -> true

            else -> false
        }
    }

    /**
     * Get ROM type name for display
     */
    fun getROMType(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        return when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            manufacturer.contains("redmi") || brand.contains("redmi") -> "MIUI"
            manufacturer.contains("oppo") || brand.contains("oppo") -> "ColorOS"
            manufacturer.contains("vivo") || brand.contains("vivo") -> "FunTouch OS"
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> "OxygenOS"
            manufacturer.contains("realme") || brand.contains("realme") -> "Realme UI"
            manufacturer.contains("huawei") || brand.contains("huawei") ||
            manufacturer.contains("honor") || brand.contains("honor") -> "EMUI"
            else -> "Android"
        }
    }

    /**
     * Try to open AutoStart/Battery settings for restrictive ROMs
     * Returns true if successfully opened, false if couldn't find the right intent
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()

        return try {
            val intent = when {
                // Xiaomi/MIUI
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                // OPPO/ColorOS
                manufacturer.contains("oppo") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                // Vivo/FunTouch
                manufacturer.contains("vivo") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                // OnePlus
                manufacturer.contains("oneplus") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.oneplus.security",
                            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                // Huawei/Honor
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                else -> null
            }

            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                // Fallback: open general app settings
                openAppSettings(context)
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open AutoStart settings")
            // Fallback: open general app settings
            openAppSettings(context)
            false
        }
    }

    /**
     * Open the app's general settings page
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open app settings")
        }
    }

    /**
     * Get user-friendly instructions for enabling AutoStart on their device
     */
    fun getAutoStartInstructions(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                "Settings → Apps → Manage apps → ThreadSummarizer → Autostart → Enable"

            manufacturer.contains("oppo") ->
                "Settings → Apps → App Management → ThreadSummarizer → Allow autostart"

            manufacturer.contains("vivo") ->
                "Settings → Battery → Background activity management → ThreadSummarizer → Enable"

            manufacturer.contains("oneplus") ->
                "Settings → Apps → ThreadSummarizer → Battery → Battery optimization → Don't optimize"

            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "Settings → Apps → ThreadSummarizer → Battery → App launch → Manage manually → Enable all"

            else ->
                "Settings → Apps → ThreadSummarizer → Enable all background permissions"
        }
    }
}
