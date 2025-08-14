package org.vechain.indexer.b3tr.gm.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.gm.GMLevelOverview
import org.vechain.indexer.b3tr.gm.GmLevelName
import org.vechain.indexer.b3tr.gm.GmNft
import org.vechain.indexer.thor.Address

@Profile("b3tr", "gm-nft")
@Repository
interface GmNftRepository : BasePagingAndSortingIndexedRepository<GmNft, String> {
    fun countByLevelAndOwnerNot(level: GmLevelName, owner: String = Address.ZERO_ADDRESS): Long

    @Aggregation(
        pipeline =
            [
                "{ \$match: { owner: { \$ne: '0x0000000000000000000000000000000000000000' } } }",
                "{ \$group: { _id: '\$level', totalNFTs: { \$sum: 1 } } }",
                "{ \$project: { _id: 0, level: '\$_id', totalNFTs: 1 } }",
                "{ \$sort: { totalNFTs: -1 } }",
            ]
    )
    fun levelCounts(): List<GMLevelOverview>
}
