package com.ayushig.localrag.demo.di

import com.ayushig.localrag.EmbeddingBackend
import com.ayushig.localrag.GenerationBackend
import com.ayushig.localrag.LocalRag
import com.ayushig.localrag.create
import com.ayushig.localrag.demo.data.LocalRagConfigFactory
import com.ayushig.localrag.demo.debug.ScriptedGenerationBackend
import com.ayushig.localrag.demo.debug.ScriptedModelFiles
import com.ayushig.localrag.demo.debug.launchEngineMode
import com.ayushig.localrag.demo.debug.scriptedDelayMillis
import com.ayushig.localrag.demo.platform.AppInfo
import com.ayushig.localrag.demo.platform.IosModelFiles
import com.ayushig.localrag.demo.platform.IosModelSelection
import com.ayushig.localrag.demo.platform.ModelFiles
import com.ayushig.localrag.demo.platform.ModelSelection
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS Koin graph: platform bindings plus the engine. The shared graph holds everything else.
 *
 * Generation is used when the selected model has been pushed to the device, and simply
 * absent when it has not: a null backend means that capability is unavailable, the load
 * policy degrades gracefully, and retrieval stays BM25-only. Model switches go through
 * LocalRag.updateConfig, never a new instance.
 */
fun iosPlatformModule(
    appInfo: AppInfo,
    generationBackend: GenerationBackend?,
    embeddingBackend: EmbeddingBackend?,
): Module = module {
    single<ModelFiles> {
        if (scriptedDelayMillis(launchEngineMode()) != null) ScriptedModelFiles()
        else IosModelFiles()
    }
    single<ModelSelection> { IosModelSelection() }
    single { appInfo }

    single<LocalRag> {
        val delay = scriptedDelayMillis(launchEngineMode())
        val config = get<LocalRagConfigFactory>().create()
        if (delay != null) {
            LocalRag.create(
                config = config.copy(generationModelPath = "scripted"),
                generationBackend = ScriptedGenerationBackend(slowMillis = delay),
                embeddingBackend = embeddingBackend,
            )
        } else {
            LocalRag.create(
                config = config,
                generationBackend = generationBackend,
                embeddingBackend = embeddingBackend,
            )
        }
    }
}

/**
 * Starts the shared Koin graph for the iOS app. Call once at app start (for example from
 * the Swift app delegate) before resolving anything through Koin; the returned [Koin]
 * instance is the shared graph. The Swift host supplies the LiteRT-LM backends, or nil
 * when a capability is unavailable.
 */
@OptIn(ExperimentalForeignApi::class)
fun initKoinIos(
    litertlmVersion: String,
    appVersion: String,
    generationBackend: GenerationBackend?,
    embeddingBackend: EmbeddingBackend?,
): Koin {
    val cacheDir = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true,
    ).first() as String
    val appInfo = AppInfo(
        litertlmVersion = litertlmVersion,
        appVersion = appVersion,
        cacheDir = cacheDir,
    )
    return startKoin {
        modules(sharedModule, iosPlatformModule(appInfo, generationBackend, embeddingBackend))
    }.koin
}
