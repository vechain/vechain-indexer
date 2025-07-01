package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.NFTBlacklist

@Profile("nft-events")
@Repository
interface NFTBlacklistRepository : BasePagingAndSortingIndexedRepository<NFTBlacklist, String>
