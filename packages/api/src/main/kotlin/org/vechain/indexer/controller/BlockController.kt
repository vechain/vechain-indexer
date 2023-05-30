package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.BLOCKS_PATH
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.model.Block
import org.vechain.indexer.service.BlockService
import org.vechain.indexer.utils.HexUtil

@Profile("blocks", "blocks-proxy")
@Tag(name = "Block", description = "Query blockchain blocks")
@RestController
@RequestMapping(BLOCKS_PATH)
open class BlockController(private val blockService: BlockService) {

    @GetMapping("{revision}")
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
    open fun getBlock(@PathVariable revision: String): Block {

        val block = if (revision == "best") {
            blockService.findBestBlock()
        } else if (HexUtil.isValidBlockID(revision)) {
            blockService.findById(revision)
        } else {
            // Try to parse to long
            try {
                val blockNumber = revision.toLong()
                blockService.findByBlockNumber(blockNumber)
            } catch (e: NumberFormatException) {
                throw BadRequestException("Invalid revision $revision")
            }
        }

        return block ?: throw ResourceNotFoundException("Block $revision not found")
    }
}
