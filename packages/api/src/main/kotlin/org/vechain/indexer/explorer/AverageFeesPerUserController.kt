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
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidNonNegativeLong

@Profile("explorer", "average-fees-per-user")
@Tag(name = "Explorer", description = "Blockchain explorer analytics")
@Validated
@RestController
@RequestMapping(EXPLORER_PATH)
open class AverageFeesPerUserController(
    private val averageFeesPerUserService: AverageFeesPerUserService
) {

    @GetMapping("/average-fees-per-user")
    @Operation(
        summary = "Get daily average fees per user for a timestamp range",
        description =
            """
            Returns daily AFPU (Average Fees Per User) points for the requested timestamp range.

            AFPU is computed per UTC day as:
                total fees paid that day / distinct transaction origins that day

            Values are daily period metrics, not cumulative counters. The source fee amount uses the
            transaction `paid` value in the native gas token VTHO.
        """,
    )
    @AfterParameter(name = "startTimestamp", required = true)
    @BeforeParameter(name = "endTimestamp", required = true)
    @CommonApiResponses
    open fun getAverageFeesPerUser(
        @ValidNonNegativeLong @RequestParam startTimestamp: Long,
        @ValidNonNegativeLong @RequestParam endTimestamp: Long,
    ): List<AverageFeesPerUser> {
        TimeValidationUtils.validateTimestamps(startTimestamp, endTimestamp)
        return averageFeesPerUserService.getAverageFeesPerUser(startTimestamp, endTimestamp)
    }
}
