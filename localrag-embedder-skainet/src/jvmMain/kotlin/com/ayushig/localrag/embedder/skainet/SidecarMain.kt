package com.ayushig.localrag.embedder.skainet

import kotlin.system.exitProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The LocalRAG embedding sidecar protocol, same as tools/embed: one JSON object per line on
 * stdin carrying the text, one JSON array of floats per line on stdout, in the same order.
 *
 * This main exists so EmbedChunksTask needs no change at all: the plugin shells out exactly as it
 * does for the Python scripts, but what it reaches is the same compiled embedder the runtime uses.
 */
fun main(args: Array<String>) {
    val dimensions = argValue(args, "--dimensions")?.toIntOrNull()
        ?: SkaiNetHashedProjectionEmbedder.DEFAULT_DIMENSIONS
    if (dimensions <= 0) {
        System.err.println("skainet-embed: --dimensions must be a positive integer")
        exitProcess(1)
    }

    // The protocol owns stdout: every non-blank line must be a JSON array. SKaiNET logs a CPU
    // capability banner to System.out on startup, so the real stream is claimed here and the
    // JVM-wide one is pointed at nothing before the first tensor op can print.
    val protocolOut = java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out))
    System.setOut(java.io.PrintStream(java.io.OutputStream.nullOutputStream()))

    val json = Json { ignoreUnknownKeys = true }
    val out = protocolOut.bufferedWriter()
    try {
        SkaiNetHashedProjectionEmbedder(dimensions).use { embedder ->
            System.`in`.bufferedReader().forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val text = json.parseToJsonElement(line).jsonObject
                    .getValue("text").jsonPrimitive.content
                val vector = embedder.embed(text)
                out.write(vector.joinToString(prefix = "[", postfix = "]", separator = ","))
                out.newLine()
            }
        }
        out.flush()
    } catch (failure: Exception) {
        System.err.println("skainet-embed: ${failure.message}")
        exitProcess(1)
    }
}

private fun argValue(args: Array<String>, name: String): String? {
    val index = args.indexOf(name)
    return if (index >= 0 && index + 1 < args.size) args[index + 1] else null
}
