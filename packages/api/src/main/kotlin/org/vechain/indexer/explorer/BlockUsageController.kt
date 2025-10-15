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
        summary = "Get block usage statistics for a timestamp range",
        description =
            """
            Returns cumulative block usage statistics (gas usage, transaction counts, etc.) for a given timestamp range.

            The API automatically determines the appropriate data granularity based on the size of the time range:
            - Range ≤ 1 hour (3,600 seconds): Returns all blocks (~360 data points)
            - Range ≤ 1 week (604,800 seconds): Returns hourly values (~168 data points)
            - Range ≤ 1 month (2,592,000 seconds): Returns daily values (~30 data points)
            - Range ≤ 1 year (31,536,000 seconds): Returns weekly values (~52 data points)
            - Range > 1 year: Returns monthly values

            Values are represented as a monotonic cumulative counter which means the values increase over time. This is
            a semantic used by Grafana for example. It requires some processing on the client side to convert to a value
            for a given timestamp.

            For example to get the gasUsed at timestamp t you would need to do:

                gasUsedAtTimestampT = gasUsedAtTimestampT - gasUsedAtTimestamp(t-1)

            In the case where we return hourly/daily/weekly/monthly values only you can calculate an average over the
            time range. If the first record in the returned data is at timestamp t1 and the next record is at timestamp t2:

                averageGasUsedPerSecond = (gasUsedAtT2 - gasUsedAtT1) / (t2 - t1)
        """,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "startTimestamp",
        schema = Schema(type = "integer", format = "int64", minimum = "0"),
        description = "The starting timestamp in seconds since Unix epoch (inclusive)",
        required = true,
        example = "1704067200",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "endTimestamp",
        schema = Schema(type = "integer", format = "int64", minimum = "0"),
        description =
            "The ending timestamp in seconds since Unix epoch (inclusive). Must be greater than or equal to startTimestamp.",
        required = true,
        example = "1704153600",
    )
    @CommonApiResponses
    open fun getBlockUsage(
        @RequestParam startTimestamp: Long,
        @RequestParam endTimestamp: Long,
    ): List<BlockUsage> {
        return blockUsageService.getBlockUsage(startTimestamp, endTimestamp)
    }
}
