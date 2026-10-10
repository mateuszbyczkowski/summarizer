package com.summarizer.app.util

import android.app.ActivityManager
import android.content.Context
import com.summarizer.app.domain.model.ModelRecommendation

object DeviceMemory {

    /** Total device RAM in whole GB as advertised, see [ModelRecommendation.ramGb]. */
    fun totalRamGb(context: Context): Int {
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
        return ModelRecommendation.ramGb(memoryInfo.totalMem)
    }
}
