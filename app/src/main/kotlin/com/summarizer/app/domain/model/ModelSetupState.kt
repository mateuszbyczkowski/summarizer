package com.summarizer.app.domain.model

/** Why the onboarding model download is not running. */
enum class SetupStopReason {
    /** Paused in the model manager. */
    PAUSED,

    /** No download is running, e.g. the app was closed by the system while it ran. */
    INTERRUPTED,
    WIFI_REQUIRED,
    NO_SPACE,
    FAILED
}

/**
 * The model onboarding picked and downloads in the background, as Home shows it. Gone (null)
 * once the model is downloaded or the download was cancelled.
 */
sealed interface ModelSetupState {
    val model: AIModel

    /** @property totalBytes Size reported by the server; 0 until known */
    data class Downloading(
        override val model: AIModel,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : ModelSetupState

    data class Stopped(override val model: AIModel, val reason: SetupStopReason) : ModelSetupState

    companion object {
        /**
         * What Home shows for [model] given its download state (null when this process has not
         * downloaded it). Pure, so it can be unit tested.
         */
        fun of(model: AIModel, download: ModelDownloadState?): ModelSetupState? = when {
            model.isDownloaded -> null
            download == null -> Stopped(model, SetupStopReason.INTERRUPTED)
            else -> when (download.status) {
                DownloadStatus.DOWNLOADING -> Downloading(
                    model = model,
                    progress = download.progress.coerceIn(0f, 1f),
                    downloadedBytes = download.downloadedBytes,
                    totalBytes = download.totalBytes
                )
                DownloadStatus.PAUSED -> Stopped(model, SetupStopReason.PAUSED)
                DownloadStatus.FAILED -> Stopped(
                    model,
                    when (download.failure) {
                        DownloadFailure.WIFI_REQUIRED -> SetupStopReason.WIFI_REQUIRED
                        DownloadFailure.NO_SPACE -> SetupStopReason.NO_SPACE
                        DownloadFailure.NETWORK, DownloadFailure.OTHER, null -> SetupStopReason.FAILED
                    }
                )
                // Done (the model is about to show as downloaded) or cancelled
                DownloadStatus.COMPLETED, DownloadStatus.NOT_STARTED -> null
            }
        }
    }
}
