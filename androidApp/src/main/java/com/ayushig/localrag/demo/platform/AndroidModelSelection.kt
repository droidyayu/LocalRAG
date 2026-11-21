package com.ayushig.localrag.demo.platform

import android.content.Context
import com.ayushig.localrag.demo.domain.model.ModelOption

/** Which generation model the assistant runs. Framework prefs, no new dependency. */
class AndroidModelSelection(context: Context) : ModelSelection {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun selected(): ModelOption? =
        prefs.getString(KEY, null)?.let { name ->
            runCatching { ModelOption.valueOf(name) }.getOrNull()
        }

    override fun select(option: ModelOption) {
        prefs.edit().putString(KEY, option.name).apply()
    }

    private companion object {
        const val PREFS = "localrag_models"
        const val KEY = "selected_model"
    }
}
