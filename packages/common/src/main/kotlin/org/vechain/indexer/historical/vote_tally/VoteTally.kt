package org.vechain.indexer.historical.vote_tally

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.vechain.indexer.IndexedDocument

@Document(collection = "historical-vote-tally")
data class VoteTally(
    @Field("_id") @Id val id: String,
    val proposalId: String,
    val voterId: String,
    val selectedOptions: List<Int>,
    val endorser: String?,
    val tokenId: String?,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : IndexedDocument
