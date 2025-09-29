package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("delegation")
@Repository
interface DelegationRepository : BasePagingAndSortingIndexedRepository<Delegation, String> {
    fun findByNotify(notify: Boolean): List<Delegation>

    fun findByValidatorNextCycleAndStatusIn(
        blockNumber: Long,
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

    fun findByValidator(validator: String, pageable: Pageable): Slice<Delegation>

    fun findByTokenId(tokenId: String, pageable: Pageable): Slice<Delegation>

    fun findByStatusIn(statuses: Collection<Status>, pageable: Pageable): Slice<Delegation>
}
