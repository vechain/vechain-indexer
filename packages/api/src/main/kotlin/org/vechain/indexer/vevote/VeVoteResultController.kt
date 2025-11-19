package org.vechain.indexer.vevote

import io.swagger.v3.oas.annotations.*
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Slice
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.VEVOTE_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.ProposalIdParameter
import org.vechain.indexer.docs.SupportParameter
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidProposalId

@Profile("vevote", "vevote-results")
@Tag(name = "VeVote", description = "Indexer API for VeVote.")
@Validated
@RestController
@RequestMapping(VEVOTE_PATH)
open class VeVoteResultController(private val resultService: VeVoteResultsService) {
    @GetMapping("proposal/results")
    @Operation(summary = "Returns a list of results on vote weight per support")
    @ProposalIdParameter
    @SupportParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getResults(
        @ValidProposalId @RequestParam(required = false) proposalId: String?,
        @RequestParam(required = false) support: Support?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<VeVoteProposalResult> {
        if (proposalId == null && support == null) {
            throw BadRequestException("Either a proposalId or support must be provided")
        }

        val pageable = toPageable(page, size, direction, VeVoteProposalResult::blockNumber.name)

        val result: Slice<VeVoteProposalResult> =
            when {
                proposalId != null && support != null ->
                    resultService.getResultsByProposalIdAndSupport(proposalId, support, pageable)
                proposalId != null -> resultService.getResultsByProposalId(proposalId, pageable)
                else -> resultService.getResultsBySupport(support!!, pageable)
            }

        return paginatedResponse(result)
    }
}
