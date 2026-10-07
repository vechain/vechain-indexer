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
import org.vechain.indexer.rest.cachedByAge
import org.vechain.indexer.rest.cachedFor
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
        summary = "Buyers' items and spend per payment token over a window",
        description =
            """
            Every address that completed a marketplace purchase in the half-open window
            `[from, to)`, in address order, with the items bought and the spend per payment token.
            Spend is the sale price in the token's smallest unit; VET and wrapped VET (VVET) are
            reported separately. Lifetime totals are `from=0`.

            - `to` omitted resolves to the newest indexed block's timestamp, which the response
              reports as `to`. A `to` later than that is refused with 409 and an
              `X-Indexed-Through` header, never answered in part.
            - The cursor carries the window's `to`; pass the same `from` on every page, and either
              omit `to` or repeat the cursor's.
            - Finality is the caller's concern: a window ending near the head can change on a reorg.
              For a reward cutoff, query once the finalized block is past `to`.
            """,
    )
    @AfterParameter(
        name = "from",
        required = true,
        description = "Start of the window, inclusive (Unix time in seconds).",
    )
    @BeforeParameter(
        name = "to",
        description =
            "End of the window, exclusive (Unix time in seconds). Defaults to the indexed head.",
    )
    @PaginationSize
    @Cursor
    @CommonApiResponses
    @ApiResponse(
        responseCode = "409",
        description = "The window ends after the newest indexed block",
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
    @CacheFor(CachePolicy.MINUTE)
    open fun getBuyers(
        @ValidNonNegativeLong @RequestParam from: Long,
        @ValidNonNegativeLong @RequestParam(required = false) to: Long?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @ValidCursor @RequestParam(required = false) cursor: String?,
    ): ResponseEntity<WovBuyerStatsResponse> {
        val page = service.buyers(from, to, size, cursor)
        // A window the caller fixed is settled for as long as it has been closed.
        return if (to != null || cursor != null) cachedByAge(page.to, page)
        else cachedFor(CachePolicy.MINUTE, page)
    }
}
