package com.ayushig.localrag.demo.di

import com.ayushig.localrag.demo.data.FakeEchoLlmRepository
import com.ayushig.localrag.demo.data.assistant.KeywordIntentResolver
import com.ayushig.localrag.demo.data.assistant.TemplatePortfolioAnswerRenderer
import com.ayushig.localrag.demo.data.repository.FakePortfolioRepository
import com.ayushig.localrag.demo.domain.assistant.IntentResolver
import com.ayushig.localrag.demo.domain.assistant.PortfolioAnswerRenderer
import com.ayushig.localrag.demo.domain.repository.LlmRepository
import com.ayushig.localrag.demo.domain.repository.PortfolioRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    // Phase 1 wiring. Phase 3 swaps this binding for the LiteRT-LM backed repository.
    @Binds
    @Singleton
    abstract fun bindLlmRepository(impl: FakeEchoLlmRepository): LlmRepository

    // Hardcoded portfolio data. Swapped for a real source once one exists.
    @Binds
    @Singleton
    abstract fun bindPortfolioRepository(impl: FakePortfolioRepository): PortfolioRepository

    @Binds
    @Singleton
    abstract fun bindIntentResolver(impl: KeywordIntentResolver): IntentResolver

    @Binds
    @Singleton
    abstract fun bindPortfolioAnswerRenderer(
        impl: TemplatePortfolioAnswerRenderer,
    ): PortfolioAnswerRenderer
}
