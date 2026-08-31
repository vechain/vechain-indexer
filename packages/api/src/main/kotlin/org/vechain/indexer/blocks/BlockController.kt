package org.vechain.indexer.blocks

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.BLOCKS_PATH
import org.vechain.indexer.docs.BlockNumberParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationSize
import org.vechain.indexer.docs.PaginationSortDirection
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize

/** A page anchored at a numeric `from` that has more rows behind it can never change again. */
internal const val IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable"
internal const val HEAD_CACHE_CONTROL = "public, max-age=0, s-maxage=10"

@Profile("blocks")
@Tag(name = "Blocks", description = "VeChainThor block headers")
@Validated
@RestController
@RequestMapping(BLOCKS_PATH)
open class BlockController(private val blockService: BlockService) {

    @GetMapping
    @Operation(
        summary = "Get a range of collapsed blocks",
        description =
            """
            Returns collapsed (unexpanded) block headers, newest-first from the indexed head when
            `from` is omitted, or ascending from `from` when it is supplied. Pass
            `pagination.cursor` straight back as `from` to continue. `direction=ASC` requires a
            `from`, so the head range has exactly one canonical URL.

            `isTrunk` and `isFinalized` are omitted. Both are node-local, time-varying properties
            rather than block contents — `isTrunk` is a live comparison against the node's best
            chain, and finality lags the head by 360–540 blocks and is derived from validator
            stake weights — so neither can be served correctly from an index. Use a Thor node's
            `GET /blocks/{revision}` if you need them, for a single block, or to look up a block
            by ID.
        """,
    )
    @BlockNumberParameter(
        name = "from",
        description =
            "Block number to anchor the range on, inclusive. Defaults to the indexed head.",
    )
    @PaginationSize
    @PaginationSortDirection
    @CommonApiResponses
    open fun getBlocks(
        @ValidNonNegativeLong @RequestParam(required = false) from: Long?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): ResponseEntity<PaginatedResponse<IndexedBlock>> {
        val response = blockService.getBlocks(from, size, direction)
        val immutable = from != null && response.pagination.hasNext
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CACHE_CONTROL,
                if (immutable) IMMUTABLE_CACHE_CONTROL else HEAD_CACHE_CONTROL,
            )
            .body(response)
    }
}
