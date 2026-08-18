package org.vechain.indexer.transaction

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("transactions", "transaction-count")
@Repository
interface TransactionCountSummaryRepository : BaseIndexedRepository<TransactionCountSummary, String>
