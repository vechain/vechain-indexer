package org.vechain.indexer.stargate.marketplace

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.BigIntegerUtils
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("stargate-marketplace")
@Service
open class StargateMarketplaceService(private val repository: StargateMarketplaceReadRepository) {

    open fun listings(
        level: String?,
        seller: String?,
        sort: ListingSort,
        pageable: Pageable,
    ): PaginatedResponse<MarketplaceListing> {
        val tokenLevel = level?.let(TokenLevel::fromString)
        val slice =
            offsetSlice(pageable, sort.name) { offset, limit, direction ->
                repository.findValid(tokenLevel, seller, sort, direction, offset, limit)
            }
        return paginatedResponse(slice.map(MarketplaceListing::from))
    }

    open fun listing(tokenId: String): MarketplaceListing {
        val decimal = BigIntegerUtils.fromHexOrDecimal(tokenId).toString(10)
        val row =
            repository.findOpenByToken(decimal)
                ?: throw ResourceNotFoundException("Token $tokenId has no open listing")
        return MarketplaceListing.from(row)
    }
}
