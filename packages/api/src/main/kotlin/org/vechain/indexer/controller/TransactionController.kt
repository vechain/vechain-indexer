package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.model.WrappedTransaction
import org.vechain.indexer.service.TransactionService
import org.vechain.indexer.utils.API_PATH
import org.vechain.indexer.utils.AddressUtil
import org.vechain.indexer.utils.TRANSACTIONS_PATH
import org.vechain.indexer.validation.Address

@Tag(name = "Transactions", description = "Query on chain transactions")
@Validated
@RestController
@RequestMapping(API_PATH + TRANSACTIONS_PATH)
open class TransactionController(private val transactionService: TransactionService) {

    @GetMapping("{address}")
    @Operation(summary = "Get all transactions by an origin address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
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
        @Address @PathVariable address: String,
        @RequestParam(required = false) includeDelegated: Boolean?
    ): List<WrappedTransaction> {
        if (includeDelegated == true)
            return transactionService.findByOriginOrGasPayer(address)

        return transactionService.findByOrigin(address)
    }

    @GetMapping("{address}/delegated")
    @Operation(summary = "Get all delegated transactions by a delegator address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "The address of the delegator",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    open fun getDelegatedTransactions(
        @Address @PathVariable address: String
    ): List<WrappedTransaction> {
        return transactionService.findAllDelegated(address)
    }
}