package org.vechain.indexer.b3tr.xAlloc

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.thor.model.Block

@Document(collection = "x_alloc_vote_events")
data class XAllocVoteEvent
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val voter: String,
    val roundId: Int,
    val appId: String,
    @JsonIgnore val rawValue: BigInteger,
    val value: Double,
) : IndexedDocument {
    constructor(
        block: Block,
        voter: String,
        roundId: Int,
        appId: String,
        voteValue: BigInteger,
    ) : this(
        id = DigestUtils.sha1Hex("${voter}-${roundId}-${appId}"),
        blockId = block.id,
        blockNumber = block.number,
        blockTimestamp = block.timestamp,
        voter = voter,
        roundId = roundId,
        appId = appId,
        rawValue = voteValue,
        value =
            voteValue
                .toBigDecimal()
                .divide(BigDecimal(1_000_000_000_000_000_000), 18, RoundingMode.HALF_UP)
                .toDouble(),
    )
}
