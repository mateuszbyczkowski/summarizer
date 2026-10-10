package com.summarizer.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelSetupStateTest {

    private val model = AIModel(id = "m", name = "M (CPU)", description = "", sizeInMB = 730, downloadUrl = "https://h/m.gguf")

    private fun download(status: DownloadStatus, failure: DownloadFailure? = null) =
        ModelDownloadState("m", status, progress = 0.25f, downloadedBytes = 10, totalBytes = 40, failure = failure)

    private fun stopReason(status: DownloadStatus, failure: DownloadFailure? = null) =
        (ModelSetupState.of(model, download(status, failure)) as ModelSetupState.Stopped).reason

    @Test
    fun `a running download shows its progress`() {
        assertEquals(
            ModelSetupState.Downloading(model, 0.25f, 10, 40),
            ModelSetupState.of(model, download(DownloadStatus.DOWNLOADING))
        )
    }

    @Test
    fun `the card goes away once the model is downloaded, done or cancelled`() {
        assertNull(ModelSetupState.of(model.copy(isDownloaded = true), download(DownloadStatus.DOWNLOADING)))
        assertNull(ModelSetupState.of(model, download(DownloadStatus.COMPLETED)))
        assertNull(ModelSetupState.of(model, download(DownloadStatus.NOT_STARTED)))
    }

    @Test
    fun `stopped downloads say why`() {
        assertEquals(SetupStopReason.PAUSED, stopReason(DownloadStatus.PAUSED))
        assertEquals(SetupStopReason.WIFI_REQUIRED, stopReason(DownloadStatus.FAILED, DownloadFailure.WIFI_REQUIRED))
        assertEquals(SetupStopReason.NO_SPACE, stopReason(DownloadStatus.FAILED, DownloadFailure.NO_SPACE))
        assertEquals(SetupStopReason.FAILED, stopReason(DownloadStatus.FAILED, DownloadFailure.NETWORK))
        assertEquals(SetupStopReason.FAILED, stopReason(DownloadStatus.FAILED))
    }

    @Test
    fun `no download in this process means it was interrupted`() {
        assertEquals(ModelSetupState.Stopped(model, SetupStopReason.INTERRUPTED), ModelSetupState.of(model, null))
    }
}
