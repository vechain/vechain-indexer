package org.vechain.indexer.b3tr.xAlloc

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.X_ALLOC_PATH
import org.vechain.indexer.docs.CommonApiResponses

@Profile("b3tr", "b3tr-x-alloc")
@Tag(name = "B3TR - X-Allocations", description = "Query XAllocation voting data on VeBetterDAO.")
@Validated
@RestController
@RequestMapping(X_ALLOC_PATH)
open class XAllocController(private val xAllocService: XAllocService) {

    @GetMapping("{roundId}/results")
    @Deprecated(message = "Use GET /results?roundId={roundId} instead")
    @Operation(
        summary =
            "Get the results of XAllocation voting for a specific round. [DEPRECATED: Use GET /results endpoint instead]"
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "roundId",
        description = "Round to filter by.",
        required = true,
        schema = Schema(type = "integer"),
        example = "62",
    )
    @CommonApiResponses
    open fun getAllocationVoteResults(
        @PathVariable(required = true) roundId: Int
    ): List<XAllocResult> {
        return xAllocService.getXAllocResults(roundId)
    }

    @GetMapping("results")
    @Operation(
        summary =
            "Get XAllocation voting results grouped by app. If roundId is provided, returns results for that specific round. If not provided, returns aggregated results across all rounds for each app."
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "roundId",
        description =
            "Optional round ID to filter by. If not provided, returns aggregated results across all rounds.",
        required = false,
        schema = Schema(type = "integer"),
        example = "2",
    )
    @CommonApiResponses
    open fun getAllocationVoteResultsWithOptionalRound(
        @RequestParam(required = false) roundId: Int?
    ): List<XAllocResultResponse> {
        return if (roundId != null) {
            xAllocService.getXAllocResultsByRoundIdAsResponse(roundId)
        } else {
            xAllocService.getXAllocResultsAggregatedByAllApps()
        }
    }

    @GetMapping("apps/{appId}/results")
    @Operation(
        summary =
            "Get the results of XAllocation voting for a specific app. If roundId is provided, returns the specific round result. If not provided, returns aggregated results across all rounds."
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "appId",
        description = "App ID to filter by.",
        required = true,
        schema = Schema(type = "string"),
        example = "0x2fc30c2ad41a2994061efaf218f1d52dc92bc4a31a0f02a4916490076a7a393a",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "roundId",
        description =
            "Optional round ID to filter by. If not provided, returns aggregated results across all rounds.",
        required = false,
        schema = Schema(type = "integer"),
        example = "2",
    )
    @CommonApiResponses
    open fun getAllocationVoteResultsByAppId(
        @PathVariable(required = true) appId: String,
        @RequestParam(required = false) roundId: Int?,
    ): XAllocResultResponse? {
        return if (roundId != null) {
            xAllocService.getXAllocResultByAppIdAndRoundId(appId, roundId)
        } else {
            xAllocService.getXAllocResultsAggregatedByAppId(appId)
        }
    }
}
