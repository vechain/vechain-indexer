package org.vechain.indexer.vevote

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.VEVOTE_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.proposal.ProposalId
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidProposalId

@Tag(name = "VeVote Historic Proposals", description = "Query VeVote Historic Proposals")
@Validated
@RestController
@Profile("vevote", "vevote-historic-proposals")
@RequestMapping(VEVOTE_PATH)
open class HistoricController(private val historicApiService: HistoricApiService) {
    @GetMapping("/historic-proposals")
    @Operation(summary = "Fetch all historic proposals")
    @Parameter(
        name = "proposalId",
        description = "Proposal ID to filter by.",
        schema = Schema(type = "string", pattern = "^[0-9]+$"),
    )
    @AddressParameter(name = "contractAddress", description = "Filter by legacy contract address.")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "testProposals",
        description = "If true, include proposals that were used for testing.",
        required = false,
        schema = Schema(type = "boolean"),
    )
    @CommonApiResponses
    open fun getAllProposals(
        @ValidProposalId @RequestParam(required = false) proposalId: ProposalId?,
        @ValidAddress @RequestParam(required = false) contractAddress: Address?,
        @PositiveOrZero @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) testProposals: Boolean?,
    ): PaginatedResponse<HistoricProposals> {
        val pageable = PaginationUtils.toPageable(page, size)
        val result =
            historicApiService.findAll(proposalId?.value, contractAddress, testProposals, pageable)
        return paginatedResponse(result)
    }
}
