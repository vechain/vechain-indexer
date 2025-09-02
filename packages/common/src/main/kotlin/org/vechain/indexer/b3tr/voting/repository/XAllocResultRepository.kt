package org.vechain.indexer.b3tr.voting.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.xAlloc.XAllocResult

@Profile("b3tr", "b3tr-x-alloc")
@Repository
interface XAllocResultRepository : BasePagingAndSortingIndexedRepository<XAllocResult, String> {
    fun findByRoundId(roundId: Int): List<XAllocResult>
}
