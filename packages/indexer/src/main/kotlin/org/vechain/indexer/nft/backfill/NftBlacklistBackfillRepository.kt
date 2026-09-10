package org.vechain.indexer.nft.backfill

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Profile("nfts", "history")
@Repository
interface NftBlacklistBackfillRepository : MongoRepository<NftBlacklistBackfillTask, String> {
    fun findAllByStatusOrderByUpdatedAtAsc(
        status: NftBlacklistBackfillTask.Status
    ): List<NftBlacklistBackfillTask>

    fun countByStatus(status: NftBlacklistBackfillTask.Status): Long
}
