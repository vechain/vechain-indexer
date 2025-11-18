package org.vechain.indexer.b3tr.proposal

import ProposalIdParameter
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.constants.B3TR_PATH
import org.vechain.indexer.constants.B3TR_PATH_V2
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.SupportParameter
import org.vechain.indexer.docs.WalletParameter
import org.vechain.indexer.exception.NotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidProposalId

@Profile("b3tr", "b3tr-proposal")
@Tag(name = "B3TR - Governance Proposals", description = "Query voting data on VeBetterDAO.")
@Validated
@RestController
open class ProposalController(private val proposalService: ProposalService) {

    @GetMapping("$B3TR_PATH_V2/proposals/results")
    @Operation(summary = "Get all proposal results paginated.")
    @CommonApiResponses
    @PaginationParameters
    open fun getAllProposalResults(
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ProposalResult> {
        val pageable = toPageable(page, size, direction, ProposalResult::createdAtBlockNumber.name)
        return paginatedResponse(proposalService.getAllProposalResults(pageable))
    }

    @GetMapping("$B3TR_PATH_V2/proposals/{proposalId}/results")
    @Operation(summary = "Get the results of a proposal.")
    @ProposalIdParameter(required = true, `in` = ParameterIn.PATH)
    @CommonApiResponses
    open fun getProposalResult(
        @ValidProposalId @PathVariable(required = true) proposalId: ProposalId
    ): ProposalResult {
        return proposalService.getProposalResult(proposalId.value)
            ?: throw NotFoundException("Proposal result not found for id: ${proposalId.value}")
    }

    @GetMapping("$B3TR_PATH/proposals/{proposalId}/results")
    @Operation(summary = "Get the results of a proposal.")
    @ProposalIdParameter(required = true, `in` = ParameterIn.PATH)
    @CommonApiResponses
    @Deprecated("This api is deprecated in favour of the v2 endpoint.")
    open fun getProposalResultDeprecated(
        @ValidProposalId @PathVariable(required = true) proposalId: ProposalId
    ): List<ProposalResultDeprecated> {
        val result =
            proposalService.getProposalResult(proposalId.value)
                ?: throw NotFoundException("Proposal result not found for id: ${proposalId.value}")
        return result.toDeprecated()
    }

    @GetMapping("$B3TR_PATH/proposals/{proposalId}/comments")
    @Operation(summary = "Get the comments for a proposal.")
    @ProposalIdParameter(required = true, `in` = ParameterIn.PATH)
    @SupportParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getProposalComments(
        @ValidProposalId @PathVariable(required = true) proposalId: ProposalId,
        @RequestParam(required = false) support: Support?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ProposalComment> {
        val pageable = toPageable(page, size, direction, ProposalComment::blockNumber.name)
        val result = proposalService.getComments(proposalId.value, support, pageable)
        return paginatedResponse(result)
    }

    @GetMapping("$B3TR_PATH/users/{wallet}/proposals/comments")
    @Operation(summary = "Get the comments made by a user on proposals.")
    @WalletParameter(required = true, `in` = ParameterIn.PATH)
    @ProposalIdParameter
    @SupportParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getUserProposalComments(
        @ValidAddress @PathVariable wallet: Address,
        @RequestParam(required = false) proposalId: ProposalId?,
        @RequestParam(required = false) support: Support?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ProposalComment> {
        val pageable = toPageable(page, size, direction, ProposalComment::blockNumber.name)
        val result =
            if (proposalId != null) {
                proposalService.getComments(
                    proposalId = proposalId.value,
                    voter = wallet.value,
                    support = support,
                    pageable = pageable,
                )
            } else {
                proposalService.getCommentsForVoter(
                    voter = wallet.value,
                    support = support,
                    pageable = pageable,
                )
            }
        return paginatedResponse(result)
    }
}
