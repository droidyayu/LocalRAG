package com.ayushig.localrag.demo.debug

import com.ayushig.localrag.demo.domain.model.ModelOption
import com.ayushig.localrag.demo.platform.ModelFiles

/**
 * Reports every model as present at path "scripted", so in scripted mode the UI neither shows
 * the missing-model banner nor blocks sending. No file is ever read from that path: the scripted
 * backend answers without a model file.
 */
class ScriptedModelFiles : ModelFiles {
    override fun pathFor(option: ModelOption): String = "scripted"

    override fun isPresent(option: ModelOption): Boolean = true
}
