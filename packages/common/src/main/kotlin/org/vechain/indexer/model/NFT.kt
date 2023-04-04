package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigInteger

@Document("nfts")
@CompoundIndex(name = "nft_token_address_idx", def = "{'contractAddress': 1, 'tokenId': 1}", unique = true)
@CompoundIndex(name = "nft_owner_address_idx", def = "{'owner': 1, 'contractAddress': 1}")
data class NFT(
    /**
     * ID = `${contractAddress}-${tokenId}`
     */
    @Id
    val id: String? = null,
    val tokenId: BigInteger? = null,
    val contractAddress: String? = null,
    val owner: String? = null,
    val txId: String? = null,
    val blockNumber: Long? = null,
)