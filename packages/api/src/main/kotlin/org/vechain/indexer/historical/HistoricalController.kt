package org.vechain.indexer.historical

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.VEVOTE_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils

@Tag(name = "VeVote Historical Proposals", description = "Query VeVote Historical Proposals")
@Validated
@RestController
@Profile("historical-proposals")
@RequestMapping("$VEVOTE_PATH/historical_proposals")
open class HistoricalController(private val historicalApiService: HistoricalApiService) {

    @GetMapping
    @Operation(summary = "Fetch all historical proposals")
    @CommonApiResponses
    open fun getAllProposals(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) proposalId: String?,
        @RequestParam(required = false) size: Int?,
    ): PaginatedResponse<HistoricalProposals> {
        val pageable = PaginationUtils.toPageable(page, size)
        val result = historicalApiService.findAll(proposalId, pageable)
        return paginatedResponse(result)
    }
}
