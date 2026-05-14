package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

/**
 * Query surface for V2 delegation documents. Mirrors the V1 [DelegationRepository] surface so the
 * API layer can swap one for the other without touching call sites.
 */
@Profile("delegation-v2", "token-reward")
@Repository
interface DelegationV2Repository : BaseIndexedRepository<DelegationV2, String> {

    @Query("{ 'transitionAtBlock': ?0, 'status': { '\$in': ?1 } }")
    fun findByTransitionAtBlockAndStatusIn(
        blockNumber: Long,
        statuses: List<DelegationStatusV2>,
    ): List<DelegationV2>

    @Query("{ 'transitionAtBlock': null, 'status': { '\$in': ?0 } }")
    fun findByTransitionAtBlockIsNullAndStatusIn(
        statuses: List<DelegationStatusV2>
    ): List<DelegationV2>

    @Query("{ 'validator': { '\$in': ?0 } }")
    fun findByValidatorIn(validators: List<String>): List<DelegationV2>

    @Query("{ 'validator': ?0, 'status': { '\$in': ?1 } }")
    fun findByValidatorAndStatusIn(
        validator: String,
        statuses: List<DelegationStatusV2>,
        pageable: Pageable,
    ): Slice<DelegationV2>

    @Query("{ 'validator': ?0, 'status': { '\$in': ?1 } }")
    fun findByValidatorAndStatusIn(
        validator: String,
        statuses: List<DelegationStatusV2>,
    ): List<DelegationV2>

    @Query("{ 'validator': ?0 }")
    fun findByValidator(validator: String, pageable: Pageable): Slice<DelegationV2>

    @Query("{ 'tokenId': ?0 }")
    fun findByTokenId(tokenId: String, pageable: Pageable): Slice<DelegationV2>

    @Query("{ 'tokenId': { '\$in': ?0 } }")
    fun findByTokenIdIn(tokenIds: List<String>): List<DelegationV2>

    @Query("{ 'status': { '\$in': ?0 } }")
    fun findByStatusIn(
        statuses: Collection<DelegationStatusV2>,
        pageable: Pageable,
    ): Slice<DelegationV2>

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'status': { '\$in': ['QUEUED', 'ACTIVE', 'EXITING'] } } }",
                "{ '\$group': { '_id': { 'validator': '\$validator', 'status': '\$status' }, 'count': { '\$sum': 1 } } }",
                "{ '\$group': { '_id': '\$_id.validator', 'counts': { '\$push': { 'status': '\$_id.status', 'count': '\$count' } } } }",
            ]
    )
    fun aggregateDelegationCountsByValidator(): List<DelegationV2CountAggregateResult>

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
    ): List<DelegationV2CountAggregateResult>

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
    fun aggregateActiveDelegationsByLevel(): List<DelegationV2LevelAggregateResult>

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
        List<DelegationV2ValidatorLevelAggregateResult>
}

data class DelegationV2StatusCount(val status: String, val count: Long)

data class DelegationV2CountAggregateResult(
    val _id: String,
    val counts: List<DelegationV2StatusCount>,
)

data class DelegationV2LevelAggregateResult(
    val level: String,
    val totalWei: String,
    val nftCount: Long,
)

data class DelegationV2ValidatorLevelAggregateResult(
    val validator: String,
    val level: String,
    val nftCount: Long,
)
