package org.vechain.indexer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.CONTRACTS_PATH
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.rest.ContractType
import org.vechain.indexer.pageable.PageablePage
import org.vechain.indexer.pageable.PageableSize
import org.vechain.indexer.pageable.PageableSortDirection
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.utils.*
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.Address
import org.vechain.indexer.validation.AddressNullable

@Tag(name = "Contract", description = "Query on chain contracts")
@Validated
@RestController
@RequestMapping(CONTRACTS_PATH)
open class ContractController(private val contractService: ContractService) {

    @GetMapping("{address}")
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
    open fun getContractByAddress(@Address @PathVariable address: String): Contract {
        return contractService.findByAddress(address)
            ?: throw ResourceNotFoundException("Contract with address $address was not found")
    }

    @GetMapping
    @Operation(summary = "Get all deployed contracts (by optional creator or type)")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "Address of the contract creator",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "type",
        schema = Schema(
            type = "string",
            allowableValues = ["VIP180", "VIP181", "VIP210", "ERC20", "ERC721", "ERC1155"]
        ),
        description = "The contract type",
        required = false,
        example = "VIP180"
    )
    open fun getContractsByCreator(
        @AddressNullable @RequestParam(required = false) address: String?,
        @RequestParam(required = false) type: String?,
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): List<Contract> {
        validateContractType(type)

        return contractService.find(
            address,
            ContractType.byNameIgnoreCaseOrNull(type),
            toPageable(page, size, direction, "blockNumber", "txId", "address")
        )
    }

    private fun validateContractType(type: String?) {
        if (type != null && ContractType.byNameIgnoreCaseOrNull(type) == null) {
            throw BadRequestException("Invalid contract type parameter: $type")
        }
    }

}