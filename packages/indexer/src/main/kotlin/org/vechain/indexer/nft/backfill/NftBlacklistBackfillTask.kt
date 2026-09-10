package org.vechain.indexer.nft.backfill

import java.time.Instant
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/**
 * One row-flag reconciliation per collection, replaced whenever the collection's blacklist state
 * changes again. `cursors` holds the last `tokenId` reached per target collection; a collection
 * absent from it is still in its null-`tokenId` pass. `revision` guards a worker's progress write
 * against a replacement that landed mid-batch.
 */
@Document(collection = NftBlacklistBackfillTask.COLLECTION)
data class NftBlacklistBackfillTask(
    @Id val id: String,
    val isBlacklisted: Boolean,
    val requestedAtBlock: Long,
    val revision: Long = 1,
    val status: Status = Status.PENDING,
    val cursors: Map<String, String> = emptyMap(),
    val completed: Set<String> = emptySet(),
    val rowsUpdated: Long = 0,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    enum class Status {
        PENDING,
        DONE,
        FAILED,
    }

    companion object {
        const val COLLECTION = "nft_blacklist_backfill"
    }
}
