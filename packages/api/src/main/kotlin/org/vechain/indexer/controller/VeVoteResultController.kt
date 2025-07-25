package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.*
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Slice
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.VEVOTE_PATH
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.paginatedResponse
import org.vechain.indexer.model.vevote.*
import org.vechain.indexer.service.VeVoteResultsService
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidPageSize

@Profile("vevote-results")
@Tag(name = "VeVote", description = "Indexer API for VeVote.")
@Validated
@RestController
@RequestMapping(VEVOTE_PATH)
open class VeVoteResultController(private val resultService: VeVoteResultsService) {
    @GetMapping("proposal/results")
    @Operation(summary = "Returns a list of results on vote weight per support")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Success"),
                ApiResponse(
                    responseCode = "400",
                    description = "A valid proposalId or voter address must be provided",
                ),
            ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "proposalId",
        description = "Proposal ID to filter by.",
        required = false,
        schema = Schema(type = "string"),
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "support",
        schema = Schema(implementation = Support::class),
        description = "Filter by support: AGAINST, FOR, or ABSTAIN.",
        required = false,
    )
    @PaginationParameters
    open fun getResults(
        @RequestParam(required = false) proposalId: String?,
        @RequestParam(required = false) support: Support?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<VeVoteProposalResults> {
        if (proposalId == null && support == null) {
            throw BadRequestException("Either a proposalId or support must be provided")
        }

        val pageable = toPageable(page, size, direction, VeVoteProposalResults::blockNumber.name)

        val result: Slice<VeVoteProposalResults> =
            when {
                proposalId != null && support != null ->
                    resultService.getResultsByProposalIdAndSupport(proposalId, support, pageable)
                proposalId != null -> resultService.getResultsByProposalId(proposalId, pageable)
                else -> resultService.getResultsBySupport(support!!, pageable)
            }

        return paginatedResponse(result)
    }
}
