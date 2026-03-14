package org.vechain.indexer.stargate.token

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.thor.Address

@Profile("stargate", "stargate-token")
@Repository
interface StargateTokenRepository : BaseIndexedRepository<StargateToken, String> {
    @Query("{ '\$and': [{ 'owner': ?0 }, { 'owner': { '\$ne': ?1 } }] }")
    fun findActiveByOwner(
        owner: String,
        excludedOwner: String = Address.ZERO_ADDRESS,
        pageable: Pageable,
    ): Slice<StargateToken>

    @Query("{ '\$and': [{ 'manager': ?0 }, { 'owner': { '\$ne': ?1 } }] }")
    fun findActiveByManager(
        manager: String,
        excludedOwner: String = Address.ZERO_ADDRESS,
        pageable: Pageable,
    ): Slice<StargateToken>

    @Query(
        "{ '\$and': [{ '\$or': [{ 'owner': ?0 }, { 'manager': ?1 }] }, { 'owner': { '\$ne': ?2 } }] }"
    )
    fun findActiveByOwnerOrManager(
        owner: String,
        manager: String,
        excludedOwner: String = Address.ZERO_ADDRESS,
        pageable: Pageable,
    ): Slice<StargateToken>

    @Query("{ 'owner': { '\$ne': ?0 } }")
    fun findAllActive(
        excludedOwner: String = Address.ZERO_ADDRESS,
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
