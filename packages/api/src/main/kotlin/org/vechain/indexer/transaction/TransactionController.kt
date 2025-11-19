package org.vechain.indexer.transaction

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.TRANSACTIONS_PATH
import org.vechain.indexer.docs.AccountParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.ExpandedParameter
import org.vechain.indexer.docs.IncludeDelegatedParameter
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.TransactionIdParameter
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.TransactionId
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize

@Profile("transactions")
@Tag(name = "Transactions", description = "Query on chain transactions")
@Validated
@RestController
@RequestMapping(TRANSACTIONS_PATH)
open class TransactionController(private val transactionService: TransactionService) {

    @GetMapping("{txId}")
    @Operation(summary = "Get transaction by ID")
    @TransactionIdParameter
    @CommonApiResponses
    @ExpandedParameter
    open fun getTransactionById(
        @TransactionId @PathVariable txId: String,
        @RequestParam(required = false) expanded: Boolean = false,
    ): IndexedTransaction {
        return transactionService.findById(txId)
            ?: throw ResourceNotFoundException("Transaction not found for txId $txId")
    }

    @GetMapping
    @Operation(summary = "Get all transactions by an origin or delegator address")
    @AccountParameter(
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
    @AccountParameter(name = "delegator", required = true)
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
    @Operation(summary = "Get all transactions for a contract address")
    @AccountParameter(name = "contractAddress", required = true)
    @CommonApiResponses
    @ExpandedParameter
    @PaginationParameters
    open fun getTransactionsByContract(
        @ValidAddress @RequestParam contractAddress: Address,
        @RequestParam(required = false) expanded: Boolean = false,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransaction> {
        return paginatedResponse(
            transactionService.findByContractAddress(
                contractAddress,
                toPageable(page, size, direction, "blockNumber", "_id"),
            )
        )
    }
}
