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
import org.vechain.indexer.constants.NFTS_PATH
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.model.rest.PaginationDetail
import org.vechain.indexer.pageable.PageablePage
import org.vechain.indexer.pageable.PageableSize
import org.vechain.indexer.pageable.PageableSortDirection
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.utils.AddressUtil
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.Address
import org.vechain.indexer.validation.OptionalAddresses


@Tag(name = "NFT", description = "Query on chain NFTs")
@Validated
@RestController
@RequestMapping(NFTS_PATH)
open class NFTController(private val nftService: NFTService) {

    @GetMapping("{address}")
    @Operation(summary = "Get all NFTs owned by an address")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "400", description = "Invalid address supplied"),
        ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "address",
        schema = Schema(type = "string", pattern = AddressUtil.REGEX),
        description = "Address of the NFT owner",
        required = true,
        example = "0x435933c8064b4Ae76bE665428e0307eF2cCFBD68"
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "contractAddresses",
        schema = Schema(type = "list of strings"),
        description = "The contract addresses to include",
        required = false,
        example = "['0x435933c8064b4Ae76bE665428e0307eF2cCFBD68']"
    )
    open fun getOwnedNFTs(
        @Address @PathVariable address: String,
        @OptionalAddresses @RequestParam(required = false) contractAddresses: List<String>?,
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<List<NFT>> {
        val resultsPage = if (contractAddresses.isNullOrEmpty()) {
            nftService.findByOwner(address, toPageable(page, size, direction, "blockNumber", "txId", "id"))
        } else {
            nftService.findByOwnerAndContractAddresses(
                address,
                contractAddresses,
                toPageable(page, size, direction, "blockNumber", "txId", "id")
            )
        }

        return PaginatedResponse(
            data = resultsPage.content,
            pagination = PaginationDetail(
                totalPages = resultsPage.totalPages,
                totalElements = resultsPage.totalElements
            )
        )
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
        @PageableSize @RequestParam(required = false) page: Int?,
        @PageablePage @RequestParam(required = false) size: Int?,
        @PageableSortDirection @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<List<String>> {
        val resultsPage =
            nftService.findContractsByNFTOwner(owner, toPageable(page, size, direction, "blockNumber", "txId", "id"))

        return PaginatedResponse(
            data = resultsPage.content.map { it.contractAddress },
            pagination = PaginationDetail(
                totalPages = resultsPage.totalPages,
                totalElements = resultsPage.totalElements
            )
        )
    }

}