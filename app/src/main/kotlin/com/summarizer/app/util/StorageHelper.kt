package com.summarizer.app.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

object StorageHelper {

    enum class StorageLocation {
        INTERNAL,
        EXTERNAL
    }

    data class StorageInfo(
        val location: StorageLocation,
        val path: String,
        val totalSpaceMB: Long,
        val availableSpaceMB: Long,
        val usedSpaceMB: Long
    ) {
        val availableSpaceGB: Double get() = availableSpaceMB / 1024.0
        val totalSpaceGB: Double get() = totalSpaceMB / 1024.0
        val usedPercent: Int get() = ((usedSpaceMB.toDouble() / totalSpaceMB) * 100).toInt()
    }

    fun getInternalStorageInfo(context: Context): StorageInfo {
        val filesDir = context.filesDir
        val stat = StatFs(filesDir.path)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = totalBytes - availableBytes

        return StorageInfo(
            location = StorageLocation.INTERNAL,
            path = filesDir.absolutePath,
            totalSpaceMB = totalBytes / (1024 * 1024),
            availableSpaceMB = availableBytes / (1024 * 1024),
            usedSpaceMB = usedBytes / (1024 * 1024)
        )
    }

    fun getExternalStorageInfo(context: Context): StorageInfo? {
        return if (isExternalStorageAvailable()) {
            // getExternalFilesDirs returns array: [0] = primary (usually internal), [1+] = removable SD card
            val externalDirs = context.getExternalFilesDirs(null)

            // Check if there's actual removable storage (index 1 or higher)
            val removableDir = if (externalDirs.size > 1 && externalDirs[1] != null) {
                externalDirs[1]
            } else {
                // No removable storage found
                return null
            }

            val stat = StatFs(removableDir!!.path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes

            StorageInfo(
                location = StorageLocation.EXTERNAL,
                path = removableDir.absolutePath,
                totalSpaceMB = totalBytes / (1024 * 1024),
                availableSpaceMB = availableBytes / (1024 * 1024),
                usedSpaceMB = usedBytes / (1024 * 1024)
            )
        } else {
            null
        }
    }

    fun isExternalStorageAvailable(): Boolean {
        val state = Environment.getExternalStorageState()
        return state == Environment.MEDIA_MOUNTED
    }

    fun hasEnoughSpace(requiredMB: Long, availableMB: Long): Boolean {
        // Require 5% buffer on top of required space (reduced from 10% for large model files)
        val requiredWithBuffer = requiredMB * 1.05
        return availableMB >= requiredWithBuffer
    }

    fun getModelStorageDirectory(context: Context, location: StorageLocation): File {
        val baseDir = when (location) {
            StorageLocation.INTERNAL -> context.filesDir
            StorageLocation.EXTERNAL -> {
                // Get removable storage (SD card) if available
                val externalDirs = context.getExternalFilesDirs(null)
                if (externalDirs.size > 1 && externalDirs[1] != null) {
                    externalDirs[1]!!
                } else {
                    // Fallback to internal if no removable storage
                    context.filesDir
                }
            }
        }
        val modelDir = File(baseDir, "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        return modelDir
    }

    fun formatStorageSize(mb: Long): String {
        return when {
            mb < 1024 -> "${mb} MB"
            else -> String.format("%.1f GB", mb / 1024.0)
        }
    }

    fun formatStorageSizeGB(gb: Double): String {
        return String.format("%.1f GB", gb)
    }
}
