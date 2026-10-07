package org.vechain.indexer.blocks

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
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
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.cachedByAge
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize

@Profile("blocks")
@Tag(name = "Blocks", description = "Block headers.")
@Validated
@RestController
@RequestMapping(BLOCKS_PATH)
open class BlockController(private val blockService: BlockService) {

    @GetMapping
    @Operation(
        summary = "List block headers",
        description =
            """
            Block headers, newest first. Starts at block `from`, or the newest indexed block if you
            leave it out, and works backwards. For the next page, pass `pagination.cursor` as
            `from`.

            Each block includes `clauseCount` and `totalVthoPaid` (hex, in wei), added up over its
            transactions.

            `isTrunk` and `isFinalized` aren't returned: they change over time and only a node knows
            them. Ask a Thor node's `GET /blocks/{revision}` for those, or to look up a block by ID.
            """,
    )
    @BlockNumberParameter(
        name = "from",
        description =
            "First block to return, the newest on the page. Defaults to the newest indexed " +
                "block.",
    )
    @PaginationSize
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getBlocks(
        @ValidNonNegativeLong @RequestParam(required = false) from: Long?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
    ): ResponseEntity<PaginatedResponse<IndexedBlock>> {
        val range = blockService.getBlocks(from, size)
        return cachedByAge(range.settledAt, range.page)
    }
}
