package org.vechain.indexer.controller

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
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.VevoteProposalComment
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.paginatedResponse
import org.vechain.indexer.service.VevoteService
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize

@Profile("vevote-events")
@Tag(name = "Vevote", description = "Query Vevote proposal comments")
@Validated
@RestController
@RequestMapping("/api/v1/vevote")
open class VevoteController(private val vevoteService: VevoteService) {

    @GetMapping("getComments")
    @Operation(
        summary = "Get comments for a proposal.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Success"),
                ApiResponse(
                    responseCode = "400",
                    description = "A valid proposalId or voter address must be provided"
                )
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
        @RequestParam(required = false) choice: Int?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?
    ): PaginatedResponse<VevoteProposalComment> {
        val pageable = toPageable(page, size, direction, VevoteProposalComment::blockNumber.name)

        // Either a proposalId or voter must be provided
        val result =
            if (proposalId == null && voter == null && choice == null) {
                throw BadRequestException(
                    "Either a proposalId, voter address, or choice must be provided"
                )
            } else if (proposalId != null && voter != null) {
                // Both proposalId and voter provided
                if (choice != null) {
                    // All three filters
                    vevoteService.getCommentsByProposalAndVoterAndChoice(
                        proposalId,
                        voter.value,
                        choice,
                        pageable
                    )
                } else {
                    // Just proposalId and voter
                    vevoteService.getCommentsByProposalAndVoter(proposalId, voter.value, pageable)
                }
            } else if (proposalId != null) {
                // Only proposalId provided
                if (choice != null) {
                    // Filter by proposalId and choice
                    vevoteService.getCommentsByProposalAndChoice(proposalId, choice, pageable)
                } else {
                    // Just proposalId
                    vevoteService.getCommentsByProposalId(proposalId, pageable)
                }
            } else if (voter != null) {
                // Only voter provided
                if (choice != null) {
                    // Filter by voter and choice
                    vevoteService.getCommentsByVoterAndChoice(voter.value, choice, pageable)
                } else {
                    // Just voter
                    vevoteService.getCommentsByVoter(voter.value, pageable)
                }
            } else {
                // Only choice provided
                vevoteService.getCommentsByChoice(choice!!, pageable)
            }

        return paginatedResponse(result)
    }
}
