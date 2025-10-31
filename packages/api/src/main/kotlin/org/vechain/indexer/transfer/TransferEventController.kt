package org.vechain.indexer.transfer

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.TRANSFER_EVENTS_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidAddressList
import org.vechain.indexer.validation.ValidPageSize

@Profile("transfers")
@Tag(name = "TransferEvent", description = "Query blockchain transfer events")
@Validated
@RestController
@RequestMapping(TRANSFER_EVENTS_PATH)
open class TransferEventController(private val transferEventService: TransferEventService) {

    @GetMapping
    @Operation(summary = "Get transfer events by address or token address")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "To or from address of the transfer event",
        required = false,
        example = "0x995711ADca070C8f6cC9ca98A5B9C5A99b8350b1",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "tokenAddress",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "The token contract address",
        required = false,
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getTransferEvents(
        @ValidAddress @RequestParam(required = false) address: Address?,
        @ValidAddress @RequestParam(required = false) tokenAddress: Address?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransferEvent> {
        val pageable = PaginationUtils.toPageable(page, size, direction)

        val resultsPage =
            when {
                address != null && tokenAddress != null ->
                    transferEventService.find(address, tokenAddress, pageable)
                address != null -> transferEventService.findByAddress(address, pageable)
                tokenAddress != null ->
                    transferEventService.findByTokenAddress(tokenAddress, pageable)
                else -> throw BadRequestException("Either address or tokenAddress must be provided")
            }

        return paginatedResponse(resultsPage)
    }

    @GetMapping("/from")
    @Operation(summary = "Get transfer events by from address")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "From address of the transfer event",
        required = true,
        example = "0x995711ADca070C8f6cC9ca98A5B9C5A99b8350b1",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "tokenAddress",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "The token contract address",
        required = false,
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getTransferEventsByFrom(
        @ValidAddress @RequestParam address: Address,
        @ValidAddress @RequestParam(required = false) tokenAddress: Address?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransferEvent> {
        return paginatedResponse(
            transferEventService.findByFrom(
                address,
                tokenAddress,
                PaginationUtils.toPageable(page, size, direction),
            )
        )
    }

    @GetMapping("/to")
    @Operation(summary = "Get transfer events by to address")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "To address of the transfer event",
        required = true,
        example = "0x995711ADca070C8f6cC9ca98A5B9C5A99b8350b1",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "tokenAddress",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "The token contract address",
        required = false,
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getTransferEventsByTo(
        @ValidAddress @RequestParam address: Address,
        @ValidAddress @RequestParam(required = false) tokenAddress: Address?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransferEvent> {
        return paginatedResponse(
            transferEventService.findByTo(
                address,
                tokenAddress,
                PaginationUtils.toPageable(page, size, direction),
            )
        )
    }

    @GetMapping("/forBlock")
    @Operation(summary = "Get transfer events for a specific block")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "addresses",
        array =
            ArraySchema(
                schema = Schema(type = "string", pattern = Address.Companion.REGEX),
                minItems = 1,
                maxItems = 20,
            ),
        description = "Addresses to query. Max 20 addresses",
        required = true,
        example = "[\"0x995711ADca070C8f6cC9ca98A5B9C5A99b8350b1\"]",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "blockNumber",
        schema =
            Schema(
                type = "integer",
                format = "int64",
                minimum = "0",
                maximum = "9223372036854775807",
            ),
        description = "Block number to query",
        required = true,
        example = "1000000",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getTransfersForBlock(
        @ValidAddressList @RequestParam addresses: List<Address>,
        @RequestParam blockNumber: Long,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransferEvent> {
        return paginatedResponse(
            transferEventService.findByBlockNumber(
                blockNumber,
                addresses,
                PaginationUtils.toPageable(page, size, direction),
            )
        )
    }

    @GetMapping("/fungible-tokens-contracts")
    @Operation(summary = "Get all fungible tokens transfers contracts for a given account")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "The address of origin or destination of the fungible tokens transfer events",
        required = true,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "officialTokensOnly",
        schema = Schema(type = "boolean"),
        description = "If set to true, only official tokens will be returned. Defaults to false.",
        required = false,
        example = "false",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getFungibleTokensContractsByAddress(
        @ValidAddress @RequestParam address: Address,
        @RequestParam(required = false) officialTokensOnly: Boolean = false,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<String> {
        return paginatedResponse(
            transferEventService.findFungibleTokensContractsByAddress(
                address,
                officialTokensOnly,
                PaginationUtils.toPageable(page, size, direction),
            )
        )
    }
}
