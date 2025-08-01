package org.vechain.indexer.historical

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.HISTORICAL_PROPOSAL_PATH
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils

@Tag(
    name = "Historical Proposal API",
    description = "Proposal API for querying Historical Proposal endpoint",
)
@Validated
@RestController
@Profile("historical-proposals")
@RequestMapping(HISTORICAL_PROPOSAL_PATH)
open class HistoricalController(private val historicalApiService: HistoricalApiService) {

    @GetMapping("{proposalId}")
    @Operation(summary = "Fetch any proposal by ID")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Success"),
                ApiResponse(responseCode = "400", description = "Invalid proposal ID format"),
            ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "proposalId",
        description = "Proposal ID to fetch proposal",
        required = true,
        schema = Schema(type = "string"),
    )
    open fun getProposalById(
        @PathVariable proposalId: String,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): PaginatedResponse<HistoricalProposals> {
        try {
            println("=== API Call Debug ===")
            println("proposalId: $proposalId")
            println("page: $page")
            println("size: $size")

            val pageable = PaginationUtils.toPageable(page, size)
            println("pageable: $pageable")

            val result = historicalApiService.findByProposalId(proposalId, pageable)
            println("result: $result")
            println("result content: ${result.content}")

            val response = paginatedResponse(result)
            println("response: $response")
            println("=== End API Call Debug ===")

            return response
        } catch (e: Exception) {
            println("=== API Error ===")
            println("Error: ${e.message}")
            e.printStackTrace()
            println("=== End API Error ===")
            throw e
        }
    }
}
