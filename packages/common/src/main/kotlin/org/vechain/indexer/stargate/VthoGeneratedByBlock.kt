package org.vechain.indexer.stargate

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument

@Document(collection = "stargate_vtho_generated_by_block")
data class VthoGeneratedByBlock
@ConstructorBinding
constructor(
    override val blockId: String,
    @Id override val blockNumber: Long,
    override val blockTimestamp: Long,
    @JsonIgnore val rewardsClaimed: BigInteger,
    val total: BigInteger,
) : IndexedDocument
