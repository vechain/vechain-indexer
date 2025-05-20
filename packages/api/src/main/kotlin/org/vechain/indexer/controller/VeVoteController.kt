package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Slice
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.VEVOTE_PATH
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.paginatedResponse
import org.vechain.indexer.model.vevote.VeVoteProposalResults
import org.vechain.indexer.model.vevote.VevoteProposalComment
import org.vechain.indexer.service.VeVoteResultsService
import org.vechain.indexer.service.VeVoteService
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize

@Profile("vevote-comments", "vevote-results")
@Tag(name = "VeVote", description = "Indexer API for querying VeVote proposal information.")
@Validated
@RestController
@RequestMapping(VEVOTE_PATH)
open class VeVoteController(
    private val vevoteService: VeVoteService,
    private val resultService: VeVoteResultsService,
) {
    @GetMapping("proposals/comments")
    @Operation(summary = "Get comments for a proposal.")
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
        name = "voter",
        schema = Schema(type = "string"),
        description = "Voter address to filter by.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "choice",
        schema = Schema(type = "integer"),
        description = "Filter by specific choice number (e.g., 1, 2, 3, 4).",
        required = false,
    )
    @PaginationParameters
    open fun getComments(
        @RequestParam(required = false) proposalId: String?,
        @ValidAddress @RequestParam(required = false) voter: Address?,
        @RequestParam(required = false) @Max(32) choice: Int?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<VevoteProposalComment> {
        val pageable = toPageable(page, size, direction, VevoteProposalComment::blockNumber.name)

        // Either a proposalId or voter or choice must be provided
        val result =
            when {
                proposalId != null && voter != null && choice != null ->
                    vevoteService.getCommentsByProposalAndVoterAndChoice(
                        proposalId,
                        voter.value,
                        choice,
                        pageable,
                    )
                proposalId != null && voter != null ->
                    vevoteService.getCommentsByProposalAndVoter(proposalId, voter.value, pageable)
                proposalId != null && choice != null ->
                    vevoteService.getCommentsByProposalAndChoice(proposalId, choice, pageable)
                proposalId != null -> vevoteService.getCommentsByProposalId(proposalId, pageable)
                voter != null && choice != null ->
                    vevoteService.getCommentsByVoterAndChoice(voter.value, choice, pageable)
                voter != null -> vevoteService.getCommentsByVoter(voter.value, pageable)
                else -> // only choice is not null
                vevoteService.getCommentsByChoice(choice!!, pageable)
            }
        return paginatedResponse(result)
    }

    @GetMapping("proposal/results")
    @Operation(summary = "Returns a list of results on vote weight per choice")
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
        name = "choice",
        schema = Schema(type = "integer"),
        description = "Filter by specific choice number",
        required = false,
    )
    @PaginationParameters
    open fun getResults(
        @RequestParam(required = false) proposalId: String?,
        @RequestParam(required = false) choice: Int?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<VeVoteProposalResults> {
        if (proposalId == null && choice == null) {
            throw BadRequestException("Either a proposalId or choice must be provided")
        }

        val pageable = toPageable(page, size, direction, VeVoteProposalResults::blockNumber.name)

        val result: Slice<VeVoteProposalResults> =
            when {
                proposalId != null && choice != null ->
                    resultService.getResultsByProposalIdAndChoice(proposalId, choice, pageable)
                proposalId != null -> resultService.getResultsByProposalId(proposalId, pageable)
                else -> resultService.getResultsByChoice(choice!!, pageable)
            }

        return paginatedResponse(result)
    }
}
