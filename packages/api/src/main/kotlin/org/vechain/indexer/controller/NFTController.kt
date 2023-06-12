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
import org.vechain.indexer.constants.NFTS_PATH
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.paginatedResponse
import org.vechain.indexer.pageable.PaginationParameters
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.utils.AddressUtils
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.Address
import org.vechain.indexer.validation.AddressNullable
import org.vechain.indexer.validation.ValidPageSize

@Profile("nft-events")
@Tag(name = "NFT", description = "Query on chain NFTs")
@Validated
@RestController
@RequestMapping(NFTS_PATH)
open class NFTController(private val nftService: NFTService) {

    @GetMapping
    @Operation(summary = "Get all NFTs owned by an address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtils.REGEX),
        description = "Address of the NFT owner",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "contractAddress",
        schema = Schema(type = "string", pattern = AddressUtils.REGEX),
        description = "The contract address",
        required = false,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    @PaginationParameters
    open fun getOwnedNFTs(
        @Address @RequestParam address: String,
        @AddressNullable @RequestParam(required = false) contractAddress: String?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedNFT> {
        val pageable = toPageable(page, size, direction)

        return if (contractAddress.isNullOrEmpty()) {
            paginatedResponse(nftService.findByOwner(address, pageable))
        } else {
            paginatedResponse(nftService.findByOwnerAndContractAddress(address, contractAddress, pageable))
        }
    }

    @GetMapping("/contracts")
    @Operation(summary = "Get all contracts addresses by NFT owner")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "owner",
        schema = Schema(type = "string", pattern = AddressUtils.REGEX),
        description = "The address of the NFTs owner",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    @PaginationParameters
    open fun getContractsByNFTOwner(
        @Address @RequestParam owner: String,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<String> {
        val pageable = toPageable(page, size, direction)

        return paginatedResponse(nftService.findContractsByNFTOwner(owner, pageable))
    }

}
