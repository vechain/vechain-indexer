package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("nfts", "history")
@Repository
interface NftBlacklistRepository : BasePagingAndSortingIndexedRepository<NftBlacklist, String> {
    fun findByIsBlacklisted(blacklisted: Boolean): List<NftBlacklist>
}
