package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.NftBlacklist

@Profile("nfts")
@Repository
interface NftBlacklistRepository : BasePagingAndSortingIndexedRepository<NftBlacklist, String>
