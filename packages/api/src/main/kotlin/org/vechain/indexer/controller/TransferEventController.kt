package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.TRANSFER_EVENTS_PATH
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.service.TransferEventService
import org.vechain.indexer.utils.ApiUtils.toPageable

@Tag(name = "TransferEvent", description = "Query blockchain transfer events")
@RestController
@RequestMapping(TRANSFER_EVENTS_PATH)
open class TransferEventController(private val transferEventService: TransferEventService) {

    @GetMapping
    @Operation(summary = "Get all blockchain transfer events")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "page",
        schema = Schema(type = "Integer"),
        description = "The results page number",
        required = false,
        example = "1"
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "size",
        schema = Schema(type = "Integer"),
        description = "The results page size",
        required = false,
        example = "20"
    )
    open fun getAllTransferEvents(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): List<TransferEvent> {
        return transferEventService.findAll(
            toPageable(page, size, Sort.by(Sort.Direction.ASC, "blockNumber", "txId", "id"))
        )
    }
}