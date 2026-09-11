package org.vechain.indexer.transaction

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.blocks.BlocksReadRepository

@Profile("blocks")
@Service
open class TransactionCountApiService(private val repository: BlocksReadRepository) {
    open fun getLatestCount(): TransactionCountSummary? = repository.latestTotals()
}
