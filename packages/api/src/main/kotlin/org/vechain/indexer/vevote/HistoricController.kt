package org.vechain.indexer.vevote

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

@Tag(name = "VeVote Historic Proposals", description = "Query VeVote Historic Proposals")
@Validated
@RestController
@Profile("vevote", "vevote-historic-proposals")
@RequestMapping(VEVOTE_PATH)
open class HistoricController(private val historicApiService: HistoricApiService) {

    @GetMapping("/historic-proposals", "historical_proposals")
    @Operation(summary = "Fetch all historic proposals")
    @CommonApiResponses
    open fun getAllProposals(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) proposalId: String?,
        @RequestParam(required = false) size: Int?,
    ): PaginatedResponse<HistoricProposals> {
        val pageable = PaginationUtils.toPageable(page, size)
        val result = historicApiService.findAll(proposalId, pageable)
        return paginatedResponse(result)
    }
}
