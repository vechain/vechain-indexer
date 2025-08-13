package org.vechain.indexer.b3tr.gm.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.gm.GMLevelOverview
import org.vechain.indexer.b3tr.gm.GmLevelName
import org.vechain.indexer.b3tr.gm.GmNft

@Profile("b3tr", "gm-nft")
@Repository
interface GmNftRepository : BasePagingAndSortingIndexedRepository<GmNft, String> {
    fun countByLevel(level: GmLevelName): Long

    @Aggregation(
        pipeline =
            [
                "{ \$group: { _id: '\$level', totalNFTs: { \$sum: 1 } } }",
                "{ \$project: { _id: 0, level: '\$_id', totalNFTs: 1 } }",
                "{ \$sort: { totalNFTs: -1 } }",
            ]
    )
    fun levelCounts(): List<GMLevelOverview>
}
