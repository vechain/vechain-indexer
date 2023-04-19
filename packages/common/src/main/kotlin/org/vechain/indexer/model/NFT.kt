package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigInteger

@Document("nfts")
@CompoundIndex(name = "nft_token_address_idx", def = "{'contractAddress': 1, 'tokenId': 1}", unique = true)
@CompoundIndex(name = "nft_owner_address_idx", def = "{'owner': 1, 'contractAddress': 1}")
data class NFT @ConstructorBinding constructor(
    /**
     * sha1 hash of `${contractAddress}-${tokenId}`
     */
    @Id
    val id: String,
    val tokenId: BigInteger,
    val contractAddress: String,
    val owner: String,
    val txId: String,
//    @Indexed(direction = IndexDirection.DESCENDING)
    override val blockNumber: Long,
    override val blockId: String,
) : IndexedDocument