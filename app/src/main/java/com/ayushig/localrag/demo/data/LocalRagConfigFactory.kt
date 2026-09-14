package com.ayushig.localrag.demo.data

import android.content.Context
import com.ayushig.localrag.android.LocalRag
import com.ayushig.localrag.demo.domain.model.ModelOption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the engine config for a model option, and resolves which option is live: the
 * stored preference when that file is present, else the first present model, else E2B
 * (whose missing-model banner then says how to push it).
 */
@Singleton
class LocalRagConfigFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locator: ModelFileLocator,
    private val selection: ModelSelectionStore,
) {
    fun selected(): ModelOption =
        ModelOption.resolve(locator.present(), selection.selected())

    fun create(option: ModelOption = selected()): LocalRag.Config =
        LocalRag.Config(
            cacheDir = context.cacheDir,
            appVersion = "4.5",
            topK = 4,
            generationModelPath = locator.fileFor(option).absolutePath.takeIf {
                locator.isPresent(option)
            },
        )
}
