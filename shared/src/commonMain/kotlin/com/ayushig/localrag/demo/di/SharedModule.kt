package com.ayushig.localrag.demo.di

import com.ayushig.localrag.AgentConfig
import com.ayushig.localrag.LocalRag
import com.ayushig.localrag.demo.data.LocalRagConfigFactory
import com.ayushig.localrag.demo.data.assistant.AGENT_SYSTEM_PROMPT
import com.ayushig.localrag.demo.data.assistant.AssistantToolDefinitions
import com.ayushig.localrag.demo.data.assistant.DocumentationContext
import com.ayushig.localrag.demo.data.repository.FakePortfolioRepository
import com.ayushig.localrag.demo.domain.repository.PortfolioRepository
import com.ayushig.localrag.demo.domain.usecase.portfolio.FindHoldingBySymbolUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetCategorySummaryUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetLeveragedPositionsUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetMarginStatusUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetMetalHoldingsUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetPortfolioSummaryUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetStockHoldingsUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetWealthInvestmentsUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Shared Koin graph: everything the demo needs that is not platform-bound.
 * Platform bindings ([ModelFiles], [ModelSelection], [AppInfo], [LocalRag] itself)
 * live in the Android module; iOS will provide its own.
 */
val sharedModule: Module = module {
    // Hardcoded portfolio data. Swapped for a real source once one exists.
    single<PortfolioRepository> { FakePortfolioRepository() }

    // The use cases are plain Kotlin; each is constructed by hand from the repository.
    factory { FindHoldingBySymbolUseCase(get()) }
    factory { GetCategorySummaryUseCase(get()) }
    factory { GetLeveragedPositionsUseCase(get()) }
    factory { GetMarginStatusUseCase(get()) }
    factory { GetMetalHoldingsUseCase(get()) }
    factory { GetPortfolioSummaryUseCase(get()) }
    factory { GetStockHoldingsUseCase(get()) }
    factory { GetWealthInvestmentsUseCase(get()) }

    // The agent's tools: portfolio functions only. Documentation is pre-searched per
    // question rather than a tool call, so the tools below are portfolio functions only.
    single { AssistantToolDefinitions(get()) }

    // Pre-searched documentation for each question. The search function is not something
    // the graph can provide as a binding, so it reads the engine through Koin instead.
    single { DocumentationContext { query -> get<LocalRag>().retrieveOnly(query) } }

    // Everything the SDK agent loop needs from the host app: prompt copy plus the tools.
    // The SDK owns the grammar, the rounds, and the output gate, but no words.
    single {
        AgentConfig(
            systemPrompt = AGENT_SYSTEM_PROMPT,
            tools = get<AssistantToolDefinitions>().list(),
        )
    }

    single { LocalRagConfigFactory(get(), get(), get()) }
}
