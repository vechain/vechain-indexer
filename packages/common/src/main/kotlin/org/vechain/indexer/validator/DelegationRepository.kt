package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("validator", "delegation", "stargate", "vet-delegated-by-block")
@Repository
interface DelegationRepository : BaseIndexedRepository<Delegation, String> {
    @Query("{ 'notify': ?0 }") fun findByNotify(notify: Boolean): List<Delegation>

    @Query("{ 'validatorNextCycle': { '\$in': ?0 }, 'status': { '\$in': ?1 } }")
    fun findByValidatorNextCycleInAndStatusIn(
        blockNumber: List<Long>,
        statuses: List<Status>,
    ): List<Delegation>

    @Query("{ 'validator': { '\$in': ?0 } }")
    fun findByValidatorIn(validators: List<String>): List<Delegation>

    @Query(value = "{ 'status': { '\$ne': ?0 } }", fields = "{ 'validator' : 1, '_id' : 0 }")
    fun findValidatorIdsByStatusNot(status: Status): List<String>

    @Query("{ 'validator': ?0, 'status': { '\$in': ?1 } }")
    fun findByValidatorAndStatusIn(
        validator: String,
        statuses: List<Status>,
        pageable: Pageable,
    ): Slice<Delegation>

    @Query("{ 'validator': ?0, 'status': { '\$in': ?1 } }")
    fun findByValidatorAndStatusIn(validator: String, statuses: List<Status>): List<Delegation>

    @Query("{ 'validator': ?0 }")
    fun findByValidator(validator: String, pageable: Pageable): Slice<Delegation>

    @Query("{ 'tokenId': ?0 }")
    fun findByTokenId(tokenId: String, pageable: Pageable): Slice<Delegation>

    @Query("{ 'tokenId': { '\$in': ?0 } }")
    fun findByTokenIdIn(tokenIds: List<String>): List<Delegation>

    @Query("{ 'status': { '\$in': ?0 } }")
    fun findByStatusIn(statuses: Collection<Status>, pageable: Pageable): Slice<Delegation>

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'status': { '\$in': ['QUEUED', 'ACTIVE', 'EXITING'] } } }",
                "{ '\$group': { '_id': { 'validator': '\$validator', 'status': '\$status' }, 'count': { '\$sum': 1 } } }",
                "{ '\$group': { '_id': '\$_id.validator', 'counts': { '\$push': { 'status': '\$_id.status', 'count': '\$count' } } } }",
            ]
    )
    fun aggregateDelegationCountsByValidator(): List<DelegationCountAggregateResult>

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'validator': ?0, 'status': { '\$in': ['QUEUED', 'ACTIVE', 'EXITING'] } } }",
                "{ '\$group': { '_id': { 'validator': '\$validator', 'status': '\$status' }, 'count': { '\$sum': 1 } } }",
                "{ '\$group': { '_id': '\$_id.validator', 'counts': { '\$push': { 'status': '\$_id.status', 'count': '\$count' } } } }",
            ]
    )
    fun aggregateDelegationCountsByValidator(
        validator: String
    ): List<DelegationCountAggregateResult>

    /**
     * Aggregate active delegations (ACTIVE + EXITING) by token level. Returns total staked amount
     * and NFT count per level.
     */
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'status': { '\$in': ['ACTIVE', 'EXITING'] } } }",
                "{ '\$group': { '_id': '\$tokenLevel', 'totalWei': { '\$sum': { '\$toDecimal': '\$stakedAmount' } }, 'nftCount': { '\$sum': 1 } } }",
                "{ '\$project': { '_id': 0, 'level': '\$_id', 'totalWei': { '\$toString': '\$totalWei' }, 'nftCount': 1 } }",
            ]
    )
    fun aggregateActiveDelegationsByLevel(): List<DelegationLevelAggregateResult>
}

data class DelegationStatusCount(val status: String, val count: Long)

data class DelegationCountAggregateResult(val _id: String, val counts: List<DelegationStatusCount>)

data class DelegationLevelAggregateResult(
    val level: String,
    val totalWei: String,
    val nftCount: Long,
)
