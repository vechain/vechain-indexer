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
import org.vechain.indexer.constants.FUNGIBLE_CONTRACTS_PATH
import org.vechain.indexer.model.Address
import org.vechain.indexer.service.FungibleTokenService
import org.vechain.indexer.validation.ValidAddress

@Profile("fungible-token-contracts")
@Tag(name = "Fungbile Tokens", description = "Query fungible tokens")
@Validated
@RestController
@RequestMapping(FUNGIBLE_CONTRACTS_PATH)
open class FungibleTokenController(private val fungibleTokenService: FungibleTokenService) {

    @GetMapping("/contracts")
    @Operation(summary = "Get all contracts addresses where the token owner has had some activity")
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "400", description = "Invalid address supplied"),
            ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "owner",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "The address of the fungible token owner",
        required = true,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
    )
    open fun getContractsByNFTOwner(
        @ValidAddress @RequestParam owner: Address,
    ): Set<String> {
        return fungibleTokenService.getContractsForOwner(owner.value)
    }
}
