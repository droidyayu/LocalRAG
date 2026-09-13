package com.ayushig.localrag.demo.di

import android.content.Context
import com.ayushig.localrag.android.LocalRag
import com.ayushig.localrag.demo.data.ModelFileLocator
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
     * Generation is used when the model has been pushed to the device, and simply absent when it
     * has not. Retrieval stays BM25 either way until an embedding model is supplied, so this
     * covers two of the four degradation states depending on what is on disk.
     */
    @Provides
    @Singleton
    fun provideLocalRag(
        @ApplicationContext context: Context,
        modelFileLocator: ModelFileLocator,
    ): LocalRag = LocalRag.create(
        context = context,
        config = LocalRag.Config(
            cacheDir = context.cacheDir,
            appVersion = "4.5",
            topK = 4,
            generationModelPath = modelFileLocator.absolutePath.takeIf {
                modelFileLocator.isPresent()
            },
        ),
    )
}
