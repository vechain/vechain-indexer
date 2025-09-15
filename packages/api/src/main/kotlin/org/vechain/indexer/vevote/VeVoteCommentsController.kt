package org.vechain.indexer.vevote

import io.swagger.v3.oas.annotations.*
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.VEVOTE_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize

@Profile("vevote", "vevote-comments")
@Tag(name = "VeVote", description = "Indexer API for VeVote.")
@Validated
@RestController
@RequestMapping(VEVOTE_PATH)
open class VeVoteCommentsController(private val vevoteService: VeVoteService) {
    @GetMapping("proposals/comments")
    @Operation(summary = "Get comments for a proposal.")
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
    @CommonApiResponses
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
                else -> vevoteService.getCommentsBySupport(support!!, pageable)
            }

        return paginatedResponse(result)
    }
}
