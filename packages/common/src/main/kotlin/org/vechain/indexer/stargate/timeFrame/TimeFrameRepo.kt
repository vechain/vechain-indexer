package org.vechain.indexer.stargate.timeFrame

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.accounts.TimeFrame

interface TimeFrameRepo<T : IndexedDocument> {
    fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): T?

    fun findByTimeFramesContains(timeFrame: TimeFrame, pageable: Pageable): Slice<T>

    fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<T>

    fun findByTimeFramesContainsAndBlockTimestampBetween(
        timeFrame: TimeFrame,
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<T>

    fun findByTimeFramesContainsAndBlockTimestampBefore(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<T>

    fun findByBlockTimestampAfter(blockTimestamp: Long, pageable: Pageable): Slice<T>

    fun getLatestRecord(): T?

    fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): T?

    @Query("{ '_id': { '\$ne': '__checkpoint__' } }") fun findAll(pageable: Pageable): Slice<T>

    fun findByBlockTimestampBefore(blockTimestamp: Long, pageable: Pageable): Slice<T>

    fun findByBlockTimestampBetween(from: Long, to: Long, pageable: Pageable): Slice<T>
}
