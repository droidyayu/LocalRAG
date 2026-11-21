package com.ayushig.localrag.demo.di

import com.ayushig.localrag.LocalRag
import com.ayushig.localrag.create
import com.ayushig.localrag.demo.BuildConfig
import com.ayushig.localrag.demo.data.LocalRagConfigFactory
import com.ayushig.localrag.demo.platform.AndroidModelFiles
import com.ayushig.localrag.demo.platform.AndroidModelSelection
import com.ayushig.localrag.demo.platform.AppInfo
import com.ayushig.localrag.demo.platform.ModelFiles
import com.ayushig.localrag.demo.platform.ModelSelection
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android Koin graph: platform bindings plus the engine and the ViewModels.
 * The shared graph holds everything else.
 */
val androidModule: Module = module {
    single<ModelFiles> { AndroidModelFiles(androidContext()) }
    single<ModelSelection> { AndroidModelSelection(androidContext()) }
    single {
        AppInfo(
            litertlmVersion = BuildConfig.LITERTLM_VERSION,
            appVersion = "4.5",
            cacheDir = androidContext().cacheDir.path,
        )
    }

    /**
     * Generation is used when the selected model has been pushed to the device, and simply
     * absent when it has not. Retrieval stays BM25 either way until an embedding model is
     * supplied; the Ready state flags say which of the two configurations is live.
     * Model switches go through LocalRag.updateConfig, never a new instance.
     */
    single<LocalRag> {
        LocalRag.create(
            context = androidContext(),
            config = get<LocalRagConfigFactory>().create(),
        )
    }
}
