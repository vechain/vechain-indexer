package org.vechain.indexer.transaction

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.TRANSACTIONS_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.Cursor
import org.vechain.indexer.docs.ExpandedParameter
import org.vechain.indexer.docs.IncludeDelegatedParameter
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.PaginationSize
import org.vechain.indexer.docs.TransactionIdParameter
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.VOLATILE_CACHE_CONTROL
import org.vechain.indexer.rest.cacheControlFor
import org.vechain.indexer.rest.gradedMaxAge
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.TransactionId
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidCursor
import org.vechain.indexer.validation.ValidPageSize

@Profile("transactions", "transaction")
@Tag(name = "Transactions", description = "Query on chain transactions")
@Validated
@RestController
@RequestMapping(TRANSACTIONS_PATH)
open class TransactionController(private val transactionService: TransactionService) {

    @GetMapping("/latest")
    @Operation(
        summary = "Get latest transactions",
        description =
            """
            Returns latest transactions by block number descending and canonical transaction order
            within each block. The head of the chain moves every block, so shared caches may serve
            a response up to one block old.
            """,
    )
    @CommonApiResponses
    @ExpandedParameter
    @PaginationSize
    @Cursor
    open fun getLatestTransactions(
        @RequestParam(required = false) expanded: Boolean = false,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @ValidCursor @RequestParam(required = false) cursor: String?,
    ): ResponseEntity<PaginatedResponse<IndexedTransaction>> {
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, VOLATILE_CACHE_CONTROL)
            .body(transactionService.findLatest(size, cursor))
    }

    @GetMapping("{txId}")
    @Operation(
        summary = "Get transaction by ID",
        description =
            """
            A confirmed transaction never changes, so the response is cacheable for as long as it
            has already been settled: `Cache-Control` grants the age of the containing block,
            capped at a year. A transaction from moments ago is therefore barely cached, and a
            reorg can only serve a dropped transaction for as long as it had been on chain.
            """,
    )
    @TransactionIdParameter
    @CommonApiResponses
    @ExpandedParameter
    open fun getTransactionById(
        @TransactionId @PathVariable txId: String,
        @RequestParam(required = false) expanded: Boolean = false,
    ): ResponseEntity<IndexedTransaction> {
        val transaction =
            transactionService.findById(txId)
                ?: throw ResourceNotFoundException("Transaction not found for txId $txId")
        val maxAge = gradedMaxAge(transaction.blockTimestamp, Instant.now().epochSecond)
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, cacheControlFor(maxAge))
            .body(transaction)
    }

    @GetMapping
    @Operation(summary = "Get all transactions by an origin or delegator address")
    @AddressParameter(
        name = "origin",
        required = true,
        description = "Address of the transaction origin",
    )
    @IncludeDelegatedParameter
    @CommonApiResponses
    @ExpandedParameter
    @PaginationParameters
    open fun getTransactionsByOriginOrDelegator(
        @ValidAddress @RequestParam origin: Address,
        @RequestParam(required = false) includeDelegated: Boolean = false,
        @RequestParam(required = false) expanded: Boolean = false,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransaction> {
        return paginatedResponse(
            transactionService.findByOriginOrDelegator(
                origin,
                includeDelegated,
                toPageable(page, size, direction, "blockNumber", "_id"),
            )
        )
    }

    @GetMapping("/delegated")
    @Operation(summary = "Get all delegated transactions by a delegator address")
    @AddressParameter(name = "delegator", required = true)
    @CommonApiResponses
    @ExpandedParameter
    @PaginationParameters
    open fun getDelegatedTransactions(
        @ValidAddress @RequestParam delegator: Address,
        @RequestParam(required = false) expanded: Boolean = true,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransaction> {
        return paginatedResponse(
            transactionService.findAllDelegated(
                delegator,
                toPageable(page, size, direction, "blockNumber", "_id"),
            )
        )
    }

    @GetMapping("/contract")
    @Operation(
        summary = "Get all transactions for a contract address",
        description =
            """
            A new transaction can arrive for any contract at any block, so shared caches may serve
            a response up to one block old.
            """,
    )
    @AddressParameter(name = "contractAddress", required = true)
    @CommonApiResponses
    @ExpandedParameter
    @PaginationParameters
    open fun getTransactionsByContract(
        @ValidAddress @RequestParam contractAddress: Address,
        @RequestParam(required = false) expanded: Boolean = false,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): ResponseEntity<PaginatedResponse<IndexedTransaction>> {
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, VOLATILE_CACHE_CONTROL)
            .body(
                paginatedResponse(
                    transactionService.findByContractAddress(
                        contractAddress,
                        toPageable(page, size, direction, "blockNumber", "_id"),
                    )
                )
            )
    }
}
