package org.vechain.indexer.b3tr.voting

enum class Support(val value: Int) {
    AGAINST(0),
    FOR(1),
    ABSTAIN(2);

    companion object {
        fun fromValue(value: Int?): Support? = values().find { it.value == value }
    }
}
