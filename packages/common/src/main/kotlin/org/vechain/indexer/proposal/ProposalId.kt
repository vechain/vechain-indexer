package org.vechain.indexer.proposal

data class ProposalId(val value: String) {
    companion object {
        const val REGEX = "^[0-9]{65,100}\$"
    }

    fun isValid(): Boolean = value.matches(Regex(REGEX))
}
