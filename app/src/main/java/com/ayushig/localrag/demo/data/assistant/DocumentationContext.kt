package com.ayushig.localrag.demo.data.assistant

import com.ayushig.localrag.android.Passage

/**
 * Documentation the app pre-searches for each question, replacing the old
 * search_documentation tool call.
 *
 * A tool round costs a full model generation (tens of seconds on CPU) to decide
 * something retrieval already knows in milliseconds — and the model sometimes
 * decided wrong and never searched at all. So every turn carries its passages
 * along: greetings naturally match nothing and inject nothing, while anything
 * with a hit arrives as evidence with no round trip.
 *
 * No score threshold on purpose: measured top scores for genuine documentation
 * questions (5.3 and up) overlap portfolio questions (3.1 to 5.7), so any floor
 * either pollutes or starves. The cap is count and characters instead, and the
 * prompt tells the model to ignore passages that do not answer the question.
 */
/**
 * Built by hand in AppModule like the tool definitions: the search function is not
 * something Dagger can provide as a binding.
 */
class DocumentationContext(
    private val search: suspend (String) -> List<Passage>,
) {
    suspend fun forQuery(query: String): List<Passage> = select(search(query))

    fun select(passages: List<Passage>): List<Passage> {
        // The top passage always rides along: a follow-up without evidence is
        // unanswerable, while a slightly long context is merely expensive.
        val kept = mutableListOf<Passage>()
        var chars = 0
        for (passage in passages.take(MAX_PASSAGES)) {
            val block = passage.title.length + passage.text.length
            if (kept.isNotEmpty() && chars + block > MAX_CHARS) break
            kept.add(passage)
            chars += block
        }
        return kept
    }

    companion object {
        const val MAX_PASSAGES = 2
        const val MAX_CHARS = 1200
    }
}
