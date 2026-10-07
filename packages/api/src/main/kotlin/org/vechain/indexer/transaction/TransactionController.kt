package org.vechain.indexer.transaction

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
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
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.cachedByAge
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.TransactionId
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidCursor
import org.vechain.indexer.validation.ValidPageSize

@Profile("blocks")
@Tag(name = "Transactions", description = "Transactions.")
@Validated
@RestController
@RequestMapping(TRANSACTIONS_PATH)
open class TransactionController(private val transactionService: TransactionService) {

    @GetMapping("/latest")
    @Operation(
        summary = "List the latest transactions",
        description =
            "The latest transactions, newest block first and in on-chain order within a " +
                "block. Results can be one block (about 10 seconds) old.",
    )
    @CommonApiResponses
    @ExpandedParameter
    @PaginationSize
    @Cursor
    @CacheFor(CachePolicy.VOLATILE)
    open fun getLatestTransactions(
        @RequestParam(required = false) expanded: Boolean = false,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @ValidCursor @RequestParam(required = false) cursor: String?,
    ): PaginatedResponse<IndexedTransaction> = transactionService.findLatest(size, cursor, expanded)

    @GetMapping("{txId}")
    @Operation(summary = "Get a transaction")
    @TransactionIdParameter
    @CommonApiResponses
    @ExpandedParameter
    @CacheFor(CachePolicy.VOLATILE)
    open fun getTransactionById(
        @TransactionId @PathVariable txId: String,
        @RequestParam(required = false) expanded: Boolean = false,
    ): ResponseEntity<IndexedTransaction> {
        val transaction =
            transactionService.findById(txId)
                ?: throw ResourceNotFoundException("Transaction not found for txId $txId")
        return cachedByAge(transaction.blockTimestamp, transaction)
    }

    @GetMapping
    @Operation(summary = "List transactions sent or sponsored by an address")
    @AddressParameter(
        name = "origin",
        required = true,
        description = "Sender address.",
    )
    @IncludeDelegatedParameter
    @CommonApiResponses
    @ExpandedParameter
    @PaginationParameters
    @CacheFor(CachePolicy.MINUTE)
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
                expanded,
            )
        )
    }

    @GetMapping("/delegated")
    @Operation(summary = "List transactions an address sponsored")
    @AddressParameter(name = "delegator", required = true)
    @CommonApiResponses
    @ExpandedParameter
    @PaginationParameters
    @CacheFor(CachePolicy.VOLATILE)
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
                expanded,
            )
        )
    }

    @GetMapping("/contract")
    @Operation(
        summary = "List transactions that called a contract",
        description = "Results can be up to a minute old.",
    )
    @AddressParameter(name = "contractAddress", required = true)
    @CommonApiResponses
    @ExpandedParameter
    @PaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getTransactionsByContract(
        @ValidAddress @RequestParam contractAddress: Address,
        @RequestParam(required = false) expanded: Boolean = false,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransaction> =
        paginatedResponse(
            transactionService.findByContractAddress(
                contractAddress,
                toPageable(page, size, direction, "blockNumber", "_id"),
                expanded,
            )
        )
}
