package org.vechain.indexer.b3tr.proposal

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.IdUtils.generateId

data class ProposalComment
@ConstructorBinding
constructor(
    @JsonIgnore val id: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val voter: String,
    val proposalId: String,
    val support: Support,
    val weight: BigInteger,
    val power: BigInteger,
    val reason: String,
) : IndexedDocument {
    constructor(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        voter: String,
        proposalId: String,
        support: Support,
        weight: BigInteger,
        power: BigInteger,
        reason: String,
    ) : this(
        id = generateId(proposalId, HexUtils.normalise(voter)),
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        voter = voter,
        proposalId = proposalId,
        support = support,
        weight = weight,
        power = power,
        reason = reason,
    )
}
