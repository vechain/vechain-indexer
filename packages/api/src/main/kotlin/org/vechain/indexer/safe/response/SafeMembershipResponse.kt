package org.vechain.indexer.safe.response

import org.vechain.indexer.safe.SafeMembership

/**
 * API representation of a Safe membership, serialised in `getSafesForOwner` responses. Hides the
 * indexer bookkeeping fields (blockId/blockNumber/version).
 */
data class SafeMembershipResponse(
    val safe: String,
    val owner: String,
    val addedBlock: Long,
    val addedTimestamp: Long,
    val removedBlock: Long?,
    val removedTimestamp: Long?,
    val lastEventBlock: Long,
) {
    companion object {
        fun from(doc: SafeMembership): SafeMembershipResponse =
            SafeMembershipResponse(
                safe = doc.safe,
                owner = doc.owner,
                addedBlock = doc.addedBlock,
                addedTimestamp = doc.addedTimestamp,
                removedBlock = doc.removedBlock,
                removedTimestamp = doc.removedTimestamp,
                lastEventBlock = doc.blockNumber,
            )
    }
}
