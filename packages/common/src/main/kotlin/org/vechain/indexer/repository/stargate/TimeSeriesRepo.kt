package org.vechain.indexer.repository.stargate

import org.springframework.data.domain.Sort
import org.vechain.indexer.model.IndexedDocument

interface TimeSeriesRepo<T : IndexedDocument> {

    fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): T?

    fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): T?

    fun findByBlockTimestampBetween(
        after: Long,
        before: Long,
        sort: Sort = Sort.by(Sort.Direction.ASC, "blockTimestamp"),
    ): List<T>
}
