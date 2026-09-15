package org.vechain.indexer.b3tr.treasury

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("b3tr")
@Service
open class TreasuryTransferService(private val repository: TreasuryTransferReadRepository) :
    IndexerService {

    fun find(
        category: TreasuryTransferCategory? = null,
        after: Long? = null,
        before: Long? = null,
        pageable: Pageable,
    ): Slice<TreasuryTransfer> =
        offsetSlice(pageable, TreasuryTransfer::blockTimestamp.name) { offset, limit, direction ->
            repository.find(category, after, before, offset, limit, direction)
        }

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf("TreasuryTransfer" to repository.latestBlockNumber())
}
