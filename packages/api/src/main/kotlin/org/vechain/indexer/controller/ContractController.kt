package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.model.Contract
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.utils.*
import org.vechain.indexer.validation.Address

@Tag(name = "Contract", description = "Query on chain contracts")
@Validated
@RestController
@RequestMapping(API_PATH + CONTRACTS_PATH)
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
    open fun getContractsByOrigin(
        @Address @PathVariable address: String
    ): List<Contract> {
        return contractService.findByCreator(address)
    }

}