package com.ayushig.localrag.demo.platform

import com.ayushig.localrag.demo.domain.model.ModelOption
import platform.Foundation.NSUserDefaults

/** Which generation model the assistant runs. Framework defaults, no new dependency. */
class IosModelSelection(
    suiteName: String? = null,
) : ModelSelection {
    private val defaults: NSUserDefaults =
        if (suiteName != null) NSUserDefaults(suiteName = suiteName)
        else NSUserDefaults.standardUserDefaults

    override fun selected(): ModelOption? =
        defaults.stringForKey(KEY)?.let { name ->
            runCatching { ModelOption.valueOf(name) }.getOrNull()
        }

    override fun select(option: ModelOption) {
        defaults.setObject(option.name, forKey = KEY)
    }

    private companion object {
        const val KEY = "selected_model"
    }
}
