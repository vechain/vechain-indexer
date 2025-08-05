package org.vechain.indexer.b3tr.gm

import java.math.BigInteger

data class LevelOverviewDelta(
    var nftsDiff: Long = 0,
    var b3trDelta: BigInteger = BigInteger.ZERO,
    var nodesAttached: Long = 0,
)
