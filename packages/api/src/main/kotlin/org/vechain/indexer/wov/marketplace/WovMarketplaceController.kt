package org.vechain.indexer.wov.marketplace

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.WOV_PATH
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.Cursor
import org.vechain.indexer.docs.PaginationSize
import org.vechain.indexer.exception.ExceptionResponse
import org.vechain.indexer.exception.WindowNotIndexedException
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.cachedByAge
import org.vechain.indexer.validation.ValidCursor
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize

@Profile("wov-marketplace")
@Tag(name = "WoV Marketplace", description = "Marketplace purchases, per buyer.")
@Validated
@RestController
@RequestMapping(WOV_PATH)
open class WovMarketplaceController(private val service: WovBuyerStatsService) {

    @GetMapping("marketplace/buyers")
    @Operation(
        summary = "List buyers and what they spent between two times",
        description =
            """
            Every address that bought on the marketplace between `from` and `to` (Unix seconds, by
            block time), sorted by address. A purchase at exactly `from` is included and one at
            exactly `to` is not, so back-to-back ranges never count a purchase twice.

            Each buyer shows the items bought and the amount spent in each payment token, in the
            token's smallest unit. VET and wrapped VET (VVET) are listed separately. Use `from=0`
            for all-time totals.

            - A `to` later than the newest indexed block gets a 409, with the latest time you can
              ask for in the `X-Indexed-Through` header.
            - When paging, send the same `from` and `to` with each `cursor`.
            - Recent purchases can still be undone by a chain reorganisation. For a reward cutoff,
              query once the chain is finalized past `to`.
            """,
    )
    @AfterParameter(
        name = "from",
        required = true,
        description = "Start time in Unix seconds. A purchase at exactly this time is included.",
    )
    @BeforeParameter(
        name = "to",
        required = true,
        description = "End time in Unix seconds. A purchase at exactly this time is not included.",
    )
    @PaginationSize
    @Cursor
    @CommonApiResponses
    @ApiResponse(
        responseCode = "409",
        description = "`to` is later than the newest indexed block",
        headers =
            [
                Header(
                    name = WindowNotIndexedException.INDEXED_THROUGH_HEADER,
                    description = "Timestamp of the newest indexed block (Unix time in seconds).",
                    schema = Schema(type = "integer", format = "int64"),
                )
            ],
        content =
            [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ExceptionResponse::class),
                )
            ],
    )
    @CacheFor(CachePolicy.VOLATILE)
    open fun getBuyers(
        @ValidNonNegativeLong @RequestParam from: Long,
        @ValidNonNegativeLong @RequestParam to: Long,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @ValidCursor @RequestParam(required = false) cursor: String?,
    ): ResponseEntity<PaginatedResponse<WovBuyerStats>> =
        cachedByAge(to, service.buyers(from, to, size, cursor))
}
