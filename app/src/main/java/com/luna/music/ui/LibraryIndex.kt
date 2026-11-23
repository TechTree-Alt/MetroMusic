package com.luna.music.ui

/**
 * Which letter a name is filed under, and how a typed query meets a name — for every alphabet the
 * app has an index for: Latin, Cyrillic and Korean.
 *
 * The framework's own `metroGroupChar` knows the first two and sweeps everything else into '#'. For a
 * Korean library that is the whole library under one heading, which is exactly what an index is meant
 * to prevent. Hangul is filed the way a Korean phone book and every Korean phone's contacts list file
 * it: by the syllable's **initial consonant**, ㄱ to ㅎ — fourteen buckets, with the tense doubles
 * (ㄲ ㄸ ㅃ ㅆ ㅉ) folded into the plain consonant they are written with, so 까치 sits beside 가수 rather
 * than in a bucket of its own that nobody would look in.
 *
 * Plain Kotlin with nothing from Android or Compose in it, so the rules are checked by unit tests
 * rather than by scrolling through somebody's library.
 */
object LibraryIndex {

    /** The heading for names that start with no letter of any alphabet here: digits, symbols. */
    const val Other = "#"

    /** The nineteen initial consonants, in the order Unicode composes syllables from them. */
    private const val Initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

    /** The fourteen index buckets: [Initials] with the tense doubles folded away. */
    val HangulBuckets: List<String> = "ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ".map { it.toString() }

    val LatinBuckets: List<String> = ('a'..'z').map { it.toString() }

    val CyrillicBuckets: List<String> = ('а'..'я').map { it.toString() }

    private const val SyllableFirst = 0xAC00
    private const val SyllableLast = 0xD7A3
    private const val SyllablesPerInitial = 21 * 28

    /** Compatibility jamo — what a Korean keyboard types when a consonant stands on its own. */
    private const val CompatFirst = 0x3131
    private const val CompatLast = 0x314E

    /** Conjoining initial consonants, as an NFD-decomposed name arrives. */
    private const val ChoseongFirst = 0x1100
    private const val ChoseongLast = 0x1112

    /** The plain consonant each tense double is filed with. */
    private val Folded = mapOf('ㄲ' to 'ㄱ', 'ㄸ' to 'ㄷ', 'ㅃ' to 'ㅂ', 'ㅆ' to 'ㅅ', 'ㅉ' to 'ㅈ')

    /**
     * The initial consonant of a Hangul character, as a compatibility jamo (ㄱ, ㄲ, …), or null for
     * anything that is not Hangul. A lone vowel (ㅏ) is Hangul with no consonant, and is null too.
     */
    fun initialOf(c: Char): Char? {
        val code = c.code
        return when (code) {
            in SyllableFirst..SyllableLast -> Initials[(code - SyllableFirst) / SyllablesPerInitial]
            in ChoseongFirst..ChoseongLast -> Initials[code - ChoseongFirst]
            in CompatFirst..CompatLast -> c.takeIf { it in Initials }
            else -> null
        }
    }

    /**
     * The index heading [text] is filed under: a lowercase Latin or Cyrillic letter, a Hangul initial
     * consonant, or [Other]. Always a single character, which is what makes the jump grid lay the
     * headings out as the phone's squares rather than as a column of words.
     */
    fun bucketOf(text: String): String {
        val c = text.trim().firstOrNull() ?: return Other
        initialOf(c)?.let { return (Folded[it] ?: it).toString() }
        val upper = c.uppercaseChar()
        return when {
            upper in 'A'..'Z' -> upper.lowercaseChar().toString()
            // Ё is filed under Е, as a Russian phone book does — the framework's rule, kept.
            upper == 'Ё' -> "е"
            upper in 'А'..'Я' -> upper.lowercaseChar().toString()
            else -> Other
        }
    }

    /**
     * The order the alphabets come in. [Other] is always first; a phone set to Korean reads ㄱ–ㅎ
     * before A–Z, the way its own contacts list does, and every other language reads Latin first.
     */
    fun scripts(koreanFirst: Boolean): List<List<String>> =
        if (koreanFirst) {
            listOf(HangulBuckets, LatinBuckets, CyrillicBuckets)
        } else {
            listOf(LatinBuckets, CyrillicBuckets, HangulBuckets)
        }

    /**
     * Where a bucket sits in the index, for the comparator: [Other] at 0, then each alphabet in the
     * order [scripts] gives. Buckets have to be ordered by this rather than by code point, because
     * the Korean-first order is not the order Unicode happens to number the scripts in.
     */
    fun rankOf(bucket: String, koreanFirst: Boolean): Int =
        (if (koreanFirst) KoreanFirstRanks else LatinFirstRanks)[bucket] ?: 0

    // Looked up once per comparison, which a sort makes O(n log n) of: a map, not a walk of the lists.
    private val LatinFirstRanks = ranks(koreanFirst = false)
    private val KoreanFirstRanks = ranks(koreanFirst = true)

    private fun ranks(koreanFirst: Boolean): Map<String, Int> =
        (listOf(Other) + scripts(koreanFirst).flatten())
            .withIndex()
            .associate { (index, bucket) -> bucket to index }

    /**
     * What the jump grid shows: [Other] always, then every alphabet the list actually uses — each in
     * full, so the letters you own nothing under are there to be dimmed — and the phone's own
     * alphabet as the fallback when it uses none, so an empty list still shows a grid. Offering all
     * three unconditionally would be seventy squares for a library of English albums.
     */
    fun jumpDomain(present: Set<String>, koreanFirst: Boolean): List<String> {
        val used = scripts(koreanFirst).filter { script -> script.any { it in present } }
        val shown = used.ifEmpty {
            listOf(if (koreanFirst) HangulBuckets else LatinBuckets)
        }
        return buildList {
            add(Other)
            shown.forEach { addAll(it) }
        }
    }

    /**
     * Whether a search [query] finds [text]: case-insensitive "contains", plus the Korean shorthand
     * of typing initial consonants only — ㅂㅌㅅㄴㄷ finds 방탄소년단, which is how anyone with a Korean
     * keyboard searches a contacts list or a music library. Spaces are ignored on that path, so the
     * shorthand still works across a name written as two words.
     */
    fun matches(text: String, query: String?): Boolean {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) return true
        if (text.contains(q, ignoreCase = true)) return true
        val initialsOnly = q.filterNot { it.isWhitespace() }
        if (initialsOnly.isEmpty() || !initialsOnly.all { it in Initials }) return false
        return initialsOf(text).contains(initialsOnly)
    }

    /** [text] with each Hangul syllable replaced by its initial consonant, spaces dropped. */
    fun initialsOf(text: String): String = buildString(text.length) {
        for (c in text) {
            if (c.isWhitespace()) continue
            append(initialOf(c) ?: c.lowercaseChar())
        }
    }
}
