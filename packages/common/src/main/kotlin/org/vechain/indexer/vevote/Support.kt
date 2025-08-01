package org.vechain.indexer.vevote

import java.math.BigInteger

enum class Support {
    AGAINST,
    FOR,
    ABSTAIN;

    companion object {
        fun map(bi: BigInteger): Support =
            when (bi) {
                BigInteger.ZERO -> AGAINST
                BigInteger.ONE -> FOR
                else -> ABSTAIN
            }
    }
}
