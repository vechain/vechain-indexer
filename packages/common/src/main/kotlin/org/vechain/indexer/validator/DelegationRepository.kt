package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

/** Query surface for [Delegation] documents. */
@Profile("delegation", "token-reward", "stargate", "vet-delegated-by-block")
@Repository
interface DelegationRepository : BaseIndexedRepository<Delegation, String> {

    @Query("{ 'transitionAtBlock': ?0, 'status': { '\$in': ?1 } }")
    fun findByTransitionAtBlockAndStatusIn(
        blockNumber: Long,
        statuses: List<DelegationStatus>,
    ): List<Delegation>

    @Query("{ 'transitionAtBlock': null, 'status': { '\$in': ?0 } }")
    fun findByTransitionAtBlockIsNullAndStatusIn(statuses: List<DelegationStatus>): List<Delegation>

    @Query("{ 'validator': { '\$in': ?0 } }")
    fun findByValidatorIn(validators: List<String>): List<Delegation>

    @Query("{ 'validator': { '\$in': ?0 }, 'status': { '\$in': ?1 } }")
    fun findByValidatorInAndStatusIn(
        validators: List<String>,
        statuses: List<DelegationStatus>,
    ): List<Delegation>

    @Query("{ 'validator': ?0, 'status': { '\$in': ?1 } }")
    fun findByValidatorAndStatusIn(
        validator: String,
        statuses: List<DelegationStatus>,
        pageable: Pageable,
    ): Slice<Delegation>

    @Query("{ 'validator': ?0, 'status': { '\$in': ?1 } }")
    fun findByValidatorAndStatusIn(
        validator: String,
        statuses: List<DelegationStatus>,
    ): List<Delegation>

    @Query("{ 'validator': ?0 }")
    fun findByValidator(validator: String, pageable: Pageable): Slice<Delegation>

    @Query("{ 'validator': ?0, 'tokenId': ?1 }")
    fun findByValidatorAndTokenId(
        validator: String,
        tokenId: String,
        pageable: Pageable,
    ): Slice<Delegation>

    @Query("{ 'validator': ?0, 'tokenId': ?1, 'status': { '\$in': ?2 } }")
    fun findByValidatorAndTokenIdAndStatusIn(
        validator: String,
        tokenId: String,
        statuses: List<DelegationStatus>,
        pageable: Pageable,
    ): Slice<Delegation>

    @Query("{ 'tokenId': ?0 }")
    fun findByTokenId(tokenId: String, pageable: Pageable): Slice<Delegation>

    @Query("{ 'tokenId': ?0, 'status': { '\$in': ?1 } }")
    fun findByTokenIdAndStatusIn(
        tokenId: String,
        statuses: List<DelegationStatus>,
        pageable: Pageable,
    ): Slice<Delegation>

    @Query("{ 'tokenId': { '\$in': ?0 } }")
    fun findByTokenIdIn(tokenIds: List<String>): List<Delegation>

    @Query("{ 'status': { '\$in': ?0 } }")
    fun findByStatusIn(
        statuses: Collection<DelegationStatus>,
        pageable: Pageable,
    ): Slice<Delegation>

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
     * Active (ACTIVE + EXITING) delegations grouped by NFT level. Powers chain-wide level mix reads
     * at the API layer.
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

    /**
     * Active delegations grouped by (validator, level). Replaces V1's
     * `aggregateActiveDelegationsByValidatorAndLevel`, which V1 used to feed `nftYields` on the
     * validator document. In V2 the API joins this with `ValidatorV2` at read time.
     */
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'status': { '\$in': ['ACTIVE', 'EXITING'] } } }",
                "{ '\$group': { '_id': { 'validator': '\$validator', 'tokenLevel': '\$tokenLevel' }, 'nftCount': { '\$sum': 1 } } }",
                "{ '\$project': { '_id': 0, 'validator': '\$_id.validator', 'level': '\$_id.tokenLevel', 'nftCount': 1 } }",
            ]
    )
    fun aggregateActiveDelegationsByValidatorAndLevel():
        List<DelegationValidatorLevelAggregateResult>
}

data class DelegationStatusCount(val status: String, val count: Long)

data class DelegationCountAggregateResult(val _id: String, val counts: List<DelegationStatusCount>)

data class DelegationLevelAggregateResult(
    val level: String,
    val totalWei: String,
    val nftCount: Long,
)

data class DelegationValidatorLevelAggregateResult(
    val validator: String,
    val level: String,
    val nftCount: Long,
)
