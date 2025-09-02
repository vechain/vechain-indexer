package org.vechain.indexer.b3tr.voting

import kotlin.text.matches

data class ProposalId(val value: String) {
    companion object {
        const val REGEX = "^[0-9]{65,100}\$"
    }

    fun isValid(): Boolean = value.matches(Regex(REGEX))
}
