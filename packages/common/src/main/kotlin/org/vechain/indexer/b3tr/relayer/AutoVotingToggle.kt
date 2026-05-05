package org.vechain.indexer.b3tr.relayer

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.model.Views

/**
 * One row per `(address, activeFromRound)`. A toggle emitted in round R becomes effective from
 * round R+1, because round R's `_autoVotingEnabled` checkpoint was already read at its `voteStart`
 * block before the event landed. Multiple toggles by the same user in the same source round
 * collapse to a single row whose `enabled` reflects the last on-chain event in that round.
 *
 * Document id is `${address}:${activeFromRound}` (sha1-hashed via IdUtils.generateId for parity
 * with other B3TR collections).
 */
@Document(collection = IndexerNames.AUTO_VOTING_TOGGLE.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AutoVotingToggle(
    @JsonIgnore @Id val id: String,
    val address: String,
    val enabled: Boolean,
    val activeFromRound: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}
