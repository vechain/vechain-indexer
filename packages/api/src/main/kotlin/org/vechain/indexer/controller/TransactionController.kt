package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.TRANSACTIONS_PATH
import org.vechain.indexer.docs.ExpandedParameter
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedTransaction
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.paginatedResponse
import org.vechain.indexer.service.TransactionService
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.utils.TransactionUtils
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
    @ApiResponses(value = [ApiResponse(responseCode = "400", description = "Invalid txId")])
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "txId",
        schema = Schema(type = "string", pattern = TransactionUtils.REGEX),
        description = "A valid transaction ID",
        required = true,
        example = "0xacc8566c931235a43a775120d48680278d42fa12111aa3c4d4e3a7e8cfcd360a",
    )
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
    @ApiResponses(
        value = [ApiResponse(responseCode = "400", description = "Invalid address supplied")]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "origin",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "Address of the transaction origin",
        required = true,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "includeDelegated",
        schema = Schema(type = "boolean"),
        description = "Whether to include transactions the address paid gas for",
        required = false,
        example = "false",
    )
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
    @ApiResponses(
        value = [ApiResponse(responseCode = "400", description = "Invalid delegator address")]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "delegator",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "The address of the delegator",
        required = true,
        example = "0x995711ADca070C8f6cC9ca98A5B9C5A99b8350b1",
    )
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
}
