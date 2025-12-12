package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("validator", "delegation")
@Repository
interface DelegationRepository : BasePagingAndSortingIndexedRepository<Delegation, String> {
    fun findByNotify(notify: Boolean): List<Delegation>

    fun findByValidatorNextCycleInAndStatusIn(
        blockNumber: List<Long>,
        statuses: List<Status>,
    ): List<Delegation>

    fun findByValidatorIn(validators: List<String>): List<Delegation>

    @Query("{ 'status': { \$ne: ?0 } }", fields = "{ 'validator' : 1, '_id' : 0 }")
    fun findValidatorIdsByStatusNot(status: Status): List<String>

    fun findByValidatorAndStatusIn(
        validator: String,
        statuses: List<Status>,
        pageable: Pageable,
    ): Slice<Delegation>

    fun findByValidatorAndStatusIn(validator: String, statuses: List<Status>): List<Delegation>

    fun findByValidator(validator: String, pageable: Pageable): Slice<Delegation>

    fun findByTokenId(tokenId: String, pageable: Pageable): Slice<Delegation>

    fun findByTokenIdIn(tokenIds: List<String>): List<Delegation>

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
}

data class DelegationStatusCount(val status: String, val count: Long)

data class DelegationCountAggregateResult(val _id: String, val counts: List<DelegationStatusCount>)
