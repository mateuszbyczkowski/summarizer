package com.summarizer.app.ui.screens.models

import com.summarizer.app.R
import com.summarizer.app.data.ai.DemoEngine
import com.summarizer.app.data.download.ModelCatalog
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.util.StorageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProfilesTest {

    private val catalog = ModelCatalog.DEFAULT_MODELS

    private fun model(id: String) = catalog.first { it.id == id }

    @Test
    fun `catalog models get plain names, timings and backends`() {
        val e2b = ModelProfiles.profileOf(model(ModelProfiles.GEMMA_E2B_ID))
        assertEquals(R.string.engine_plain_recommended, e2b.plainName)
        assertEquals(ModelBackend.GPU, e2b.backend)
        assertEquals(40, e2b.typicalSeconds)

        assertEquals(R.string.engine_plain_best_quality, ModelProfiles.profileOf(model(ModelProfiles.GEMMA_E4B_ID)).plainName)

        val lfm = ModelProfiles.profileOf(model(ModelProfiles.LFM_ID))
        assertEquals(R.string.engine_plain_backup_cpu, lfm.plainName)
        assertEquals(ModelBackend.CPU, lfm.backend)

        val demo = ModelProfiles.profileOf(DemoEngine.MODEL)
        assertEquals(R.string.engine_plain_demo, demo.plainName)
        assertEquals(ModelBackend.DEMO, demo.backend)
    }

    @Test
    fun `every catalog id has a profile`() {
        catalog.forEach { assertTrue(it.id, ModelProfiles.profileOf(it).plainName != null) }
    }

    @Test
    fun `unknown models fall back to the technical name and the file type`() {
        val unknown = AIModel(id = "x", name = "Other (GPU)", description = "", sizeInMB = 1, downloadUrl = "https://h/m.litertlm")
        val profile = ModelProfiles.profileOf(unknown)
        assertNull(profile.plainName)
        assertNull(profile.typicalSeconds)
        assertEquals(ModelBackend.GPU, profile.backend)
        assertEquals(ModelBackend.CPU, ModelProfiles.backendFromUrl("https://h/m.gguf"))
    }

    @Test
    fun `technical name drops the backend in parentheses`() {
        assertEquals("Gemma 4 E2B", ModelProfiles.technicalName(model(ModelProfiles.GEMMA_E2B_ID)))
        assertEquals("Gemma 4 E4B", ModelProfiles.technicalName("Gemma 4 E4B (GPU, Quality)"))
        assertEquals("Demo", ModelProfiles.technicalName(DemoEngine.MODEL))
    }

    @Test
    fun `models are listed best default first and demo last`() {
        val shuffled = listOf(DemoEngine.MODEL) + catalog.reversed()
        assertEquals(
            listOf(ModelProfiles.GEMMA_E2B_ID, ModelProfiles.GEMMA_E4B_ID, ModelProfiles.LFM_ID, DemoEngine.MODEL_ID),
            ModelProfiles.sorted(shuffled).map { it.id }
        )
    }

    @Test
    fun `models needing more RAM are disabled unless already downloaded`() {
        val e4b = model(ModelProfiles.GEMMA_E4B_ID)
        assertTrue(ModelProfiles.needsMoreRam(e4b, deviceRamGb = 6))
        assertFalse(ModelProfiles.needsMoreRam(e4b, deviceRamGb = 8))
        assertFalse(ModelProfiles.needsMoreRam(e4b.copy(isDownloaded = true), deviceRamGb = 6))
    }

    @Test
    fun `storage bar shares add up and other space is never negative`() {
        val usage = StorageUsage(StorageHelper.StorageLocation.INTERNAL, totalMB = 1000, freeMB = 400, modelsMB = 100, externalAvailable = false)
        val (models, other, free) = usage.fractions()
        assertEquals(0.1f, models, 0.0001f)
        assertEquals(0.5f, other, 0.0001f)
        assertEquals(0.4f, free, 0.0001f)
        assertEquals(500, usage.otherMB)

        val odd = usage.copy(freeMB = 950, modelsMB = 100)
        assertEquals(0, odd.otherMB)
        assertEquals(0f, odd.fractions().second, 0.0001f)
        assertEquals(Triple(0f, 0f, 0f), usage.copy(totalMB = 0).fractions())
    }

    @Test
    fun `typical time is shown in seconds below 90 s and in minutes above`() {
        assertEquals(TypicalTime.Seconds(40), TypicalTime.of(40))
        assertEquals(TypicalTime.Minutes(2), TypicalTime.of(120))
        assertEquals(TypicalTime.Minutes(2), TypicalTime.of(90))
    }
}
