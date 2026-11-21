package com.ayushig.localrag.core.parity

import com.ayushig.localrag.core.document.Chunk
import com.ayushig.localrag.core.document.Chunker
import com.ayushig.localrag.core.document.FrontMatterParser
import com.ayushig.localrag.core.index.Bm25Index
import com.ayushig.localrag.core.text.Tokenizer
import kotlin.math.roundToInt

/**
 * The cross-platform parity corpus: tricky Unicode inputs for the tokenizer, Markdown documents
 * for the chunker, and queries for BM25 ranking.
 *
 * Non-ASCII text is written with unicode escapes so the file is encoding-proof; each escaped
 * line names the character it exercises. One input per trap, so a platform mismatch names the
 * trap by its case index.
 */
object ParityCases {

    /** At least one input per trap in the brief; order is the case index used in failures. */
    val tokenInputs: List<String> = listOf(
        "Caf\u00E9 Cr\u00E8me", // French accents: e-acute, e-grave
        "caf\u00E9", // NFC precomposed e-acute
        "cafe\u0301", // NFD e plus combining acute
        "Stra\u00DFe", // German sharp s
        "STRA\u1E9EE", // German capital sharp s
        "\u0130stanbul", // Turkish dotted capital I
        "I\u0131", // Turkish capital I plus dotless i
        "\u039F\u0394\u039F\u03A3", // Greek uppercase ODOS
        "\u03BF\u03B4\u03CC\u03C2", // Greek lowercase odos with final sigma
        "\u0916\u093E\u0924\u093E", // Devanagari khata with vowel signs
        "\u0930\u0941\u092A\u092F\u0947", // Devanagari rupaye with vowel signs
        "\u20B9500", // rupee sign plus digits
        "\u20AC9.99", // euro sign plus decimal
        "\u00A312", // pound sign plus digits
        "don\u2019t", // curly apostrophe
        "\u201Cquoted\u201D", // curly double quotes
        "2020\u20132021", // en dash range
        "word\u2014word", // em dash join
        "wait\u2026", // ellipsis
        "a\u00A0b", // NBSP separator
        "a\u202Fb", // narrow NBSP separator
        "a\u3000b", // ideographic space separator
        "a\u200Db", // zero-width joiner inside word
        "a\u200Bb", // zero-width space inside word
        "\uD83D\uDCB0 money", // emoji money bag plus word
        "\uD83D\uDCB0", // emoji alone
        "a\uD835\uDC00b", // supplementary-plane letter between ASCII
        "\uFF11\uFF12\uFF13", // fullwidth digits
        "\u0663\u0664", // Arabic-Indic digits
        "\u6295\u8D44 fund", // CJK plus word
        "\u0414\u0435\u043D\u044C\u0433\u0438", // Cyrillic dengi
        "x\u00B2", // superscript two
        "\u00BD price", // fraction one half
        "R&D 5%", // kept ampersand and percent
        "The AND Of", // stopwords in mixed case
        "", // empty string
        "   ", // whitespace only
        "a".repeat(5000), // 5,000-character repeated word
        "1,234.50", // digits with separators
        "a\tb\nc", // tabs and newlines
        "a\r\nb", // CR LF
        "&", // lone ampersand
        "%", // lone percent
        "hello_world", // underscore splits
        "e-mail", // hyphen splits
    )

    /**
     * Full Markdown documents with front matter in the frozen-corpus shape. Every document must
     * parse with zero issues; the generator asserts that on the JVM.
     */
    val documents: List<String> = listOf(
        DOCUMENT_CAFE,
        DOCUMENT_DEVANAGARI,
        DOCUMENT_EMOJI,
        DOCUMENT_SLUGS,
        DOCUMENT_CRLF,
        DOCUMENT_MARKDOWN,
        DOCUMENT_CJK,
        DOCUMENT_FEES,
    )

    /** Queries against the corpus above; NFC/NFD pair, uppercase, stopwords, emoji, rupee, ties. */
    val queries: List<String> = listOf(
        "caf\u00E9", // NFC query word
        "cafe\u0301", // NFD same word
        "CAF\u00C9", // uppercase Unicode query with E-acute
        "the and of", // only stopwords
        "\uD83D\uDCB0", // emoji query
        "\u20B9500", // rupee-number query
        "fund", // hits several chunks, ties likely
        "money", // hits several chunks, ties likely
        "fees", // hits several chunks, ties likely
        "\u0916\u093E\u0924\u093E", // Devanagari query
        "\u6295\u8D44", // CJK query
        "\u0414\u0435\u043D\u044C\u0433\u0438", // Cyrillic query
        "stra\u00DFe", // German sharp s query
        "quoted", // curly-quote word in ASCII
        "refund", // alias-weighted term
        "coffee", // alias term
        "code fund", // multi-term query
        "wise quote", // blockquote words
        "R&D", // ampersand query
        "5%", // percent query
        "nonexistentword xyzzy", // no hits anywhere
        "x\u00B2 \u00BD", // superscript plus fraction query
    )

