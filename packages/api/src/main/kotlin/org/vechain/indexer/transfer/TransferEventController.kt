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
import org.vechain.indexer.docs.Cursor
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.PaginationSize
import org.vechain.indexer.docs.TransferEventTypeParameter
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidAddressList
import org.vechain.indexer.validation.ValidCursor
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidTransferEventType

@Profile("transfers")
@Tag(name = "TransferEvent", description = "VET, VTHO, token and NFT transfers.")
@Validated
@RestController
@RequestMapping(TRANSFER_EVENTS_PATH)
open class TransferEventController(private val transferEventService: TransferEventService) {

    @GetMapping("/latest")
    @Operation(
        summary = "List the latest transfers",
        description =
            "The latest transfers, newest block first and in on-chain order within a block. " +
                "Every transfer type is included unless you pass `eventType`.",
    )
    @TransferEventTypeParameter
    @CommonApiResponses
    @PaginationSize
    @Cursor
    @CacheFor(CachePolicy.VOLATILE)
    open fun getLatestTransfers(
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @ValidCursor @RequestParam(required = false) cursor: String?,
        @ValidTransferEventType @RequestParam(required = false) eventType: List<String>?,
    ): PaginatedResponse<IndexedTransferEvent> {
        val eventTypes =
            eventType?.map { TransferEventType.valueOf(it) }?.takeIf { it.isNotEmpty() }
                ?: TransferEventType.entries
        return transferEventService.findLatestByType(eventTypes, size, cursor)
    }

    @GetMapping
    @Operation(summary = "List transfers by address or token")
    @TransferEventTypeParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getTransferEvents(
        @AddressParameter(
            name = "address",
            description = "Sender or recipient. Give this, `tokenAddress`, or both.",
        )
        @ValidAddress
        @RequestParam(required = false)
        address: Address?,
        @AddressParameter(
            name = "tokenAddress",
            description = "Token contract. Give this, `address`, or both.",
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
    @Operation(summary = "List transfers sent by an address")
    @TransferEventTypeParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getTransferEventsByFrom(
        @AddressParameter(description = "Sender address.", required = true)
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
    @Operation(summary = "List transfers received by an address")
    @TransferEventTypeParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getTransferEventsByTo(
        @AddressParameter(description = "Recipient address.", required = true)
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
    @Operation(summary = "List the transfers in a block")
    @AddressListParameter(required = true)
    @BlockNumberParameter(
        required = true,
        description = "Block number.",
        example = "1000000",
    )
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.MINUTE)
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
    @Operation(summary = "List the tokens an account has sent or received")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "officialTokensOnly",
        schema = Schema(type = "boolean"),
        description = "Only tokens in the official token registry. Defaults to false.",
        required = false,
        example = "false",
    )
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getFungibleTokensContractsByAddress(
        @AddressParameter(
            description = "Account address.",
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
