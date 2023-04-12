package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.by
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.BLOCKS_PATH
import org.vechain.indexer.model.Block
import org.vechain.indexer.service.BlockService
import org.vechain.indexer.utils.PaginationUtils.toPageable

@Tag(name = "Block", description = "Query blockchain blocks")
@RestController
@RequestMapping(BLOCKS_PATH)
open class BlockController(private val blockService: BlockService) {

    @GetMapping
    @Operation(summary = "Get all blockchain blocks")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "page",
        schema = Schema(type = "Integer"),
        description = "The results page number",
        required = false,
        example = "0"
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "size",
        schema = Schema(type = "Integer"),
        description = "The results page size",
        required = false,
        example = "20"
    )
    open fun getAllBlocks(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): List<Block> {
        return blockService.findAll(toPageable(page, size, by(ASC, "number")))
    }
}