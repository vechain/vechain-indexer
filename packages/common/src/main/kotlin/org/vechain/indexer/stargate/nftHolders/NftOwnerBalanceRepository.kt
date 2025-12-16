package org.vechain.indexer.stargate.nftHolders

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.MongoRepository

@Profile("stargate", "nft-holders-by-block")
interface NftOwnerBalanceRepository : MongoRepository<NftOwnerBalance, String> {
    fun findByOwnerIn(owners: Collection<String>): List<NftOwnerBalance>
}
