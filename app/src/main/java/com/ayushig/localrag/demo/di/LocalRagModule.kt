package com.ayushig.localrag.demo.di

import android.content.Context
import com.ayushig.localrag.android.LocalRag
import com.ayushig.localrag.demo.data.LocalRagConfigFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalRagModule {

    /**
     * Generation is used when the selected model has been pushed to the device, and simply
     * absent when it has not. Retrieval stays BM25 either way until an embedding model is
     * supplied; the Ready state flags say which of the two configurations is live.
     * Model switches go through LocalRag.updateConfig, never a new instance.
     */
    @Provides
    @Singleton
    fun provideLocalRag(
        @ApplicationContext context: Context,
        configFactory: LocalRagConfigFactory,
    ): LocalRag = LocalRag.create(
        context = context,
        config = configFactory.create(),
    )
}
