package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.TRANSFER_EVENTS_PATH
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.pageable.PageablePage
import org.vechain.indexer.pageable.PageableSize
import org.vechain.indexer.pageable.PageableSortDirection
import org.vechain.indexer.service.TransferEventService
import org.vechain.indexer.utils.AddressUtil
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.AddressNullable

@Tag(name = "TransferEvent", description = "Query blockchain transfer events")
@RestController
@RequestMapping(TRANSFER_EVENTS_PATH)
open class TransferEventController(private val transferEventService: TransferEventService) {

    @GetMapping
    @Operation(summary = "Get transfer events by address or token address")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "To or from address of the transfer event",
        required = false,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "tokenAddress",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "The token contract address",
        required = false,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    open fun getTransferEvents(
        @AddressNullable @RequestParam(required = false) address: String?,
        @AddressNullable @RequestParam(required = false) tokenAddress: String?,
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): List<TransferEvent> {

        if (address == null && tokenAddress == null) {
            throw BadRequestException("Either address or tokenAddress must be provided")
        }

        val pageable = toPageable(page, size, direction, "blockNumber", "txId", "id")
        return if (address != null && tokenAddress != null) {
            transferEventService.find(address, tokenAddress, pageable)
        } else if (address != null) {
            transferEventService.findByAddress(address, pageable)
        } else {
            transferEventService.findByTokenAddress(tokenAddress!!, pageable)
        }
    }
}