package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.vechain.indexer.constants.BLOCKS_PATH
import org.vechain.indexer.model.Block
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.BLOCKS_PATH
import org.vechain.indexer.model.Block
import org.vechain.indexer.pageable.PageablePage
import org.vechain.indexer.pageable.PageableSize
import org.vechain.indexer.pageable.PageableSortDirection
import org.vechain.indexer.service.BlockService

@Tag(name = "Block", description = "Query blockchain blocks")
@RestController
@RequestMapping(BLOCKS_PATH)
open class BlockController(private val blockService: BlockService) {

    @GetMapping
    @Operation(summary = "Get a block by block number or block id or best block")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "404", description = "Block not found")
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "revision",
        description = "block ID or number, or 'best' stands for latest block",
        required = true,
        example = "best"
    )
    open fun getBlock(
        @RequestParam revision: String,
    ): Block {
        return blockService.findBlock(revision) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND, "Block not found"
        )
    }
}