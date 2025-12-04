package org.vechain.indexer.explorer

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.EXPLORER_PATH
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.validation.ValidNonNegativeLong

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
            - Range ≤ 4,000 seconds: Returns all blocks (~360 data points)
            - Range ≤ 700,000 seconds: Returns hourly values (~168 data points)
            - Range ≤ 6,000,000 seconds: Returns daily values (~60 data points)
            - Range ≤ 35,000,000 seconds: Returns weekly values (~52 data points)
            - Range > 35,000,000 seconds: Returns monthly values

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
    @AfterParameter(name = "startTimestamp", required = true)
    @BeforeParameter(name = "endTimestamp", required = true)
    @CommonApiResponses
    open fun getBlockUsage(
        @ValidNonNegativeLong @RequestParam startTimestamp: Long,
        @ValidNonNegativeLong @RequestParam endTimestamp: Long,
    ): List<BlockUsage> {
        if (endTimestamp < startTimestamp) {
            throw BadRequestException(
                "endTimestamp must be greater than or equal to startTimestamp"
            )
        }
        return blockUsageService.getBlockUsage(startTimestamp, endTimestamp)
    }
}