    private const val DOCUMENT_CAFE =
        "---\n" +
            "id: parity-cafe\n" +
            "title: Caf\u00E9 prices\n" + // e-acute in title
            "category: parity\n" +
            "aliases: [coffee, latte]\n" +
            "screen: app://cafe/menu\n" +
            "last_reviewed: 2026-08-01\n" +
            "status: published\n" +
            "---\n" +
            "## Caf\u00E9 menu\n" + // accented heading
            "The Caf\u00E9 serves Cr\u00E8me br\u00FBl\u00E9e for \u20AC9.99.\n" + // e-acute, e-grave, u-circumflex, euro
            "## Cr\u00E8me specials\n" + // accented heading
            "Try the seasonal cake and don\u2019t miss the R&D blend.\n" // curly apostrophe

    private const val DOCUMENT_DEVANAGARI =
        "---\n" +
            "id: parity-devanagari\n" +
            "title: \u0916\u093E\u0924\u093E help\n" + // Devanagari in title
            "category: parity\n" +
            "aliases: [account, khata]\n" +
            "screen: app://account/help\n" +
            "last_reviewed: 2026-08-02\n" +
            "status: published\n" +
            "---\n" +
            "## \u0916\u093E\u0924\u093E \u0936\u0947\u0937\n" + // Devanagari heading
            "Your \u0916\u093E\u0924\u093E shows \u0930\u0941\u092A\u092F\u0947 deposits of \u20B9500 each.\n" + // Devanagari body, rupee
            "## \u0930\u0941\u092A\u092F\u0947 refunds\n" + // Devanagari heading
            "Refunds post in 2 days. Fees are \u20B950 or \u20B9100.\n" // rupee amounts

    private const val DOCUMENT_EMOJI =
        "---\n" +
            "id: parity-emoji\n" +
            "title: Emoji guide\n" +
            "category: parity\n" +
            "aliases: [emoji, symbols]\n" +
            "screen: app://help/emoji\n" +
            "last_reviewed: 2026-08-03\n" +
            "status: published\n" +
            "---\n" +
            "## \uD83D\uDCB0 Money basics\n" + // emoji money bag heading
            "Save money every week. The fund grows.\n" +
            "## \u20B9 \u20B9\n" + // symbols-only heading, rupee signs
            "Prices in \u20B9 and \u20AC9.99 terms.\n" + // rupee and euro body
            "## \uD83C\uDF89 Party fund\n" + // emoji party popper heading
            "Celebrate with the team fund. Money jokes welcome.\n"

    private const val DOCUMENT_SLUGS =
        "---\n" +
            "id: parity-slugs\n" +
            "title: Money moves\n" +
            "category: parity\n" +
            "aliases: [money, deposit]\n" +
            "screen: app://money/moves\n" +
            "last_reviewed: 2026-08-04\n" +
            "status: published\n" +
            "---\n" +
            "## Money In\n" +
            "Deposit money into the fund today.\n" +
            "## Money in!\n" + // duplicate slug of the previous heading
            "A second section whose slug matches the first one.\n" +
            "## VERY Long Heading About Absolutely Everything Concerning Fees And Funds And Money Today Indeed\n" + // very long mixed-case heading
            "This section has a very long heading with mixed case words.\n" +
            "## Trailing spaces   \n" + // trailing spaces on the heading line
            "Body under a heading with trailing spaces.\n"

    private const val DOCUMENT_CRLF =
        "---\r\n" + // CRLF line endings throughout this document
            "id: parity-crlf\r\n" +
            "title: Code fund\r\n" +
            "category: parity\r\n" +
            "aliases: [code]\r\n" +
            "screen: app://fund/code\r\n" +
            "last_reviewed: 2026-08-05\r\n" +
            "status: published\r\n" +
            "---\r\n" +
            "## Code fund\r\n" +
            "Put the fund code in a fence:\r\n" +
            "```\r\n" + // fenced code block
            "fund = money + fees\r\n" +
            "```\r\n" +
            "\r\n" +
            "## Trailing CRLF   \r\n" + // trailing spaces before CRLF
            "Ends with spaces before the line break.\r\n"

