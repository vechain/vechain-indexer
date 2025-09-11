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
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.SupportParameter
import org.vechain.indexer.docs.WalletParameter
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
@RequestMapping(B3TR_PATH)
open class ProposalController(private val proposalService: ProposalService) {

    @GetMapping("proposals/{proposalId}/results")
    @Operation(summary = "Get the results of a proposal.")
    @ProposalIdParameter(required = true, `in` = ParameterIn.PATH)
    @CommonApiResponses
    open fun getProposalResult(
        @ValidProposalId @PathVariable(required = true) proposalId: ProposalId
    ): List<ProposalResult> {
        return proposalService.getProposalResult(proposalId.value)
    }

    @GetMapping("proposals/{proposalId}/comments")
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

    @GetMapping("users/{wallet}/proposal-comments")
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
