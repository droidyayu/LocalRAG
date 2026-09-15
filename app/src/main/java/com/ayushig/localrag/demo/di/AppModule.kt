package com.ayushig.localrag.demo.di

import com.ayushig.localrag.android.AgentConfig
import com.ayushig.localrag.android.LocalRag
import com.ayushig.localrag.demo.data.assistant.AGENT_SYSTEM_PROMPT
import com.ayushig.localrag.demo.data.assistant.AssistantToolDefinitions
import com.ayushig.localrag.demo.data.assistant.DocumentationContext
import com.ayushig.localrag.demo.data.repository.FakePortfolioRepository
import com.ayushig.localrag.demo.domain.repository.PortfolioRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Hardcoded portfolio data. Swapped for a real source once one exists.
    @Provides
    @Singleton
    fun bindPortfolioRepository(impl: FakePortfolioRepository): PortfolioRepository = impl

    /**
     * The agent's tools, built by hand: Dagger only sees the concrete repository and the
     * finished object. Documentation is pre-searched per question rather than a tool call,
     * so the tools below are portfolio functions only.
     */
    @Provides
    @Singleton
    fun provideAssistantToolDefinitions(
        repository: PortfolioRepository,
    ): AssistantToolDefinitions = AssistantToolDefinitions(repository)

    /**
     * Pre-searched documentation for each question, built by hand for the same reason:
     * the search function is not something Dagger can provide as a binding.
     */
    @Provides
    @Singleton
    fun provideDocumentationContext(localRag: LocalRag): DocumentationContext =
        DocumentationContext { query -> localRag.retrieveOnly(query) }

    /**
     * Everything the SDK agent loop needs from the host app: prompt copy plus the tools above.
     * The SDK owns the grammar, the rounds, and the output gate, but no words.
     */
    @Provides
    @Singleton
    fun provideAgentConfig(definitions: AssistantToolDefinitions): AgentConfig =
        AgentConfig(
            systemPrompt = AGENT_SYSTEM_PROMPT,
            tools = definitions.list(),
        )
}
