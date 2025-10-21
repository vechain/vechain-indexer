package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("stargate", "stargate-token")
@Repository
interface StargateTokenRepository : BasePagingAndSortingIndexedRepository<StargateToken, String> {
    fun findByOwner(owner: String, pageable: Pageable): Slice<StargateToken>

    fun findByManager(manager: String, pageable: Pageable): Slice<StargateToken>

    fun findByOwnerOrManager(
        owner: String,
        manager: String,
        pageable: Pageable,
    ): Slice<StargateToken>

    fun findByValidatorIdIn(validatorIds: Set<String>): List<StargateToken>

    @Query("{ 'delegationNextPeriod': { \$in: ?0 }, 'delegationStatus': { \$in: ?1 } }")
    fun findByDelegationNextPeriodAndDelegationStatusIn(
        blockNumbers: List<Long>,
        statuses: List<String>,
    ): List<StargateToken>

    @Aggregation("{ '\$group': { '_id': '\$validatorId' } }")
    fun findAllDistinctValidatorIds(): List<String?>
}
