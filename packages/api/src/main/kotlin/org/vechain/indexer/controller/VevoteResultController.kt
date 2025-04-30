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
import org.springframework.data.domain.SliceImpl
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.VEVOTE_PATH
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.model.VoteAggregate
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.paginatedResponse
import org.vechain.indexer.service.VevoteResultsService
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidPageSize

@Profile("vevote-result")
@Tag(name = "vevote results", description = "Query the sum of weight per choice")
@Validated
@RestController
@RequestMapping(VEVOTE_PATH)
open class VevoteResultController(private val resultsService: VevoteResultsService) {

    @GetMapping("proposal/results")
    @Operation(
        summary = "Returns a list of results on vote weight per choice",
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
        @RequestParam(required = false) direction: String?
    ): PaginatedResponse<VoteAggregate> {
        if (proposalId == null && choice == null) {
            throw BadRequestException("Either a proposalId or choice must be provided")
        }

        val pageable = toPageable(page, size, direction, VoteAggregate::blockNumber.name)

        val results: Slice<VoteAggregate> =
            when {
                proposalId != null && choice != null -> {
                    // Handle case where both proposalId and choice are provided
                    val singleResult =
                        resultsService.getResultsByProposalIdAndChoice(proposalId, choice, pageable)

                    if (singleResult != null) {
                        SliceImpl(listOf(singleResult), pageable, false)
                    } else {
                        SliceImpl(emptyList(), pageable, false)
                    }
                }
                proposalId != null -> resultsService.getResultsByProposalId(proposalId, pageable)
                else -> resultsService.getResultsByChoice(choice!!, pageable)
            }

        return paginatedResponse(results)
    }
}
