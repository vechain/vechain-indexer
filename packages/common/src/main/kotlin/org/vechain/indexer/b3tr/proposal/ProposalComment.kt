package org.vechain.indexer.b3tr.proposal

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.b3tr.voting.Support

/** A voter's stated reason for their vote on a proposal; one per (proposal, voter). */
data class ProposalComment(
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val voter: String,
    val proposalId: String,
    val support: Support,
    val weight: BigInteger,
    val power: BigInteger,
    val reason: String,
) : IndexedDocument
