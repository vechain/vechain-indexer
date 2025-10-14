package org.vechain.indexer.explorer

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.EXPLORER_PATH
import org.vechain.indexer.docs.CommonApiResponses

@Profile("explorer", "block-usage")
@Tag(name = "Explorer", description = "Blockchain explorer analytics")
@Validated
@RestController
@RequestMapping(EXPLORER_PATH)
open class BlockUsageController(private val blockUsageService: BlockUsageService) {

    @GetMapping("/block-usage")
    @Operation(
        summary = "Get block usage statistics for a block range",
        description =
            """
            Returns cumulative block usage statistics (gas usage, transaction counts, etc.) for a given block range.

            The API automatically determines the appropriate data granularity based on the size of the block range:
            - Range ≤ 2,160 blocks (~6 hours): Returns all blocks (~2.2k data points)
            - Range ≤ 259,200 blocks (~1 month): Returns hourly aggregates (~720 data points)
            - Range ≤ 1,555,200 blocks (~6 months): Returns daily aggregates (~180 data points)
            - Range ≤ 6,307,200 blocks (~2 years): Returns weekly aggregates (~104 data points)
            - Range > 6,307,200 blocks: Returns monthly aggregates

            This ensures optimal performance and reasonable data point counts for visualization.
        """,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "startBlock",
        schema = Schema(type = "integer", format = "int64", minimum = "0"),
        description = "The starting block number (inclusive)",
        required = true,
        example = "10000000",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "endBlock",
        schema = Schema(type = "integer", format = "int64", minimum = "0"),
        description =
            "The ending block number (inclusive). Must be greater than or equal to startBlock.",
        required = true,
        example = "10010000",
    )
    @CommonApiResponses
    open fun getBlockUsage(
        @RequestParam startBlock: Long,
        @RequestParam endBlock: Long,
    ): List<BlockUsage> {
        return blockUsageService.getBlockUsage(startBlock, endBlock)
    }
}
