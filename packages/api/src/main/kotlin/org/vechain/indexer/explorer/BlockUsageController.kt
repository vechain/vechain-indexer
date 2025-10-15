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
            - Range ≤ 360 blocks (~1 hour): Returns all blocks (~360 data points)
            - Range ≤ 60,480 blocks (~1 week): Returns hourly values (~168 data points)
            - Range ≤ 259,200 blocks (~1 month): Returns daily values (~30 data points)
            - Range ≤ 3,153,600 blocks (~1 year): Returns weekly values (~52 data points)
            - Range > 3,153,600 blocks: Returns monthly values

            Values are represented as a monotonic cumulative counter which means the values increase over time. This is
            a semantic used by Grafana for example. It requires some processing on the client side to convert to a value
            for a given block.

            For example to get the gasUsed at block n you would need to do:

                gasUsedAtBlockN = gasUsedAtBlockN - gasUsedAtBlock(n-1)

            In the case where we return hourly/daily/weekly/monthly values only you can calculate an average over the
            block range. If the first record in the returned data is at block n and the next record is at block n + k:

                averageGasUsedPerBlock = (gasUsedAtBlock(n+k) - gasUsedAtBlockN) / k
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
