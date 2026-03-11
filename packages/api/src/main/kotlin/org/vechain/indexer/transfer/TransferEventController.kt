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
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.BlockNumberParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.TransferEventTypeParameter
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidAddressList
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidTransferEventType

@Profile("transfers")
@Tag(name = "TransferEvent", description = "Query blockchain transfer events")
@Validated
@RestController
@RequestMapping(TRANSFER_EVENTS_PATH)
open class TransferEventController(private val transferEventService: TransferEventService) {

    @GetMapping
    @Operation(summary = "Get transfer events by address or token address")
    @TransferEventTypeParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getTransferEvents(
        @AddressParameter(
            name = "address",
            description =
                "To or from address of the transfer event. Either address or tokenAddress must be provided",
        )
        @ValidAddress
        @RequestParam(required = false)
        address: Address?,
        @AddressParameter(
            name = "tokenAddress",
            description =
                "The token contract address. Either address or tokenAddress must be provided",
        )
        @ValidAddress
        @RequestParam(required = false)
        tokenAddress: Address?,
        @ValidTransferEventType @RequestParam(required = false) eventType: List<String>?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransferEvent> {
        TimeValidationUtils.validateTimestamps(after, before)

        if (address == null && tokenAddress == null) {
            throw BadRequestException("Either address or tokenAddress must be provided")
        }

        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                IndexedTransferEvent::blockTimestamp.name,
                IndexedTransferEvent::txId.name,
                "_id",
            )

        return paginatedResponse(
            transferEventService.find(
                toOrFrom = address,
                tokenAddress = tokenAddress,
                eventTypes = eventType?.map { TransferEventType.valueOf(it) },
                after = after,
                before = before,
                pageable = pageable,
            )
        )
    }

    @GetMapping("/from")
    @Operation(summary = "Get transfer events by from address")
    @TransferEventTypeParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getTransferEventsByFrom(
        @AddressParameter(description = "From address of the transfer event", required = true)
        @ValidAddress
        @RequestParam
        address: Address,
        @AddressParameter(name = "tokenAddress", description = "The token contract address")
        @ValidAddress
        @RequestParam(required = false)
        tokenAddress: Address?,
        @ValidTransferEventType @RequestParam(required = false) eventType: List<String>?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransferEvent> {
        TimeValidationUtils.validateTimestamps(after, before)

        return paginatedResponse(
            transferEventService.find(
                from = address,
                tokenAddress = tokenAddress,
                eventTypes = eventType?.map { TransferEventType.valueOf(it) },
                after = after,
                before = before,
                pageable =
                    PaginationUtils.toPageable(
                        page,
                        size,
                        direction,
                        IndexedTransferEvent::blockTimestamp.name,
                        IndexedTransferEvent::txId.name,
                        "_id",
                    ),
            )
        )
    }

    @GetMapping("/to")
    @Operation(summary = "Get transfer events by to address")
    @TransferEventTypeParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getTransferEventsByTo(
        @AddressParameter(description = "To address of the transfer event", required = true)
        @ValidAddress
        @RequestParam
        address: Address,
        @AddressParameter(name = "tokenAddress", description = "The token contract address")
        @ValidAddress
        @RequestParam(required = false)
        tokenAddress: Address?,
        @ValidTransferEventType @RequestParam(required = false) eventType: List<String>?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedTransferEvent> {
        TimeValidationUtils.validateTimestamps(after, before)

        return paginatedResponse(
            transferEventService.find(
                to = address,
                tokenAddress = tokenAddress,
                eventTypes = eventType?.map { TransferEventType.valueOf(it) },
                after = after,
                before = before,
                pageable =
                    PaginationUtils.toPageable(
                        page,
                        size,
                        direction,
                        IndexedTransferEvent::blockTimestamp.name,
                        IndexedTransferEvent::txId.name,
                        "_id",
                    ),
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
        @AddressParameter(
            description =
                "The address of origin or destination of the fungible tokens transfer events",
            required = true,
        )
        @ValidAddress
        @RequestParam
        address: Address,
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
