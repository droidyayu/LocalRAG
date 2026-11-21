package com.ayushig.localrag.demo.data

import com.ayushig.localrag.LocalRag
import com.ayushig.localrag.demo.domain.model.ModelOption
import com.ayushig.localrag.demo.platform.AppInfo
import com.ayushig.localrag.demo.platform.ModelFiles
import com.ayushig.localrag.demo.platform.ModelSelection

/**
 * Builds the engine config for a model option, and resolves which option is live: the
 * stored preference when that file is present, else the first present model, else E2B
 * (whose missing-model banner then says how to push it).
 */
class LocalRagConfigFactory(
    private val modelFiles: ModelFiles,
    private val selection: ModelSelection,
    private val appInfo: AppInfo,
) {
    fun selected(): ModelOption =
        ModelOption.resolve(modelFiles.present(), selection.selected())

    fun create(option: ModelOption = selected()): LocalRag.Config =
        LocalRag.Config(
            cacheDir = appInfo.cacheDir,
            appVersion = appInfo.appVersion,
            topK = 4,
            generationModelPath = modelFiles.pathFor(option).takeIf {
                modelFiles.isPresent(option)
            },
        )
}
