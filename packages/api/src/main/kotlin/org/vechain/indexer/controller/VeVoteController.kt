package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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
import org.vechain.indexer.model.vevote.Support
import org.vechain.indexer.model.vevote.VeVoteProposalComment
import org.vechain.indexer.model.vevote.VeVoteProposalResults
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
        name = "support",
        schema = Schema(implementation = Support::class),
        description = "Filter by support: AGAINST, FOR, or ABSTAIN.",
        required = false,
    )
    @PaginationParameters
    open fun getComments(
        @RequestParam(required = false) proposalId: String?,
        @ValidAddress @RequestParam(required = false) voter: Address?,
        @RequestParam(required = false) support: Support?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<VeVoteProposalComment> {
        val pageable = toPageable(page, size, direction, VeVoteProposalComment::blockNumber.name)

        // Either a proposalId or voter or support must be provided
        val result =
            when {
                proposalId != null && voter != null && support != null ->
                    vevoteService.getCommentsByProposalAndVoterAndSupport(
                        proposalId,
                        voter.value,
                        support,
                        pageable,
                    )
                proposalId != null && voter != null ->
                    vevoteService.getCommentsByProposalAndVoter(proposalId, voter.value, pageable)
                proposalId != null && support != null ->
                    vevoteService.getCommentsByProposalAndSupport(proposalId, support, pageable)
                proposalId != null -> vevoteService.getCommentsByProposalId(proposalId, pageable)
                voter != null && support != null ->
                    vevoteService.getCommentsByVoterAndSupport(voter.value, support, pageable)
                voter != null -> vevoteService.getCommentsByVoter(voter.value, pageable)
                else -> // only support is not null
                vevoteService.getCommentsBySupport(support!!, pageable)
            }
        return paginatedResponse(result)
    }

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
