package com.ayushig.localrag.demo.di

import com.ayushig.localrag.LocalRag
import com.ayushig.localrag.demo.data.LocalRagConfigFactory
import com.ayushig.localrag.demo.platform.AppInfo
import com.ayushig.localrag.demo.platform.ModelFiles
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.koin.core.context.stopKoin

/** The iOS Koin graph resolves without a model backend: retrieval-only is a supported state. */
class KoinIosTest {

    @AfterTest
    fun stop() = stopKoin()

    @Test
    fun theGraphResolvesTheEngineTheConfigFactoryAndTheAppInfo() {
        val koin = initKoinIos(litertlmVersion = "0.17.0", appVersion = "4.5", generationBackend = null, embeddingBackend = null)

        assertNotNull(koin.get<LocalRag>())
        assertNotNull(koin.get<LocalRagConfigFactory>())
        val info = koin.get<AppInfo>()
        assertEquals("0.17.0", info.litertlmVersion)
        assertEquals("4.5", info.appVersion)
        assertTrue(info.cacheDir.isNotEmpty())
        assertTrue(koin.get<ModelFiles>().present().isEmpty())
    }
}
