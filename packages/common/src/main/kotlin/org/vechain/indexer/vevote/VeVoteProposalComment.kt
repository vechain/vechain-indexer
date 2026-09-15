package org.vechain.indexer.vevote

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.vechain.indexer.IndexedDocument

data class VeVoteProposalComment(
    @JsonIgnore val id: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val voter: String,
    val proposalId: String,
    val support: Support,
    val weight: BigInteger,
    val reason: String,
) : IndexedDocument
