package org.vechain.indexer.stargate.timeSeries

import org.springframework.data.domain.Sort
import org.vechain.indexer.IndexedDocument

interface TimeSeriesRepo<T : IndexedDocument> {
    fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): T?

    fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): T?

    fun findByBlockTimestampBetween(
        after: Long,
        before: Long,
        sort: Sort = Sort.by(Sort.Direction.ASC, "blockTimestamp"),
    ): List<T>
}
