package org.vechain.indexer.b3tr

enum class ProposalSupport(val value: Int) {
    AGAINST(0),
    FOR(1),
    ABSTAIN(2);

    companion object {
        fun fromValue(value: Int?): ProposalSupport? = values().find { it.value == value }
    }
}
