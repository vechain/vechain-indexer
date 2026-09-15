package org.vechain.indexer.vevote

import org.vechain.indexer.IndexedDocument

/** One voter's ballot on a legacy proposal; [choices] are the 1-based options it selected. */
data class HistoricProposalsVote(
    val proposalId: String,
    val contract: String,
    val voter: String,
    val choices: List<Int>,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : IndexedDocument
