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
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.model.Transaction
import org.vechain.indexer.pageable.PageablePage
import org.vechain.indexer.pageable.PageableSize
import org.vechain.indexer.pageable.PageableSortDirection
import org.vechain.indexer.service.TransactionService
import org.vechain.indexer.utils.AddressUtil
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.utils.TransactionUtils
import org.vechain.indexer.validation.Address
import org.vechain.indexer.validation.TransactionId

@Profile("transactions")
@Tag(name = "Transactions", description = "Query on chain transactions")
@Validated
@RestController
@RequestMapping(TRANSACTIONS_PATH)
open class TransactionController(private val transactionService: TransactionService) {

    @GetMapping("{txId}")
    @Operation(summary = "Get transaction by ID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid txId"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "txId",
        schema = Schema(type = "string", pattern = TransactionUtils.REGEX),
        description = "A valid transaction ID",
        required = true,
        example = "0xacc8566c931235a43a775120d48680278d42fa12111aa3c4d4e3a7e8cfcd360a"
    )
    open fun getTransactionById(@TransactionId @PathVariable txId: String): Transaction {
        return transactionService.findById(txId)
            ?: throw ResourceNotFoundException("Transaction not found for txId $txId")
    }


    @GetMapping
    @Operation(summary = "Get all transactions by an origin address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "origin",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "Address of the transaction origin",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "includeDelegated",
        schema = Schema(type = "boolean"),
        description = "Whether to include transactions the address paid gas for",
        required = false,
        example = "false"
    )
    open fun getTransactionsByOrigin(
        @Address @RequestParam origin: String,
        @RequestParam(required = false) includeDelegated: Boolean = false,
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): List<Transaction> {
        return transactionService.findByOrigin(
            origin,
            includeDelegated,
            toPageable(page, size, direction, "blockNumber", "_id")
        )
    }

    @GetMapping("/delegated")
    @Operation(summary = "Get all delegated transactions by a delegator address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid delegator address"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "delegator",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "The address of the delegator",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    open fun getDelegatedTransactions(
        @Address @RequestParam delegator: String,
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): List<Transaction> {
        return transactionService.findAllDelegated(
            delegator,
            toPageable(page, size, direction, "blockNumber", "_id")
        )
    }
}
