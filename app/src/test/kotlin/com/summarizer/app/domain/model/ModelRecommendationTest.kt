package com.summarizer.app.domain.model

import com.summarizer.app.data.ai.DemoEngine
import com.summarizer.app.data.download.ModelCatalog
import com.summarizer.app.ui.screens.models.ModelProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelRecommendationTest {

    private val catalog = ModelCatalog.DEFAULT_MODELS

    @Test
    fun `advertised RAM is rounded from what the phone reports`() {
        assertEquals(8, ModelRecommendation.ramGb(7_900_000_000L))
        assertEquals(6, ModelRecommendation.ramGb(5_600_000_000L))
        assertEquals(4, ModelRecommendation.ramGb(4_106_825_728L)) // emulator, 4 GB
        assertEquals(3, ModelRecommendation.ramGb(2_800_000_000L))
    }

    @Test
    fun `phones with enough RAM get Gemma 4 E2B, never the larger E4B`() {
        assertEquals(ModelProfiles.GEMMA_E2B_ID, ModelRecommendation.pick(catalog, 6)?.id)
        assertEquals(ModelProfiles.GEMMA_E2B_ID, ModelRecommendation.pick(catalog, 8)?.id)
        assertEquals(ModelProfiles.GEMMA_E2B_ID, ModelRecommendation.pick(catalog, 12)?.id)
    }

    @Test
    fun `phones with less RAM get the small CPU model`() {
        assertEquals(ModelProfiles.LFM_ID, ModelRecommendation.pick(catalog, 5)?.id)
        assertEquals(ModelProfiles.LFM_ID, ModelRecommendation.pick(catalog, 4)?.id)
        assertEquals(ModelProfiles.LFM_ID, ModelRecommendation.pick(catalog, 3)?.id)
    }

    @Test
    fun `nothing is picked when no model fits`() {
        assertNull(ModelRecommendation.pick(catalog, 2))
    }

    @Test
    fun `the demo model is never picked, it has nothing to download`() {
        val demoFirst = listOf(DemoEngine.MODEL.copy(isRecommended = true)) + catalog
        assertEquals(ModelProfiles.LFM_ID, ModelRecommendation.pick(demoFirst, 4)?.id)
        assertNull(ModelRecommendation.pick(listOf(DemoEngine.MODEL), 16))
    }
}
