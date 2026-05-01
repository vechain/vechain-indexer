package org.vechain.indexer.safe

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.model.Views

/**
 * Tracks ownership of a Safe by an address. One document per (safe, owner) pair. `removedBlock` is
 * null while the address is currently an owner; non-null after a `RemovedOwner` event archives the
 * relationship. The same (safe, owner) pair can transition between states: a later `AddedOwner`
 * clears `removedBlock` again (re-add).
 */
@Document(collection = IndexerNames.SAFE_MEMBERSHIP.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SafeMembership(
    @Id val id: String,
    val safe: String,
    val owner: String,
    val addedBlock: Long,
    val addedTimestamp: Long,
    var removedBlock: Long? = null,
    var removedTimestamp: Long? = null,
    @JsonIgnore override var blockId: String,
    @JsonIgnore override var blockNumber: Long,
    @JsonIgnore override var blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        fun buildId(safe: String, owner: String): String =
            "${HexUtils.normalise(safe)}_${HexUtils.normalise(owner)}"
    }
}
