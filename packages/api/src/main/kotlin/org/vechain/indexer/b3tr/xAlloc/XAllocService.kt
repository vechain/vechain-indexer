package org.vechain.indexer.b3tr.xAlloc

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.b3tr.xAlloc.repository.XAllocResultRepository

@Profile("b3tr", "b3tr-x-alloc")
@Service
open class XAllocService(private val xAllocResultRepository: XAllocResultRepository) :
    IndexerService {
    /**
     * Get the results of XAllocation voting for a specific round.
     *
     * @param roundId Round to filter by.
     */
    open fun getXAllocResults(roundId: Int): List<XAllocResult> =
        xAllocResultRepository.findByRoundId(roundId)

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf("XAllocResult" to (xAllocResultRepository.getLatestRecord()?.blockNumber ?: 0))
}
