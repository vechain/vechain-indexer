package org.vechain.indexer.historical

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.VEVOTE_PATH
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils

@Tag(
    name = "Historical Proposal API",
    description = "Proposal API for querying Historical Proposal endpoint",
)
@Validated
@RestController
@Profile("historical-proposals")
@RequestMapping("$VEVOTE_PATH/historical_proposals")
open class HistoricalController(private val historicalApiService: HistoricalApiService) {

    @GetMapping
    @Operation(summary = "Fetch all historical proposals")
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "Success")])
    open fun getAllProposals(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): PaginatedResponse<HistoricalProposals> {
        val pageable = PaginationUtils.toPageable(page, size)
        val result = historicalApiService.findAll(pageable)
        return paginatedResponse(result)
    }
}
