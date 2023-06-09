package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.BLOCKS_PATH
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.model.IndexedBlock
import org.vechain.indexer.service.BlockService
import org.vechain.indexer.utils.HexUtils
import org.vechain.indexer.utils.RevisionUtils
import org.vechain.indexer.validation.Revision

@Profile("blocks", "blocks-proxy")
@Tag(name = "Block", description = "Query blockchain blocks")
@Validated
@RestController
@RequestMapping(BLOCKS_PATH)
open class BlockController(private val blockService: BlockService) {

    @GetMapping("{revision}")
    @Operation(summary = "Get a block by ID, number, 'best' for the latest block or 'finalized' for latest finalized block.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "404", description = "Block not found")
        ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "revision",
        description = "block ID, number, 'best' for the latest block or 'finalized' for latest finalized block",
        required = true,
        example = "best"
    )
    open fun getBlock(@Revision @PathVariable revision: String): IndexedBlock {

        val normalisedRevision = RevisionUtils.normalise(revision)

        val block = when {
            normalisedRevision == "best" -> blockService.findBestBlock()
            normalisedRevision == "finalized" -> blockService.findFinalizedBlock()
            HexUtils.isValidBlockID(normalisedRevision) -> blockService.findById(normalisedRevision)
            else -> {
                // Try parse to a long
                try {
                    val blockNumber = revision.toLong()
                    blockService.findByBlockNumber(blockNumber)
                } catch (e: NumberFormatException) {
                    throw BadRequestException("Invalid revision $revision")
                }
            }
        }

        return block ?: throw ResourceNotFoundException("Block $revision not found")
    }
}
