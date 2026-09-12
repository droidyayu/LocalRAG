package com.ayushig.localrag.di

import com.ayushig.localrag.data.FakeEchoLlmRepository
import com.ayushig.localrag.domain.repository.LlmRepository
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
}
