package org.vechain.indexer.repos

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.NFT

@Repository
interface NFTRepo : CrudRepository<NFT, String> {
    fun findAllByOwner(owner: String): List<NFT>

    @Aggregation(pipeline = ["{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }"])
    fun getMaxBlockNumber(): List<NFT>
}