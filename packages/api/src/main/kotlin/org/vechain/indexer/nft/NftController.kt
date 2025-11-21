package org.vechain.indexer.nft

import com.fasterxml.jackson.annotation.JsonView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.NFTS_PATH
import org.vechain.indexer.docs.AddressListParameter
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.TokenIdParameter
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.model.Views
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidAddressList
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidTokenId

@Profile("nfts")
@Tag(name = "NFT", description = "Query on chain NFTs")
@Validated
@RestController
@RequestMapping(NFTS_PATH)
open class NftController(private val nftService: NftService) {

    @GetMapping
    @JsonView(Views.Public::class)
    @Operation(summary = "Get all NFTs owned by an address")
    @AddressParameter(required = true, description = "Address of the NFT owner")
    @AddressParameter(name = "contractAddress")
    @TokenIdParameter
    @AddressListParameter(
        name = "excludeCollections",
        description = "The addresses of the collections to exclude. Max 20 collections.",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getOwnedNFTs(
        @ValidAddress @RequestParam address: Address,
        @ValidAddress @RequestParam(required = false) contractAddress: Address?,
        @ValidTokenId @RequestParam(required = false) tokenId: String?,
        @ValidAddressList @RequestParam(required = false) excludeCollections: List<Address>?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedNft> {
        val pageable = PaginationUtils.toPageable(page, size, direction)

        return paginatedResponse(
            nftService.findOwnedNfts(
                address,
                contractAddress,
                tokenId,
                excludeCollections,
                pageable,
            )
        )
    }

    @GetMapping("/contracts")
    @Operation(summary = "Get all contracts addresses by NFT owner")
    @AddressParameter(name = "owner", required = true)
    @AddressListParameter(
        name = "excludeCollections",
        description = "The addresses of the collections to exclude. Max 20 collections.",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getContractsByNFTOwner(
        @ValidAddress @RequestParam owner: Address,
        @ValidAddressList @RequestParam(required = false) excludeCollections: List<Address>?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<String> {
        val pageable = PaginationUtils.toPageable(page, size, direction)

        return paginatedResponse(
            nftService.findContractsByNftOwner(owner, excludeCollections, pageable)
        )
    }
}
