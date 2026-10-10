package com.summarizer.app.domain.model

import kotlin.math.roundToInt

/** Which on-device model fits a phone, decided from its memory alone (pure, unit-tested). */
object ModelRecommendation {

    /**
     * Total RAM in whole GB as advertised. `ActivityManager.MemoryInfo.totalMem` excludes memory
     * reserved by the kernel, so rounding the decimal value lands on the advertised size
     * (e.g. ~7.9e9 bytes on an "8 GB" phone, ~5.6e9 on a "6 GB" one).
     */
    fun ramGb(totalMemBytes: Long): Int = (totalMemBytes / 1_000_000_000.0).roundToInt()

    /**
     * The model onboarding downloads: the catalog's recommended model when the phone has the RAM
     * it needs (Gemma 4 E2B, 6 GB), else the downloadable model that needs the least RAM (the
     * small CPU model). Null when nothing fits; the user then picks in the model manager.
     * Models without a download (the debug Demo) are never picked.
     */
    fun pick(catalog: List<AIModel>, deviceRamGb: Int): AIModel? {
        val fitting = catalog.filter { it.downloadUrl.isNotBlank() && it.minimumRAM <= deviceRamGb }
        return fitting.firstOrNull { it.isRecommended } ?: fitting.minByOrNull { it.minimumRAM }
    }
}
