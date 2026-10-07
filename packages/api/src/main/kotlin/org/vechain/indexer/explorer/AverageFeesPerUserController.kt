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
open class AverageFeesPerUserController(
    private val averageFeesPerUserService: AverageFeesPerUserService
) {

    @GetMapping("/average-fees-per-user")
    @Operation(
        summary = "Get the average fee per user for each day",
        description =
            "Average fee per user for each UTC day in the range: the VTHO paid in fees that " +
                "day divided by the number of different addresses that sent transactions that " +
                "day. Each point covers its own day; it isn't a running total.",
    )
    @AfterParameter(name = "startTimestamp", required = true)
    @BeforeParameter(name = "endTimestamp", required = true)
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getAverageFeesPerUser(
        @ValidNonNegativeLong @RequestParam startTimestamp: Long,
        @ValidNonNegativeLong @RequestParam endTimestamp: Long,
    ): ResponseEntity<List<AverageFeesPerUser>> {
        TimeValidationUtils.validateTimestamps(
            startTimestamp,
            endTimestamp,
            "startTimestamp",
            "endTimestamp",
        )
        return cachedByAge(
            endTimestamp,
            averageFeesPerUserService.getAverageFeesPerUser(startTimestamp, endTimestamp),
        )
    }
}
