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
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.constants.NFTS_PATH
import org.vechain.indexer.docs.AddressListParameter
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.NftHistoryEventNameParameter
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.TokenIdParameter
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.model.Views
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidAddressList
import org.vechain.indexer.validation.ValidNftHistoryEventName
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidTokenId

@Profile("nfts")
@Tag(name = "NFT", description = "Query on chain NFTs")
@Validated
@RestController
@RequestMapping(NFTS_PATH)
open class NftController(
    private val nftService: NftService,
    private val nftHistoryService: NftHistoryService,
) {

    @GetMapping
    @JsonView(Views.Public::class)
    @Operation(summary = "Get all NFTs owned by an address")
    @TokenIdParameter
    @AddressListParameter(
        name = "excludeCollections",
        description = "The addresses of the collections to exclude. Max 20 collections.",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getOwnedNFTs(
        @AddressParameter(required = true, description = "Address of the NFT owner")
        @ValidAddress
        @RequestParam
        address: Address,
        @AddressParameter(name = "contractAddress")
        @ValidAddress
        @RequestParam(required = false)
        contractAddress: Address?,
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
    @AddressListParameter(
        name = "excludeCollections",
        description = "The addresses of the collections to exclude. Max 20 collections.",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getContractsByNFTOwner(
        @AddressParameter(name = "owner", required = true)
        @ValidAddress
        @RequestParam
        owner: Address,
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

    @GetMapping("/history")
    @Operation(
        summary = "Get NFT token history",
        description =
            "Retrieve NFT transfer and sale history for a specific contract and token ID. " +
                "If eventName is omitted, the response includes both TRANSFER_NFT and NFT_SALE events.",
    )
    @AddressParameter(name = "contractAddress", required = true)
    @TokenIdParameter(required = true)
    @NftHistoryEventNameParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getNftHistory(
        @ValidAddress @RequestParam contractAddress: Address,
        @ValidTokenId @RequestParam tokenId: String,
        @ValidNftHistoryEventName
        @RequestParam(name = "eventName", required = false)
        eventName: List<String>?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int? = DEFAULT_PAGE_SIZE,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedHistoryEvent> {
        TimeValidationUtils.validateTimestamps(after, before)

        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                IndexedHistoryEvent::blockTimestamp.name,
            )

        return paginatedResponse(
            nftHistoryService.findTokenHistory(
                contractAddress = contractAddress,
                tokenId = tokenId,
                eventNames = eventName,
                before = before,
                after = after,
                pageable = pageable,
            )
        )
    }
}
