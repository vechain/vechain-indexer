package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.by
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.CONTRACTS_PATH
import org.vechain.indexer.model.Contract
import org.vechain.indexer.pageable.PageablePage
import org.vechain.indexer.pageable.PageableSize
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.utils.*
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.Address

@Tag(name = "Contract", description = "Query on chain contracts")
@Validated
@RestController
@RequestMapping(CONTRACTS_PATH)
open class ContractController(private val contractService: ContractService) {

    @GetMapping("{address}")
    @Operation(summary = "Get all deployed contracts by an origin address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "Address of the origin",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    @PageablePage
    @PageableSize
    open fun getContractsByOrigin(
        @Address @PathVariable address: String,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): List<Contract> {
        return contractService.findByCreator(
            address,
            toPageable(page, size, by(ASC, "blockNumber", "txId", "address"))
        )
    }

}