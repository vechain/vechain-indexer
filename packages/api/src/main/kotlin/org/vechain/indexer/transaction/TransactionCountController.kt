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

@Profile("blocks")
@Tag(name = "Transactions", description = "Transactions.")
@Validated
@RestController
@RequestMapping(TRANSACTIONS_PATH)
open class TransactionCountController(
    private val transactionCountService: TransactionCountApiService
) {

    @GetMapping("/count")
    @Operation(
        summary = "Get total transactions and clauses on VeChain",
        description =
            "Running totals of transactions and clauses on VeChain up to the newest indexed " +
                "block, and how many of each reverted. Every clause in a reverted transaction " +
                "counts as a reverted clause.",
    )
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getTransactionCount(): TransactionCountSummary =
        transactionCountService.getLatestCount()
            ?: throw ResourceNotFoundException("Transaction count not found")
}
