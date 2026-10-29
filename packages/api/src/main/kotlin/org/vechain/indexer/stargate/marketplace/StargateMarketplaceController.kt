package org.vechain.indexer.stargate.marketplace

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.STARGATE_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.ListingSortParameter
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.TokenIdParameter
import org.vechain.indexer.docs.TokenLevelParameter
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidSortField
import org.vechain.indexer.validation.ValidTokenId
import org.vechain.indexer.validation.ValidTokenLevel

@Profile("stargate-marketplace")
@Tag(
    name = "Stargate Marketplace",
    description = "Stargate NFTs listed for resale on the marketplace contract.",
)
@Validated
@RestController
@RequestMapping("$STARGATE_PATH/marketplace")
open class StargateMarketplaceController(private val service: StargateMarketplaceService) {

    @GetMapping("/listings")
    @Operation(
        summary = "List Stargate NFTs for sale",
        description =
            "Open VET-priced listings whose NFT is still held by its seller. The marketplace " +
                "contract is the source of truth: verify a listing on-chain before buying.",
    )
    @TokenLevelParameter
    @AddressParameter(name = "seller", description = "Only listings by this address.")
    @ListingSortParameter
    @PaginationParameters
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getListings(
        @ValidTokenLevel @RequestParam(required = false) level: String?,
        @ValidAddress @RequestParam(required = false) seller: Address?,
        @ValidSortField(allowedValues = ["newest", "price"])
        @RequestParam(required = false, defaultValue = "newest")
        sort: String,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<MarketplaceListing> {
        val order = ListingSort.valueOf(sort.uppercase())
        return service.listings(
            level,
            seller?.value?.lowercase(),
            order,
            PaginationUtils.toPageable(page, size, direction, order.name),
        )
    }

    @GetMapping("/listings/{tokenId}")
    @Operation(
        summary = "Get a Stargate NFT's open listing",
        description =
            "The token's open listing, stale or not: `tokenExists` and `sellerIsOwner` say " +
                "whether it can still be bought. 404 when the token is not listed.",
    )
    @TokenIdParameter(required = true, `in` = ParameterIn.PATH)
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getListing(@ValidTokenId @PathVariable tokenId: String): MarketplaceListing =
        service.listing(tokenId)
}
