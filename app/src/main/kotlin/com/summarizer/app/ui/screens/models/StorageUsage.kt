package com.summarizer.app.ui.screens.models

import com.summarizer.app.util.StorageHelper

/**
 * Storage where new models are saved, split into models / everything else / free.
 *
 * @property modelsMB Size of the model files (including partial downloads) in that location
 * @property externalAvailable Whether an SD card can be chosen instead
 */
data class StorageUsage(
    val location: StorageHelper.StorageLocation,
    val totalMB: Long,
    val freeMB: Long,
    val modelsMB: Long,
    val externalAvailable: Boolean
) {
    /** Everything that is neither a model nor free space. */
    val otherMB: Long get() = (totalMB - freeMB - modelsMB).coerceAtLeast(0)

    /** Shares of the usage bar (models, other, free); they add up to 1, or are all 0 when unknown. */
    fun fractions(): Triple<Float, Float, Float> {
        if (totalMB <= 0) return Triple(0f, 0f, 0f)
        val models = modelsMB.coerceIn(0, totalMB).toFloat() / totalMB
        val free = freeMB.coerceIn(0, totalMB).toFloat() / totalMB
        val other = (1f - models - free).coerceAtLeast(0f)
        return Triple(models, other, free)
    }
}

/** Typical time per summary as a short label value: seconds below 90 s, whole minutes above. */
sealed interface TypicalTime {
    data class Seconds(val value: Int) : TypicalTime
    data class Minutes(val value: Int) : TypicalTime

    companion object {
        fun of(seconds: Int): TypicalTime =
            if (seconds < 90) Seconds(seconds) else Minutes((seconds + 30) / 60)
    }
}
