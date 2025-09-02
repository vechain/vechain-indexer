package org.vechain.indexer.b3tr.voting

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.VOTING_PATH
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidProposalId

@Profile("b3tr", "b3tr-voting")
@Tag(name = "Voting", description = "Query voting data on VeBetterDAO.")
@Validated
@RestController
@RequestMapping(VOTING_PATH)
open class VotingController(private val votingService: VotingService) {

    @GetMapping("xallocations/{roundId}/results")
    @Operation(summary = "Get the results of XAllocation voting for a specific round.")
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "Success")])
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "roundId",
        description = "Round to filter by.",
        required = true,
        schema = Schema(type = "integer"),
        example = "2",
    )
    open fun getAllocationVoteResults(
        @PathVariable(required = true) roundId: Int
    ): List<XAllocResult> {
        return votingService.getXAllocResults(roundId)
    }

    @GetMapping("proposals/{proposalId}/results")
    @Operation(summary = "Get the results of a proposal.")
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "Success")])
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "proposalId",
        description = "Proposal ID to filter by.",
        required = true,
        schema = Schema(type = "string", pattern = ProposalId.REGEX),
    )
    open fun getProposalResult(
        @ValidProposalId @PathVariable(required = true) proposalId: ProposalId
    ): List<ProposalResult> {
        return votingService.getProposalResult(proposalId.value)
    }

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
        schema = Schema(type = "string", pattern = ProposalId.REGEX),
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "voter",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "Voter address to filter by.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "support",
        schema = Schema(type = "string", allowableValues = ["FOR", "AGAINST", "ABSTAIN"]),
        description = "Filter by support.",
        required = false,
    )
    @PaginationParameters
    open fun getComments(
        @ValidProposalId @RequestParam(required = false) proposalId: ProposalId?,
        @ValidAddress @RequestParam(required = false) voter: Address?,
        @RequestParam(required = false) support: Support?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ProposalComment> {
        val pageable = toPageable(page, size, direction, ProposalComment::blockNumber.name)
        // Either a proposalId or voter must be provided. Return an appropriate response code if
        // not.
        val result =
            if (proposalId == null && voter == null) {
                throw BadRequestException("Either a proposalId or voter address must be provided")
            } else if (proposalId != null && voter != null) {
                votingService.getComments(proposalId.value, voter.value, support, pageable)
            } else if (proposalId != null) {
                votingService.getComments(proposalId.value, support, pageable)
            } else {
                votingService.getCommentsForVoter(voter!!.value, support, pageable)
            }

        return paginatedResponse(result)
    }
}
