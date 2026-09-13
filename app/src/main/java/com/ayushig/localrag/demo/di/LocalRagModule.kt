package com.ayushig.localrag.demo.di

import android.content.Context
import com.ayushig.localrag.android.LocalRag
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
     * No model paths: this is the zero-model configuration, which retrieves with BM25 and answers
     * with the best passage verbatim. It is the state every device supports.
     */
    @Provides
    @Singleton
    fun provideLocalRag(@ApplicationContext context: Context): LocalRag = LocalRag.create(
        context = context,
        config = LocalRag.Config(
            cacheDir = context.cacheDir,
            appVersion = "4.5",
            topK = 4,
        ),
    )
}
