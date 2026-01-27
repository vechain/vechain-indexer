package org.vechain.indexer.vevote

import com.fasterxml.jackson.annotation.JsonIgnore
import org.vechain.indexer.IndexedDocument

data class HistoricProposals(
    val id: String,
    val proposalId: String,
    val contractAddress: String,
    val createdDate: String,
    val proposer: String?,
    val title: String?,
    val description: String?,
    val proposalType: Int?,
    val choices: List<String>?,
    @JsonIgnore val test: Boolean = false,
    val createTime: Long?,
    val votingStartTime: Long?,
    val votingEndTime: Long?,
    val voteTallies: List<Long>?,
    val totalVotes: Long?,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : IndexedDocument
