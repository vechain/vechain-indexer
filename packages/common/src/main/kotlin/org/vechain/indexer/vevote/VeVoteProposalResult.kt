package org.vechain.indexer.vevote

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.vechain.indexer.IndexedDocument

/** One proposal's running tally for one support, as of [blockNumber]. */
data class VeVoteProposalResult(
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val proposalId: String,
    val support: Support,
    val totalWeight: BigDecimal,
    val totalVoters: Int,
) : IndexedDocument
