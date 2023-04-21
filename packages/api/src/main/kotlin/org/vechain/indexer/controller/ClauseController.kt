package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.by
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.CLAUSES_PATH
import org.vechain.indexer.model.PaginatedResponse
import org.vechain.indexer.model.PaginationDetail
import org.vechain.indexer.model.WrappedClause
import org.vechain.indexer.pageable.PageablePage
import org.vechain.indexer.pageable.PageableSize
import org.vechain.indexer.service.ClauseService
import org.vechain.indexer.utils.AddressUtil
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.Address

@Tag(name = "Clause", description = "Query on chain tx clauses")
@RestController
@RequestMapping(CLAUSES_PATH)
open class ClauseController(private val clauseService: ClauseService) {

    @GetMapping
    @Operation(summary = "Get clauses for address")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "Address of the clause origin or destination",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    open fun getClauses(
        @Address @RequestParam(required = true) address: String,
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
    ): PaginatedResponse<List<WrappedClause>> {
        val resultsPage =
            clauseService.findByAddress(address, toPageable(page, size, by(ASC, "blockNumber", "txId", "id")))

        return PaginatedResponse(
            data = resultsPage.content,
            pagination = PaginationDetail(
                totalPages = resultsPage.totalPages,
                totalElements = resultsPage.totalElements
            )
        )

    }

}