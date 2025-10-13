package org.vechain.indexer.validator

import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument

@Document(collection = "validator_block_rewards")
data class ValidatorReward
@ConstructorBinding
constructor(
    override val blockId: String,
    @Id override val blockNumber: Long,
    override val blockTimestamp: Long,
    val validator: String,
    val blockReward: BigInteger,
    val priorityReward: BigInteger,
    val total: BigInteger,
) : IndexedDocument
