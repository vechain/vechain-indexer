package org.vechain.indexer.historical.vote_tally

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument

@Document(collection = "historical-proposal-votes")
data class VoteTally(
    @Id val id: String,
    val proposalId: String,
    val voterId: String,
    val selectedOption: List<Int>,
    val tokenId: String,
    val endorser: String?,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : IndexedDocument
