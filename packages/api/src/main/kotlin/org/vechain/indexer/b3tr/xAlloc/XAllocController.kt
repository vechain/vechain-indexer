package org.vechain.indexer.b3tr.xAlloc

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
import org.vechain.indexer.constants.X_ALLOC_PATH

@Profile("b3tr", "b3tr-x-alloc")
@Tag(name = "B3TR", description = "B3TR related endpoints.")
@Tag(name = "B3TR X-Allocations", description = "Query XAllocation voting data on VeBetterDAO.")
@Validated
@RestController
@RequestMapping(X_ALLOC_PATH)
open class XAllocController(private val xAllocService: XAllocService) {

    @GetMapping("{roundId}/results")
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
        return xAllocService.getXAllocResults(roundId)
    }
}
