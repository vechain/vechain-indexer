package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
import org.vechain.indexer.pageable.PageableSortDirection
import org.vechain.indexer.service.ClauseService
import org.vechain.indexer.utils.PaginationUtils.toPageable

@Tag(name = "Clause", description = "Query on chain tx clauses")
@RestController
@RequestMapping(CLAUSES_PATH)
open class ClauseController(private val clauseService: ClauseService) {

    @GetMapping
    @Operation(summary = "Get all on chain tx clauses")
    open fun getAllClauses(
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<List<WrappedClause>> {
        val resultsPage = clauseService.findAll(toPageable(page, size, direction, "blockNumber", "txId", "id"))

        return PaginatedResponse(
            data = resultsPage.content,
            pagination = PaginationDetail(
                totalPages = resultsPage.totalPages,
                totalElements = resultsPage.totalElements
            )
        )

    }

}