package org.vechain.indexer.explorer

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.EXPLORER_PATH
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.cachedByAge
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidNonNegativeLong

@Profile("explorer")
@Tag(name = "Explorer", description = "Blockchain explorer analytics")
@Validated
@RestController
@RequestMapping(EXPLORER_PATH)
open class BlockUsageController(private val blockUsageService: BlockUsageService) {

    @GetMapping("/block-usage")
    @Operation(
        summary = "Get gas and transaction totals over time",
        description =
            """
            Gas used, transaction counts and other block totals over time, for charting.

            Point spacing depends on the length of the range:

            - up to 4,000 seconds (about an hour): every block
            - up to 700,000 seconds (about 8 days): hourly
            - up to 6,000,000 seconds (about 69 days): daily
            - up to 35,000,000 seconds (about 13 months): weekly
            - longer: monthly

            Each value is a running total since the start of the chain, so subtract one point from
            the next to get the amount in between. Divide that by the number of blocks between the
            points for a per-block average.

            The response also includes the last point at or before each end of the range, so a chart
            line reaches both edges.
            """,
    )
    @AfterParameter(name = "startTimestamp", required = true)
    @BeforeParameter(name = "endTimestamp", required = true)
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getBlockUsage(
        @ValidNonNegativeLong @RequestParam startTimestamp: Long,
        @ValidNonNegativeLong @RequestParam endTimestamp: Long,
    ): ResponseEntity<List<BlockUsage>> {
        TimeValidationUtils.validateTimestamps(
            startTimestamp,
            endTimestamp,
            "startTimestamp",
            "endTimestamp",
        )
        return cachedByAge(
            endTimestamp,
            blockUsageService.getBlockUsage(startTimestamp, endTimestamp),
        )
    }
}
