package org.vechain.indexer.postgres

/**
 * Postgres rejects U+0000 in TEXT and JSONB alike, and chain strings carry it. NUL is written as
 * ESC NUL_MARK and a literal ESC as ESC ESC_MARK; [unescape] reverses both and leaves any other ESC
 * alone, so rows written before a column was escaped still read back unchanged.
 */
object PostgresText {
    private val NUL = Char(0)
    private val ESC = Char(0xE000)
    private val NUL_MARK = Char(0xE001)
    private val ESC_MARK = Char(0xE002)

    fun needsEscaping(s: String): Boolean = NUL in s || ESC in s

    fun escape(s: String): String =
        if (!needsEscaping(s)) s
        else
            buildString(s.length + 4) {
                for (c in s) {
                    when (c) {
                        NUL -> append(ESC).append(NUL_MARK)
                        ESC -> append(ESC).append(ESC_MARK)
                        else -> append(c)
                    }
                }
            }

    fun unescape(s: String): String =
        if (ESC !in s) s
        else
            buildString(s.length) {
                var i = 0
                while (i < s.length) {
                    val c = s[i]
                    val mark = if (c == ESC && i + 1 < s.length) s[i + 1] else null
                    if (mark == NUL_MARK || mark == ESC_MARK) {
                        append(if (mark == NUL_MARK) NUL else ESC)
                        i += 2
                    } else {
                        append(c)
                        i++
                    }
                }
            }
}
