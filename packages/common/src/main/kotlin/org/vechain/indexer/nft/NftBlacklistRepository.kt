package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("nfts", "history")
@Repository
interface NftBlacklistRepository : BaseIndexedRepository<NftBlacklist, String>
