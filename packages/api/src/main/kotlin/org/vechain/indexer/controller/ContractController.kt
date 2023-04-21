package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.vechain.indexer.constants.CONTRACTS_PATH
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.PaginationDetail
import org.vechain.indexer.pageable.PageablePage
import org.vechain.indexer.pageable.PageableSize
import org.vechain.indexer.pageable.PageableSortDirection
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.utils.*
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.Address

@Tag(name = "Contract", description = "Query on chain contracts")
@Validated
@RestController
@RequestMapping(CONTRACTS_PATH)
open class ContractController(private val contractService: ContractService) {

    @GetMapping
    @Operation(summary = "Get contract by address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "Address of the contract",
        required = true,
        example = "0x0000000000000000000000417574686f72697479"
    )
    open fun getContractByAddress(@Address @RequestParam(required = true) address: String): Contract {
        return contractService.findByAddress(address) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND, "Contract not found"
        )
    }

    @GetMapping("/origin")
    @Operation(summary = "Get all deployed contracts by an origin address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "Address of the origin",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    open fun getContractsByOrigin(
        @Address @RequestParam(required = true) address: String,
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<List<Contract>> {
        val resultsPage = contractService.findByCreator(
            address,
            toPageable(page, size, direction, "blockNumber", "txId", "address")
        )

        return PaginatedResponse(
            data = resultsPage.content,
            pagination = PaginationDetail(
                totalPages = resultsPage.totalPages,
                totalElements = resultsPage.totalElements
            )
        )
    }

}