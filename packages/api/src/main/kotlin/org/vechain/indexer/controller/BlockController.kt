package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.by
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.BLOCKS_PATH
import org.vechain.indexer.model.Block
import org.vechain.indexer.pageable.PageablePage
import org.vechain.indexer.pageable.PageableSize
import org.vechain.indexer.service.BlockService
import org.vechain.indexer.utils.PaginationUtils.toPageable

@Tag(name = "Block", description = "Query blockchain blocks")
@RestController
@RequestMapping(BLOCKS_PATH)
open class BlockController(private val blockService: BlockService) {

    @GetMapping
    @Operation(summary = "Get all blockchain blocks")
    open fun getAllBlocks(
        @PageablePage @RequestParam(required = false) page: Int?,
        @PageableSize @RequestParam(required = false) size: Int?,
    ): List<Block> {
        return blockService.findAll(toPageable(page, size, by(ASC, "number")))
    }
}