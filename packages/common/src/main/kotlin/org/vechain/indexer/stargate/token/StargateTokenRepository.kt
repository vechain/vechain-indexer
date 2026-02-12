package org.vechain.indexer.stargate.token

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("stargate", "stargate-token")
@Repository
interface StargateTokenRepository : BaseIndexedRepository<StargateToken, String> {
    @Query("{ 'owner': ?0 }")
    fun findByOwner(owner: String, pageable: Pageable): Slice<StargateToken>

    @Query("{ 'manager': ?0 }")
    fun findByManager(manager: String, pageable: Pageable): Slice<StargateToken>

    @Query("{ '\$or': [{ 'owner': ?0 }, { 'manager': ?1 }] }")
    fun findByOwnerOrManager(
        owner: String,
        manager: String,
        pageable: Pageable,
    ): Slice<StargateToken>

    @Query("{ 'validatorId': { '\$in': ?0 } }")
    fun findByValidatorIdIn(validatorIds: Set<String>): List<StargateToken>

    @Query("{ 'delegationNextPeriod': { \$in: ?0 }, 'delegationStatus': { \$in: ?1 } }")
    fun findByDelegationNextPeriodAndDelegationStatusIn(
        blockNumbers: List<Long>,
        statuses: List<String>,
    ): List<StargateToken>

    @Aggregation("{ '\$group': { '_id': '\$validatorId' } }")
    fun findAllDistinctValidatorIds(): List<String?>
}
