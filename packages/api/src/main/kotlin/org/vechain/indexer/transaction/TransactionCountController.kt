package org.vechain.indexer.transaction

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.TRANSACTIONS_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy

@Profile("transactions", "transaction-count")
@Tag(name = "Transactions", description = "Query on chain transactions")
@Validated
@RestController
@RequestMapping(TRANSACTIONS_PATH)
open class TransactionCountController(
    private val transactionCountService: TransactionCountApiService
) {

    @GetMapping("/count")
    @Operation(
        summary = "Get cumulative transaction, clause, and reverted totals on VeChain",
        description =
            """
            Returns the cumulative number of transactions, clauses, reverted transactions, and
            reverted clauses observed on VeChain up to the most recently indexed block. Clause
            totals are included because clauses are a property of transactions, and reverted totals
            cover reverted transactions plus the clauses contained within those reverted
            transactions.
        """,
    )
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getTransactionCount(): TransactionCountSummary =
        transactionCountService.getLatestCount()
            ?: throw ResourceNotFoundException("Transaction count not found")
}
