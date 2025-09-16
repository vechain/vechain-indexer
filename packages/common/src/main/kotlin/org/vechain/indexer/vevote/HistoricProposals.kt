package org.vechain.indexer.vevote

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument

@Document(collection = "historic_proposals")
data class HistoricProposals(
    @Id val id: String,
    val proposalId: String,
    val createdDate: String,
    val proposer: String?,
    val title: String?,
    val proposalType: Int?,
    val choices: List<String>?,
    val createTime: Long?,
    val votingStartTime: Long?,
    val votingEndTime: Long?,
    val voteTallies: List<Long>?,
    val totalVotes: Long?,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : IndexedDocument
