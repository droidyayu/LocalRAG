package com.ayushig.localrag.core.bundle

import kotlinx.serialization.json.Json

object BundleJson {
    val format: Json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
        // A BM25-only bundle must have no embedding block at all, not a null one: the runtime
        // treats the block as a parity contract, and an explicit null invites a reader to think
        // one was intended.
        explicitNulls = false
    }
}
