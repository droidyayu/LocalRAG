package com.ayushig.localrag.demo.data

import android.content.Context
import com.ayushig.localrag.demo.domain.model.ModelOption
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves where .litertlm model files are expected on device. Paths are never hardcoded:
 * they derive from [Context.getExternalFilesDir], which maps to
 * /sdcard/Android/data/<applicationId>/files and is adb-writable without root.
 */
@Singleton
class ModelFileLocator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun fileFor(option: ModelOption): File =
        File(context.getExternalFilesDir(null), option.fileName)

    /** A model is only usable if it exists and is not a zero-byte or truncated download. */
    fun isPresent(option: ModelOption): Boolean =
        fileFor(option).let { it.isFile && it.length() > 0L }

    fun present(): Set<ModelOption> =
        ModelOption.entries.filterTo(mutableSetOf(), ::isPresent)
}
