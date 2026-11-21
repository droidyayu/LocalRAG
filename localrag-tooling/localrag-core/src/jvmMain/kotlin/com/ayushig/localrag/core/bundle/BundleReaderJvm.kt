package com.ayushig.localrag.core.bundle

import java.io.InputStream

fun BundleReader.read(input: InputStream): Bundle = read(input.readBytes())
