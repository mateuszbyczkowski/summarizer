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
            val externalDir = context.getExternalFilesDir(null) ?: return null
            val stat = StatFs(externalDir.path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes

            StorageInfo(
                location = StorageLocation.EXTERNAL,
                path = externalDir.absolutePath,
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
        // Require 10% buffer on top of required space
        val requiredWithBuffer = requiredMB * 1.1
        return availableMB >= requiredWithBuffer
    }

    fun getModelStorageDirectory(context: Context, location: StorageLocation): File {
        val baseDir = when (location) {
            StorageLocation.INTERNAL -> context.filesDir
            StorageLocation.EXTERNAL -> context.getExternalFilesDir(null) ?: context.filesDir
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
