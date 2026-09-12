package com.ayushig.localrag.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves where the .litertlm model file is expected on device. The path is never hardcoded:
 * it is derived from [Context.getExternalFilesDir], which maps to
 * /sdcard/Android/data/<applicationId>/files and is adb-writable without root.
 */
@Singleton
class ModelFileLocator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val modelFile: File
        get() = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)

    val absolutePath: String
        get() = modelFile.absolutePath

    /** The model is only usable if it exists and is not a zero-byte or truncated download. */
    fun isPresent(): Boolean = modelFile.isFile && modelFile.length() > 0L

    companion object {
        const val MODEL_FILE_NAME: String = "gemma3-270m-it-q8.litertlm"
    }
}
