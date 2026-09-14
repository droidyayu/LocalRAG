package com.ayushig.localrag.demo.data

import android.content.Context
import com.ayushig.localrag.demo.domain.model.ModelOption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Which generation model the assistant runs. Framework prefs, no new dependency. */
@Singleton
class ModelSelectionStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selected(): ModelOption? =
        prefs.getString(KEY, null)?.let { name ->
            runCatching { ModelOption.valueOf(name) }.getOrNull()
        }

    fun select(option: ModelOption) {
        prefs.edit().putString(KEY, option.name).apply()
    }

    private companion object {
        const val PREFS = "localrag_models"
        const val KEY = "selected_model"
    }
}
