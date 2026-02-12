package org.vechain.indexer.b3tr.xAlloc.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.xAlloc.XAllocResult

@Profile("b3tr", "b3tr-x-alloc")
@Repository
interface XAllocResultRepository : BaseIndexedRepository<XAllocResult, String> {
    @Query("{ 'roundId': ?0 }") fun findByRoundId(roundId: Int): List<XAllocResult>

    @Query("{ 'appId': ?0 }") fun findByAppId(appId: String): List<XAllocResult>

    @Query("{ 'appId': ?0, 'roundId': ?1 }")
    fun findByAppIdAndRoundId(appId: String, roundId: Int): XAllocResult?
}
