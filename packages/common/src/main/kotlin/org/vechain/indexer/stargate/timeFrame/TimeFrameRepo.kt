package org.vechain.indexer.stargate.timeFrame

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.accounts.TimeFrame

interface TimeFrameRepo<T : IndexedDocument> {
    fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): T?

    fun findByTimeFramesContains(timeFrame: TimeFrame, pageable: Pageable): Slice<T>

    fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<T>

    fun findByBlockTimestampAfter(blockTimestamp: Long): List<T>
}
