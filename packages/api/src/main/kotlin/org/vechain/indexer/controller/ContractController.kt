package org.vechain.indexer.controller

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
import org.vechain.indexer.constants.CONTRACTS_PATH
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.ContractType
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.paginatedResponse
import org.vechain.indexer.pageable.PaginationParameters
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.utils.*
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidContractType
import org.vechain.indexer.validation.ValidPageSize

@Profile("contracts")
@Tag(name = "Contract", description = "Query on chain contracts")
@Validated
@RestController
@RequestMapping(CONTRACTS_PATH)
open class ContractController(private val contractService: ContractService) {

    @GetMapping("{address}")
    @Operation(summary = "Get contract by address")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "400", description = "Invalid address supplied"),
            ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "address",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "Address of the contract",
        required = true,
        example = "0x0000000000000000000000417574686f72697479"
    )
    open fun getContractByAddress(@ValidAddress @PathVariable address: Address): IndexedContract {
        return contractService.findByAddress(address)
            ?: throw ResourceNotFoundException("Contract with address $address was not found")
    }

    @GetMapping
    @Operation(summary = "Get all deployed contracts (by optional creator or type)")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "400", description = "Invalid address supplied"),
            ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "Address of the contract creator",
        required = false,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "type",
        schema =
            Schema(
                type = "string",
                allowableValues = ["VIP180", "VIP181", "VIP210", "ERC20", "ERC721", "ERC1155"]
            ),
        description = "The contract type",
        required = false
    )
    @PaginationParameters
    open fun getContractsByCreator(
        @ValidAddress @RequestParam(required = false) address: Address?,
        @ValidContractType @RequestParam(required = false) type: String?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedContract> {
        if (address == null && type.isNullOrEmpty())
            throw BadRequestException("Either contract address or contract type should be non null")

        return paginatedResponse(
            contractService.find(
                address,
                ContractType.byNameIgnoreCaseOrNull(type),
                toPageable(page, size, direction)
            )
        )
    }
}
