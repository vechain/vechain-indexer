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
import org.vechain.indexer.model.NFT
import org.vechain.indexer.pageable.PageablePage
import org.vechain.indexer.pageable.PageableSize
import org.vechain.indexer.pageable.PageableSortDirection
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.utils.AddressUtil
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.Address
import org.vechain.indexer.validation.AddressNullable

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
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "Address of the NFT owner",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "contractAddress",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "The contract address",
        required = false,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    open fun getOwnedNFTs(
        @Address @RequestParam(required = true) address: String,
        @AddressNullable @RequestParam(required = false) contractAddress: String?,
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): List<NFT> {
        val pageable = toPageable(page, size, direction)

        return if (contractAddress.isNullOrEmpty()) nftService.findByOwner(address, pageable)
        else nftService.findByOwnerAndContractAddress(address, contractAddress, pageable)
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
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "The address of the NFTs owner",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    open fun getContractsByNFTOwner(
        @Address @RequestParam owner: String,
    ): List<String> {
        return nftService.findContractsByNFTOwner(owner)
    }

}
