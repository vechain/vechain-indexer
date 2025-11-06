package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument

@Document(collection = "stargate_vtho_claimed_by_block")
data class VthoClaimedByBlock
@ConstructorBinding
constructor(
    override val blockId: String,
    @Id override val blockNumber: Long,
    override val blockTimestamp: Long,
    val total: BigInteger,
    val legacyRewards: BigInteger,
) : IndexedDocument
