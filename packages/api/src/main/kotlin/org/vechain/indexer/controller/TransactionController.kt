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

@Profile("transaction-indexer")
@Tag(name = "Transactions", description = "Query on chain transactions")
@Validated
@RestController
@RequestMapping(TRANSACTIONS_PATH)
open class TransactionController(private val transactionService: TransactionService) {

    @GetMapping
    @Operation(summary = "Get transaction by ID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid id"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "id",
        schema = Schema(type = "string", pattern = TransactionUtils.REGEX),
        description = "A valid transaction ID",
        required = true,
        example = "0xacc8566c931235a43a775120d48680278d42fa12111aa3c4d4e3a7e8cfcd360a"
    )
    open fun getTransactionById(@TransactionId @RequestParam(required = true) id: String): Transaction {
        return transactionService.findById(id) ?: throw ResourceNotFoundException("Transaction not found")
    }


    @GetMapping("/origin")
    @Operation(summary = "Get all transactions by an origin address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
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
        @Address @RequestParam(required = true) address: String,
        @RequestParam(required = false) includeDelegated: Boolean = false,
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): List<Transaction> {
        return transactionService.findByOrigin(
            address,
            includeDelegated,
            toPageable(page, size, direction, "blockNumber", "id")
        )
    }

    @GetMapping("/delegated")
    @Operation(summary = "Get all delegated transactions by a delegator address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "The address of the delegator",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    open fun getDelegatedTransactions(
        @Address @RequestParam(required = true) address: String,
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): List<Transaction> {
        return transactionService.findAllDelegated(
            address,
            toPageable(page, size, direction, "blockNumber", "id")
        )
    }
}