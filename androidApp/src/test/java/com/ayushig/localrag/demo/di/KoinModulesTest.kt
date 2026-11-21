package com.ayushig.localrag.demo.di

import com.ayushig.localrag.AgentConfig
import com.ayushig.localrag.demo.data.LocalRagConfigFactory
import com.ayushig.localrag.demo.data.assistant.AGENT_SYSTEM_PROMPT
import com.ayushig.localrag.demo.data.assistant.AssistantToolDefinitions
import com.ayushig.localrag.demo.data.assistant.DocumentationContext
import com.ayushig.localrag.demo.domain.model.ModelOption
import com.ayushig.localrag.demo.domain.repository.PortfolioRepository
import com.ayushig.localrag.demo.domain.usecase.portfolio.FindHoldingBySymbolUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetCategorySummaryUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetLeveragedPositionsUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetMarginStatusUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetMetalHoldingsUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetPortfolioSummaryUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetStockHoldingsUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetWealthInvestmentsUseCase
import com.ayushig.localrag.demo.platform.AppInfo
import com.ayushig.localrag.demo.platform.ModelFiles
import com.ayushig.localrag.demo.platform.ModelSelection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Guards the Koin graph on the JVM, where no [android.content.Context] exists.
 *
 * The fakes below stand in for the Android-bound services ([ModelFiles], [ModelSelection],
 * [AppInfo]) and for [DocumentationContext], whose real definition reads the [LocalRag]
 * engine — and the engine itself needs a Context to be built. The real DocumentationContext
 * and the [LocalRag] binding are verified by the on-device smoke run instead.
 */
class KoinModulesTest {

    private class FakeFiles(
        private val present: Set<ModelOption> = setOf(ModelOption.E2B),
    ) : ModelFiles {
        override fun pathFor(option: ModelOption) = "/models/${option.fileName}"
        override fun isPresent(option: ModelOption) = option in present
    }

    private class FakeSelection(
        private var chosen: ModelOption? = null,
    ) : ModelSelection {
        override fun selected() = chosen
        override fun select(option: ModelOption) {
            chosen = option
        }
    }

    private val koinApp = koinApplication {
        allowOverride(true)
        modules(
            sharedModule,
            module {
                single<ModelFiles> { FakeFiles() }
                single<ModelSelection> { FakeSelection() }
                single {
                    AppInfo(
                        litertlmVersion = "0.17.0",
                        appVersion = "4.5",
                        cacheDir = "/cache",
                    )
                }
                single { DocumentationContext { emptyList() } }
            },
        )
    }

    @After
    fun tearDown() {
        koinApp.close()
    }

    @Test
    fun sharedGraphResolvesWithoutLocalRag() {
        val koin = koinApp.koin

        koin.get<PortfolioRepository>()
        koin.get<FindHoldingBySymbolUseCase>()
        koin.get<GetCategorySummaryUseCase>()
        koin.get<GetLeveragedPositionsUseCase>()
        koin.get<GetMarginStatusUseCase>()
        koin.get<GetMetalHoldingsUseCase>()
        koin.get<GetPortfolioSummaryUseCase>()
        koin.get<GetStockHoldingsUseCase>()
        koin.get<GetWealthInvestmentsUseCase>()
        koin.get<AssistantToolDefinitions>()

        val agentConfig = koin.get<AgentConfig>()
        assertEquals(AGENT_SYSTEM_PROMPT, agentConfig.systemPrompt)
        assertEquals(4, agentConfig.tools.size)

        val factory = koin.get<LocalRagConfigFactory>()
        assertEquals(ModelOption.E2B, factory.selected())
        val config = factory.create()
        assertEquals("/cache", config.cacheDir)
        assertEquals("4.5", config.appVersion)
        assertEquals(4, config.topK)
    }
}
