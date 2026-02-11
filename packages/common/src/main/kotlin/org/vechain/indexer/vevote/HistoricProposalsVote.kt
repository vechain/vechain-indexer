package org.vechain.indexer.vevote

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames

@Document(collection = IndexerNames.HISTORIC_PROPOSALS_VOTE.COLLECTION)
data class HistoricProposalsVote(
    @Id val id: String,
    val proposalId: String,
    val contract: String,
    val choices: List<Int>,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : IndexedDocument
