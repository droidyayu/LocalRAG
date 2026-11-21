package com.ayushig.localrag.demo.data

import com.ayushig.localrag.demo.domain.model.ModelOption
import com.ayushig.localrag.demo.platform.AppInfo
import com.ayushig.localrag.demo.platform.ModelFiles
import com.ayushig.localrag.demo.platform.ModelSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalRagConfigFactoryTest {

    private class FakeFiles(private val present: Set<ModelOption>) : ModelFiles {
        override fun pathFor(option: ModelOption) = "/models/${option.fileName}"

        override fun isPresent(option: ModelOption) = option in present
    }

    private class FakeSelection(private var chosen: ModelOption?) : ModelSelection {
        override fun selected() = chosen

        override fun select(option: ModelOption) {
            chosen = option
        }
    }

    private val info = AppInfo(litertlmVersion = "0.17.0", appVersion = "4.5", cacheDir = "/cache")

    private fun factory(present: Set<ModelOption>, chosen: ModelOption? = null) =
        LocalRagConfigFactory(FakeFiles(present), FakeSelection(chosen), info)

    @Test
    fun theStoredPreferenceWinsWhenItsFileIsPresent() {
        val factory = factory(setOf(ModelOption.E2B, ModelOption.GEMMA_270M), ModelOption.GEMMA_270M)

        assertEquals(ModelOption.GEMMA_270M, factory.selected())
    }

    @Test
    fun aPreferenceWhoseFileIsMissingFallsBackToAPresentModel() {
        val factory = factory(setOf(ModelOption.GEMMA_270M), ModelOption.E2B)

        assertEquals(ModelOption.GEMMA_270M, factory.selected())
    }

    @Test
    fun withNoModelOnDeviceTheDefaultIsE2B() {
        assertEquals(ModelOption.E2B, factory(emptySet()).selected())
    }

    @Test
    fun theConfigCarriesThePathOnlyWhenTheFileIsPresent() {
        val present = factory(setOf(ModelOption.E2B))
        assertEquals("/models/gemma-4-E2B-it.litertlm", present.create(ModelOption.E2B).generationModelPath)
        assertNull(present.create(ModelOption.GEMMA_270M).generationModelPath)
    }

    @Test
    fun theConfigUsesTheAppInfoForCacheAndVersion() {
        val config = factory(setOf(ModelOption.E2B)).create()

        assertEquals("/cache", config.cacheDir)
        assertEquals("4.5", config.appVersion)
        assertEquals(4, config.topK)
    }
}
