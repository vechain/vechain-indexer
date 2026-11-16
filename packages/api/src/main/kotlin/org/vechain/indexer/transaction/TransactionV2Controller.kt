package org.vechain.indexer.transaction

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.TRANSACTIONS_PATH_V2
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.BlockNumberParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.CursorPaginationParameters
import org.vechain.indexer.docs.ExpandedParameter
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.toSortDirection
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidCursor
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize

@Profile("blocks")
@Tag(name = "Transactions", description = "Transactions.")
@Validated
@RestController
@RequestMapping(TRANSACTIONS_PATH_V2)
open class TransactionV2Controller(private val transactionService: TransactionService) {

    @GetMapping("/contract")
    @Operation(
        summary = "List transactions that called a contract",
        description =
            "Results can be up to a minute old. For the next page, pass `pagination.cursor` " +
                "back as `cursor` with the same `direction`.",
    )
    @AddressParameter(name = "contractAddress", required = true)
    @CommonApiResponses
    @ExpandedParameter
    @CursorPaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getTransactionsByContract(
        @ValidAddress @RequestParam contractAddress: Address,
        @RequestParam(required = false) expanded: Boolean = false,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @ValidCursor @RequestParam(required = false) cursor: String?,
        @BlockNumberParameter(name = "fromBlock", description = "Lowest block, inclusive.")
        @ValidNonNegativeLong
        @RequestParam(required = false)
        fromBlock: Long?,
        @BlockNumberParameter(name = "toBlock", description = "Highest block, inclusive.")
        @ValidNonNegativeLong
        @RequestParam(required = false)
        toBlock: Long?,
    ): PaginatedResponse<IndexedTransaction> {
        if (fromBlock != null && toBlock != null && fromBlock > toBlock) {
            throw BadRequestException("fromBlock must not be greater than toBlock")
        }
        return transactionService.findByContractAddress(
            contractAddress,
            cursor,
            fromBlock,
            toBlock,
            size,
            toSortDirection(direction),
            expanded,
        )
    }
}
