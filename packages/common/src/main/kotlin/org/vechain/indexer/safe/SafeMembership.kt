package org.vechain.indexer.safe

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.model.Views

/** Ownership of a Safe by an address; `removedBlock` is null while the address still owns it. */
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SafeMembership(
    val id: String,
    val safe: String,
    val owner: String,
    val addedBlock: Long,
    val addedTimestamp: Long,
    val removedBlock: Long? = null,
    val removedTimestamp: Long? = null,
    @JsonIgnore val blockId: String,
    @JsonIgnore val blockNumber: Long,
    @JsonIgnore val blockTimestamp: Long,
) {
    companion object {
        fun buildId(safe: String, owner: String): String =
            "${HexUtils.normalise(safe)}_${HexUtils.normalise(owner)}"
    }
}
