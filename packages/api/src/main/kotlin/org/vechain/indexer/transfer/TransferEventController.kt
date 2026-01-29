package org.vechain.indexer.transfer

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.TRANSFER_EVENTS_PATH
import org.vechain.indexer.docs.AddressListParameter
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.BlockNumberParameter
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
    @AddressParameter(description = "To or from address of the transfer event")
    @AddressParameter(name = "tokenAddress", description = "The token contract address")
    @CommonApiResponses
    @PaginationParameters
    open fun getTransferEvents(
        @ValidAddress @RequestParam(required = false) address: Address?,
        @ValidAddress @RequestParam(required = false) tokenAddress: Address?,
        @RequestParam(required = false) eventType: TransferEventType?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransferEvent> {
        val pageable = PaginationUtils.toPageable(page, size, direction)

        val resultsPage =
            when {
                address != null && tokenAddress != null ->
                    transferEventService.find(address, tokenAddress, eventType, pageable)
                address != null -> transferEventService.findByAddress(address, eventType, pageable)
                tokenAddress != null ->
                    transferEventService.findByTokenAddress(tokenAddress, eventType, pageable)
                else -> throw BadRequestException("Either address or tokenAddress must be provided")
            }

        return paginatedResponse(resultsPage)
    }

    @GetMapping("/from")
    @Operation(summary = "Get transfer events by from address")
    @AddressParameter(description = "From address of the transfer event", required = true)
    @AddressParameter(name = "tokenAddress", description = "The token contract address")
    @CommonApiResponses
    @PaginationParameters
    open fun getTransferEventsByFrom(
        @ValidAddress @RequestParam address: Address,
        @ValidAddress @RequestParam(required = false) tokenAddress: Address?,
        @RequestParam(required = false) eventType: TransferEventType?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransferEvent> {
        return paginatedResponse(
            transferEventService.findByFrom(
                address,
                tokenAddress,
                eventType,
                PaginationUtils.toPageable(page, size, direction),
            )
        )
    }

    @GetMapping("/to")
    @Operation(summary = "Get transfer events by to address")
    @AddressParameter(description = "To address of the transfer event", required = true)
    @AddressParameter(name = "tokenAddress", description = "The token contract address")
    @CommonApiResponses
    @PaginationParameters
    open fun getTransferEventsByTo(
        @ValidAddress @RequestParam address: Address,
        @ValidAddress @RequestParam(required = false) tokenAddress: Address?,
        @RequestParam(required = false) eventType: TransferEventType?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransferEvent> {
        return paginatedResponse(
            transferEventService.findByTo(
                address,
                tokenAddress,
                eventType,
                PaginationUtils.toPageable(page, size, direction),
            )
        )
    }

    @GetMapping("/forBlock")
    @Operation(summary = "Get transfer events for a specific block")
    @AddressListParameter(required = true)
    @BlockNumberParameter(
        required = true,
        description = "Block number to query",
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
    @AddressParameter(
        description = "The address of origin or destination of the fungible tokens transfer events",
        required = true,
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
