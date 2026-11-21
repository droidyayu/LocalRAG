package com.ayushig.localrag.demo.platform

import android.content.Context
import com.ayushig.localrag.demo.domain.model.ModelOption
import java.io.File

/**
 * Resolves where .litertlm model files are expected on device. Paths are never hardcoded:
 * they derive from [Context.getExternalFilesDir], which maps to
 * /sdcard/Android/data/<applicationId>/files and is adb-writable without root.
 */
class AndroidModelFiles(context: Context) : ModelFiles {
    private val appContext = context.applicationContext

    override fun pathFor(option: ModelOption): String =
        File(appContext.getExternalFilesDir(null), option.fileName).absolutePath

    /** A model is only usable if it exists and is not a zero-byte or truncated download. */
    override fun isPresent(option: ModelOption): Boolean {
        val file = File(pathFor(option))
        return file.isFile && file.length() > 0L
    }
}
