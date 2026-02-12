package org.vechain.indexer.accounts.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.accounts.VetBalance

@Profile("accounts", "vet-balance")
interface VetBalanceRepository : BaseIndexedRepository<VetBalance, String> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'address': ?0 } }",
                "{ '\$sort': { 'blockTimestamp': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    fun findFirstByAddressOrderByBlockTimestampDesc(address: String): VetBalance?

    @Query(
        value = "{ 'address': ?0, 'blockTimestamp': { '\$gte': ?1, '\$lte': ?2 } }",
        sort = "{ 'blockTimestamp': -1 }",
    )
    fun findByAddressAndBlockTimestampBetweenOrderByBlockTimestampDesc(
        address: String,
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<VetBalance>

    @Query(
        value = "{ 'address': ?0, 'blockTimestamp': { '\$lte': ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findByAddressAndBlockTimestampLessThanEqualOrderByBlockTimestampAsc(
        address: String,
        endTimestamp: Long,
    ): List<VetBalance>
}
