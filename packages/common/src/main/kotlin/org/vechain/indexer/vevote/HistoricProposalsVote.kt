package org.vechain.indexer.vevote

import org.vechain.indexer.IndexedDocument

data class HistoricProposalsVote(
    val id: String,
    val proposalId: String,
    val contract: String,
    val choices: List<Int>,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : IndexedDocument
