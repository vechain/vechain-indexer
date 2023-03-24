package org.vechain.indexer.model

import java.math.BigInteger

data class NFT(
    val tokenId: BigInteger? = null,
    val contractAddress: String? = null,
    val owner: String? = null,
    val metadata: Any? = null,
    val tokenUri: String? = null
)