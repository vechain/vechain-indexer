package org.vechain.indexer.controller

import com.fasterxml.jackson.annotation.JsonView
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
import org.vechain.indexer.constants.NFTS_PATH
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.paginatedResponse
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.thor.model.Views

@Profile("nft-events")
@Tag(name = "NFT", description = "Query on chain NFTs")
@Validated
@RestController
@RequestMapping(NFTS_PATH)
open class NFTController(private val nftService: NFTService) {

    @GetMapping
    @JsonView(Views.Public::class)
    @Operation(summary = "Get all NFTs owned by an address")
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
        description = "Address of the NFT owner",
        required = true,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "contractAddress",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "The contract address",
        required = false
    )
    @PaginationParameters
    open fun getOwnedNFTs(
        @ValidAddress @RequestParam address: Address,
        @ValidAddress @RequestParam(required = false) contractAddress: Address?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedNFT> {
        val pageable = toPageable(page, size, direction)

        val resultsPage =
            if (contractAddress == null) {
                nftService.findByOwner(address, pageable)
            } else {
                nftService.findByOwnerAndContractAddress(address, contractAddress, pageable)
            }

        return paginatedResponse(resultsPage)
    }

    @GetMapping("/contracts")
    @Operation(summary = "Get all contracts addresses by NFT owner")
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
        description = "The address of the NFTs owner",
        required = true,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
    )
    @PaginationParameters
    open fun getContractsByNFTOwner(
        @ValidAddress @RequestParam owner: Address,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<String> {
        val pageable = toPageable(page, size, direction)

        return paginatedResponse(nftService.findContractsByNFTOwner(owner, pageable))
    }
}
