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
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.VOLATILE_CACHE_CONTROL
import org.vechain.indexer.rest.cacheControlFor
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize

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
            Returns collapsed (unexpanded) block headers newest-first, starting at `from` and
            walking backwards, or starting at the indexed head when `from` is omitted. Pass
            `pagination.cursor` straight back as `from` to fetch the next page.

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
        description = "Block number to start from, inclusive. Defaults to the indexed head.",
    )
    @PaginationSize
    @CommonApiResponses
    open fun getBlocks(
        @ValidNonNegativeLong @RequestParam(required = false) from: Long?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
    ): ResponseEntity<PaginatedResponse<IndexedBlock>> {
        val range = blockService.getBlocks(from, size)
        val cacheControl = range.maxAgeSeconds?.let(::cacheControlFor) ?: VOLATILE_CACHE_CONTROL
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, cacheControl).body(range.page)
    }
}
