package org.vechain.indexer.transaction

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Profile("transactions", "transaction-count")
@Service
open class TransactionCountApiService(
    private val transactionCountSummaryRepository: TransactionCountSummaryRepository
) {
    open fun getLatestCount(): TransactionCountSummary? =
        transactionCountSummaryRepository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID)
}
