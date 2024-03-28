package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.CLAUSES_PATH
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.paginatedResponse2
import org.vechain.indexer.service.ClauseService
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize

@Profile("clauses")
@Tag(name = "Clause", description = "Query on chain tx clauses")
@Validated
@RestController
@RequestMapping(CLAUSES_PATH)
open class ClauseController(private val clauseService: ClauseService) {

    @GetMapping
    @Operation(summary = "Get clauses for address")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "Address of the clause origin or destination",
        required = true,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
    )
    @PaginationParameters
    open fun getClauses(
        @ValidAddress @RequestParam("address") address: Address,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?
    ): PaginatedResponse<IndexedClause> {
        return paginatedResponse2(
            clauseService.findByAddress(address, toPageable(page, size, direction))
        )
    }
}
