package org.vechain.indexer.utils

object RevisionUtils {

    const val REGEX = "^(?:\\d+|(?i)\\s*(?:best|finalized)\\s*)$"

    fun isValid(revision: String?): Boolean {
        revision?.let {
            return it.matches(Regex(REGEX)) || HexUtils.isValidBlockID(it)
        } ?: return false
    }

    fun isNotValid(revision: String?): Boolean {
        return !isValid(revision)
    }

    fun normalise(revision: String): String {
        return revision.lowercase().trim()
    }
}