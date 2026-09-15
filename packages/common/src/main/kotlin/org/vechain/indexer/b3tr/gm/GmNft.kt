package org.vechain.indexer.b3tr.gm

import java.math.BigInteger
import org.vechain.indexer.IndexedDocument

data class GmNft(
    val tokenId: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val level: GmLevelName,
    val attachedNodeId: String?,
    val b3trDonated: BigInteger,
    val owner: String,
) : IndexedDocument