    private const val DOCUMENT_MARKDOWN =
        "---\n" +
            "id: parity-markdown\n" +
            "title: Markdown features\n" +
            "category: parity\n" +
            "aliases: [markdown, formatting]\n" +
            "screen: app://help/markdown\n" +
            "last_reviewed: 2026-08-06\n" +
            "status: published\n" +
            "---\n" +
            "## Links and images\n" +
            "Read the [fee guide](app://fees) and see ![chart](app://img/fees.png) for details.\n" + // link and image
            "## Lists and quotes\n" +
            "> A wise quote about money and funds.\n" + // block quote
            "- apples\n" + // list markers
            "* oranges\n" +
            "+ bananas\n" +
            "1. first step\n" +
            "2. second step\n" +
            "Some `inline code` and **bold funds** with _emphasis_.\n"

    private const val DOCUMENT_CJK =
        "---\n" +
            "id: parity-cjk\n" +
            "title: \u6295\u8D44 funds\n" + // CJK in title
            "category: parity\n" +
            "aliases: [asia, growth]\n" +
            "screen: app://funds/asia\n" +
            "last_reviewed: 2026-08-07\n" +
            "status: published\n" +
            "---\n" +
            "## \u6295\u8D44 Fund\n" + // CJK heading
            "The \u6295\u8D44 fund holds \uFF11\uFF12\uFF13 units and \u0663\u0664 bonus points.\n" + // CJK body, fullwidth and Arabic-Indic digits
            "## \u0414\u0435\u043D\u044C\u0433\u0438 details\n" + // Cyrillic heading
            "\u0414\u0435\u043D\u044C\u0433\u0438 grow when fees stay low. Cyrillic money terms.\n" // Cyrillic body

    private const val DOCUMENT_FEES =
        "---\n" +
            "id: parity-fees\n" +
            "title: Fees and refunds\n" +
            "category: parity\n" +
            "aliases: [fees, refund, charges]\n" +
            "screen: app://fees/schedule\n" +
            "last_reviewed: 2026-08-08\n" +
            "status: published\n" +
            "---\n" +
            "## Fee schedule\n" +
            "Standard fees are flat. Every fund pays the same fee.\n" +
            "## Refunds 2020\u20132021\n" + // en dash in heading
            "Refunds from 2020\u20132021 \u2014 processed fast \u2014 use the \u201Cquoted\u201D form.\n" + // en dash, em dashes, curly quotes
            "Wait\u2026 the fund\u2019s fee table lists 1,234.50 as the cap.\n" // ellipsis, curly apostrophe
}

/** Everything ParityTest compares; the generator writes these same values to ParityExpected. */
data class ParityResult(
    val tokens: List<List<String>>,
    val chunkIds: List<List<String>>,
    val parseIssueCounts: List<Int>,
    val chunkIssueCounts: List<Int>,
    val rankingIds: List<List<String>>,
    val rankingScores: List<List<Int>>,
)

/**
 * Runs the real commonMain pipeline: Tokenizer over the token inputs, FrontMatterParser plus
 * Chunker over the documents, then one Bm25Index over all chunks searched per query.
 */
object ParityRun {

    fun compute(): ParityResult {
        val tokens = ParityCases.tokenInputs.map { Tokenizer.tokenize(it) }

        val chunker = Chunker()
        val parsed = ParityCases.documents.mapIndexed { index, content ->
            FrontMatterParser.parse("parity/doc$index.md", content)
        }
        val chunkIds = mutableListOf<List<String>>()
        val parseIssueCounts = mutableListOf<Int>()
        val chunkIssueCounts = mutableListOf<Int>()
        val allChunks = mutableListOf<Chunk>()
        for (result in parsed) {
            parseIssueCounts.add(result.issues.size)
            val document = result.document
            if (document == null) {
                chunkIds.add(emptyList())
                chunkIssueCounts.add(0)
                continue
            }
            val chunked = chunker.chunk(document)
            chunkIds.add(chunked.chunks.map { it.chunkId })
            chunkIssueCounts.add(chunked.issues.size)
            allChunks.addAll(chunked.chunks)
        }

        val index = Bm25Index.build(allChunks)
        val rankingIds = mutableListOf<List<String>>()
        val rankingScores = mutableListOf<List<Int>>()
        for (query in ParityCases.queries) {
            val hits = index.search(Tokenizer.tokenize(query), 10)
            rankingIds.add(hits.map { allChunks[it.chunkIndex].chunkId })
            rankingScores.add(hits.map { (it.score * 10000).roundToInt() })
        }

        return ParityResult(
            tokens = tokens,
            chunkIds = chunkIds,
            parseIssueCounts = parseIssueCounts,
            chunkIssueCounts = chunkIssueCounts,
            rankingIds = rankingIds,
            rankingScores = rankingScores,
        )
    }
}
