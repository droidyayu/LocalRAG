package com.ayushig.localrag.demo.domain.model

/**
 * The generation models the assistant can run. Small model, same grammar: the 270M answers
 * from the same tools and the same gate, just faster and weaker. Both are gated Hub repos,
 * pushed with scripts/push_model.sh — never bundled, never committed.
 */
enum class ModelOption(
    /** File name under the app's external files dir; must match what was pushed. */
    val fileName: String,
    val displayName: String,
    val shortName: String,
    val hfRepo: String,
    /** Human download size, shown before pushing and in the missing-model banner. */
    val approxSize: String,
    /** push_model.sh refuses anything smaller: a gated-repo miss downloads an HTML page. */
    val minBytes: Long,
) {
    E2B(
        fileName = "gemma-4-E2B-it.litertlm",
        displayName = "Gemma 4 E2B IT",
        shortName = "Gemma 4 E2B",
        hfRepo = "litert-community/gemma-4-E2B-it-litert-lm",
        approxSize = "2.5 GB",
        minBytes = 1_073_741_824L,
    ),
    GEMMA_270M(
        fileName = "gemma3-270m-it-q8.litertlm",
        displayName = "Gemma 3 270M IT",
        shortName = "Gemma 3 270M",
        hfRepo = "litert-community/gemma-3-270m-it",
        approxSize = "304 MB",
        minBytes = 262_144_000L,
    );

    companion object {
        /**
         * The preferred option when present, else the first present one, else E2B (which the
         * missing-model banner then explains how to push). Never null: the UI always has a
         * selected model, even when none is on device.
         */
        fun resolve(present: Set<ModelOption>, preferred: ModelOption?): ModelOption =
            preferred?.takeIf { it in present }
                ?: entries.firstOrNull { it in present }
                ?: E2B
    }
}
