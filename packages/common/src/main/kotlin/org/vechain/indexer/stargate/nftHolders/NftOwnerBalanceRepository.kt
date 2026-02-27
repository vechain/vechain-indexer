package org.vechain.indexer.stargate.nftHolders

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.BaseIndexedRepository

@Profile("stargate", "nft-owner-balance", "nft-holders-by-block")
interface NftOwnerBalanceRepository : BaseIndexedRepository<NftOwnerBalance, String> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'owner': { '\$in': ?0 }, 'blockNumber': { '\$lt': ?1 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$group': { '_id': '\$owner', 'doc': { '\$first': '\$\$ROOT' } } }",
                "{ '\$replaceRoot': { 'newRoot': '\$doc' } }",
            ]
    )
    fun findLatestBalancesBeforeBlock(
        owners: Collection<String>,
        blockNumber: Long,
    ): List<NftOwnerBalance>
}
